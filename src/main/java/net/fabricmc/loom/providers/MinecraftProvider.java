/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinTask;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.zip.ZipError;

import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.Callables;
import com.google.gson.Gson;

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.LoomGradleExtension.JarMergeOrder;
import net.fabricmc.loom.dependencies.LoomDependencyManager;
import net.fabricmc.loom.dependencies.PhysicalDependencyProvider;
import net.fabricmc.loom.util.Checksum;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.DownloadUtil;
import net.fabricmc.loom.util.ManifestVersion;
import net.fabricmc.loom.util.MapJarsTiny;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.loom.util.PentaFunction;
import net.fabricmc.loom.util.MinecraftVersionInfo.AssetIndex;
import net.fabricmc.loom.util.MinecraftVersionInfo.Library;
import net.fabricmc.loom.util.StaticPathWatcher;
import net.fabricmc.stitch.merge.JarMerger;

public class MinecraftProvider extends PhysicalDependencyProvider implements MinecraftVersionAdaptable {
	private static final class VersionKey {
		private final boolean customManifest;
		private final String value;

		public static VersionKey forVersion(String minecraftVersion) {
			return new VersionKey(false, minecraftVersion);
		}

		public static VersionKey forManifest(String manifestURL) {
			return new VersionKey(true, manifestURL);
		}

		private VersionKey(boolean customManifest, String value) {
			this.customManifest = customManifest;
			this.value = value;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj) return true;
			if (!(obj instanceof VersionKey)) return false;

			VersionKey that = (VersionKey) obj;
			return customManifest == that.customManifest && Objects.equals(value, that.value);
		}

		@Override
		public int hashCode() {
			return Boolean.hashCode(customManifest) * value.hashCode();
		}
	}
	public static class MinecraftVersion implements MinecraftVersionAdaptable {
		private final MinecraftVersionInfo versionInfo;
		private final File clientJar;
		private final File serverJar;

		private final JarMergeOrder mergeOrder;
		private final ForkJoinTask<Path> jarMerger;
		volatile Path mappings;

		MinecraftVersion(MinecraftVersionInfo versionInfo, File clientJar, File serverJar, JarMergeOrder mergeOrder, Function<MinecraftVersion, Callable<Path>> jarMerger) {
			this.versionInfo = versionInfo;
			this.clientJar = clientJar;
			this.serverJar = serverJar;
			this.mergeOrder = mergeOrder;
			this.jarMerger = ForkJoinPool.commonPool().submit(jarMerger.apply(this));
		}

		@Override
		public String getName() {
			return versionInfo.id;
		}

		public Collection<Library> getLibraries() {
			return Collections.unmodifiableList(versionInfo.libraries);
		}

		@Override
		public Set<File> getJavaLibraries(Project project) {
			return project.getConfigurations().detachedConfiguration(versionInfo.libraries.stream().filter(library -> library.allowed() && !library.isNative())
					.map(library -> project.getDependencies().module(library.getArtifactName())).toArray(Dependency[]::new)).getFiles();
		}

		public AssetIndex getAssetIndex() {
			return versionInfo.assetIndex;
		}

		public File getClientJar() {
			return clientJar;
		}

		public File getServerJar() {
			return serverJar;
		}

		@Override
		public Path getMergedJar() {
			return jarMerger.join();
		}

		@Override
		public JarMergeOrder getMergeStrategy() {
			return mergeOrder;
		}

		@Override
		public boolean needsIntermediaries() {
			return mergeOrder == JarMergeOrder.LAST && mappings == null;
		}

		@Override
		public void giveIntermediaries(Path mappings) {
			if (!jarMerger.isDone()) {
				synchronized (this) {
					if (this.mappings == null) {
						this.mappings = mappings;
						notify();
					}
				}
			}
		}
	}
	private static final Map<VersionKey, Map<JarMergeOrder, MinecraftVersion>> VERSION_TO_VERSION = new ConcurrentHashMap<>();
	private static final byte DOWNLOAD_ATTEMPTS = 3;
	private static final Gson GSON = new Gson();

	public String minecraftVersion;
	private MinecraftVersion version;

	@Override
	public void register(LoomDependencyManager dependencyManager) {
		super.register(dependencyManager);

		dependencyManager.addProvider(new MinecraftLibraryProvider());
	}

	@Override
	public void provide(DependencyInfo dependency, Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
		minecraftVersion = dependency.getDependency().getVersion();

		VersionKey key = extension.customManifest != null ? VersionKey.forManifest(extension.customManifest) : VersionKey.forVersion(minecraftVersion);
		Map<JarMergeOrder, MinecraftVersion> mergeToVersion = VERSION_TO_VERSION.computeIfAbsent(key, k -> new EnumMap<>(JarMergeOrder.class));

		JarMergeOrder mergeOrder = extension.getJarMergeOrder();
		version = mergeToVersion.get(mergeOrder);

		if (version == null) {
			synchronized (mergeToVersion) {
				MinecraftVersion version = mergeToVersion.get(mergeOrder);

				if (version == null) {
					version = makeMergedJar(project, extension, minecraftVersion, Optional.ofNullable(extension.customManifest), mergeOrder,
						(versionInfo, clientJar, serverJar, actualMergeOrder, jarMerger) -> {//We only want to override one method :|
							return new MinecraftVersion(versionInfo, clientJar, serverJar, actualMergeOrder, jarMerger) {
								@Override
								public Set<File> getJavaLibraries(Project project) {
									return getLibraryProvider().getLibraries();
								}
							};
					});

					mergeToVersion.put(mergeOrder, version);
					if (mergeOrder == JarMergeOrder.INDIFFERENT) {
						mergeToVersion.put(version.getMergeStrategy(), version);
					}

					//TODO: Pull these straight from MappingProvider as it finds them
					if (version.getMergeStrategy() == JarMergeOrder.LAST) version.giveIntermediaries(MappingsProvider.getIntermediaries(extension, minecraftVersion));
				}

				this.version = version;
			}
		}
;	}


	public static MinecraftVersion makeMergedJar(Project project, LoomGradleExtension extension, String version, Optional<String> customManifest, JarMergeOrder mergeOrder) throws IOException {
		return makeMergedJar(project, extension, version, customManifest, mergeOrder, MinecraftVersion::new);
	}

	private static MinecraftVersion makeMergedJar(Project project, LoomGradleExtension extension, String version, Optional<String> customManifest, JarMergeOrder mergeOrder,
			PentaFunction<MinecraftVersionInfo, File, File, JarMergeOrder, Function<MinecraftVersion, Callable<Path>>, MinecraftVersion> versionFactory) throws IOException {
		boolean offline = project.getGradle().getStartParameter().isOffline();

		MinecraftVersionInfo versionInfo;
		try (FileReader reader = new FileReader(downloadMcJson(project.getLogger(), extension, version, offline, customManifest))) {
			versionInfo = GSON.fromJson(reader, MinecraftVersionInfo.class);
		}
		SpecialCases.enhanceVersion(extension, versionInfo);

		if (mergeOrder == JarMergeOrder.INDIFFERENT) {
			Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone("Europe/Stockholm"));
			calendar.set(2012, Calendar.JULY, 22); //Day before 12w30a
			mergeOrder = calendar.getTime().before(versionInfo.releaseTime) ? JarMergeOrder.FIRST : JarMergeOrder.LAST;
		}

		boolean needClient = extension.getJarMergeOrder() != JarMergeOrder.SERVER_ONLY;
		boolean needServer = extension.getJarMergeOrder() != JarMergeOrder.CLIENT_ONLY;

		File clientJar = new File(extension.getUserCache(), JarNameFactory.CLIENT.getJarName(version));
		File serverJar = new File(extension.getUserCache(), JarNameFactory.SERVER.getJarName(version));
		File mergedJar = new File(extension.getUserCache(), mergeOrder.getJarName(version));

		if (offline) {
			if ((!needClient || clientJar.exists()) && (!needServer || serverJar.exists())) {
				project.getLogger().debug("Found client and server " + version + " jars, presuming up-to-date");
			} else if (mergedJar.exists()) {
				//Strictly we don't need the split jars if the merged one exists, let's try go on
				project.getLogger().warn("Missing game jar(s) but merged " + version + " jar present, things might end badly");
			} else {
				throw new GradleException("Missing " + version + " jar(s); Client: " + clientJar.exists() + ", Server: " + serverJar.exists());
			}
		} else {
			if (needClient) downloadJar(project.getLogger(), version, versionInfo, clientJar, "client");
			if (needServer) downloadJar(project.getLogger(), version, versionInfo, serverJar, "server");
		}

		Function<MinecraftVersion, Callable<Path>> mergeTask;
		switch (mergeOrder) {
		case FIRST:
			mergeTask = lock -> () -> {
				if (!mergedJar.exists()) {
					try {
						mergeJars(project.getLogger(), clientJar, serverJar, mergedJar);
					} catch (ZipError e) {
						DownloadUtil.delete(clientJar);
						DownloadUtil.delete(serverJar);

						project.getLogger().error("Could not merge JARs! Deleting source JARs - please re-run the command and move on.", e);
						throw new RuntimeException("Error merging " + clientJar + " and " + serverJar, e);
					}
				}

				return mergedJar.toPath();
			};
			break;

		case LAST:
			mergeTask = lock -> () -> {//TODO: Account for Openfine (or any other jar interfering tasks) changing the merged jar name
				if (!mergedJar.exists()) {
					synchronized (lock) {
						while (lock.mappings == null) {
							lock.wait();
						}
					}

					Path interClient = extension.getUserCache().toPath().resolve(JarNameFactory.CLIENT_INTERMEDIARY.getJarName(version));
					if (Files.notExists(interClient)) {
						//Can't use the library provider yet as the configuration might need more things adding to it
						Set<File> libraries = lock.getJavaLibraries(project);
						MapJarsTiny.remapJar(project.getLogger(), clientJar.toPath(), lock.mappings, false, libraries, interClient, "client");
					}

					Path interServer = interClient.resolveSibling(JarNameFactory.SERVER_INTERMEDIARY.getJarName(version));
					if (Files.notExists(interServer)) {
						Set<File> libraries = Collections.emptySet(); //The server contains all its own dependencies
						MapJarsTiny.remapJar(project.getLogger(), serverJar.toPath(), lock.mappings, false, libraries, interServer, "server");
					}

					MinecraftProvider.mergeJars(project.getLogger(), interClient.toFile(), interServer.toFile(), mergedJar);
				}

				return mergedJar.toPath();
			};
			break;

		case CLIENT_ONLY:
			mergeTask = lock -> Callables.returning(clientJar.toPath());
			break;

		case SERVER_ONLY:
			mergeTask = lock -> Callables.returning(serverJar.toPath());
			break;

		case INDIFFERENT:
		default:
			throw new IllegalStateException("Unexpected jar merge order " + mergeOrder);
		}

		return versionFactory.apply(versionInfo, clientJar, serverJar, mergeOrder, mergeTask);
	}

	private static File downloadMcJson(Logger logger, LoomGradleExtension extension, String minecraftVersion, boolean offline, Optional<String> customManifest) throws IOException {
		File MINECRAFT_JSON = new File(extension.getUserCache(), "minecraft-" + minecraftVersion + "-info.json");

		if (offline) {
			if (MINECRAFT_JSON.exists()) {
				//If there is the manifest already we'll presume that's good enough
				logger.debug("Found Minecraft {} manifest, presuming up-to-date", minecraftVersion);
			} else {
				//If we don't have the manifests then there's nothing more we can do
				throw new GradleException("Minecraft " + minecraftVersion + " manifest not found at " + MINECRAFT_JSON.getAbsolutePath());
			}
		} else {
			Optional<String> versionURL;
			if (customManifest.isPresent()) {
				logger.lifecycle("Using custom minecraft manifest");

				versionURL = customManifest;
			} else {
				File manifests = new File(extension.getUserCache(), "version_manifest.json");

				logger.debug("Downloading version manifests");
				DownloadUtil.downloadIfChanged(new URL("https://launchermeta.mojang.com/mc/game/version_manifest.json"), manifests, logger);

				try (Reader versionManifest = Files.newBufferedReader(manifests.toPath(), StandardCharsets.UTF_8)) {
					ManifestVersion mcManifest = GSON.fromJson(versionManifest, ManifestVersion.class);
					versionURL = mcManifest.versions.stream().filter(versions -> versions.id.equalsIgnoreCase(minecraftVersion)).findFirst().map(version -> version.url);
				}

				out: if (!versionURL.isPresent()) {
					String url;
					switch (minecraftVersion) {
					case "1.14.3 - Combat Test":
						logger.warn("Using old name for first Combat Test, should use 1.14_combat-212796 instead");
					case "1.14_combat-212796": //Combat Test 1
						//Extracted from https://launcher.mojang.com/experiments/combat/610f5c9874ba8926d5ae1bcce647e5f0e6e7c889/1_14_combat-212796.zip
						url = "https://gist.github.com/Chocohead/b62b1e94d8d4b32ef3326df63cf407f2/raw/bdc1f968b3529ebca9b197a5b2612f57b6354624/1.14_combat-212796.json";

					case "1.14_combat-0": //Combat Test 2
						//Extracted from https://launcher.mojang.com/experiments/combat/d164bb6ecc5fca9ac02878c85f11befae61ac1ca/1_14_combat-0.zip
						url = "https://gist.github.com/Chocohead/b62b1e94d8d4b32ef3326df63cf407f2/raw/bdc1f968b3529ebca9b197a5b2612f57b6354624/1.14_combat-0.json";
						break;

					case "1.14_combat-3": //Combat Test 3
						//Extracted from https://launcher.mojang.com/experiments/combat/0f209c9c84b81c7d4c88b4632155b9ae550beb89/1_14_combat-3.zip
						url = "https://gist.github.com/Chocohead/b62b1e94d8d4b32ef3326df63cf407f2/raw/bdc1f968b3529ebca9b197a5b2612f57b6354624/1.14_combat-3.json";
						break;

					case "1.15_combat-1": //Combat Test 4
						//Extracted from https://launcher.mojang.com/experiments/combat/ac11ea96f3bb2fa2b9b76ab1d20cacb1b1f7ef60/1_15_combat-1.zip
						url = "https://gist.github.com/Chocohead/b62b1e94d8d4b32ef3326df63cf407f2/raw/bdc1f968b3529ebca9b197a5b2612f57b6354624/1.15_combat-1.json";
						break;

					case "1_15_combat-6": //Combat Test 5
						//Extracted from https://launcher.mojang.com/experiments/combat/52263d42a626b40c947e523128f7a195ec5af76a/1_15_combat-6.zip
						url = "https://gist.github.com/Chocohead/b62b1e94d8d4b32ef3326df63cf407f2/raw/bdc1f968b3529ebca9b197a5b2612f57b6354624/1.15_combat-6.json";
						break;

					default:
						break out;
					}

					versionURL = Optional.of(url);
				}
			}

			if (versionURL.isPresent()) {
				if (StaticPathWatcher.INSTANCE.hasFileChanged(MINECRAFT_JSON.toPath())) {
					logger.debug("Downloading Minecraft {} manifest", minecraftVersion);
					DownloadUtil.downloadIfChanged(new URL(versionURL.get()), MINECRAFT_JSON, logger);
					StaticPathWatcher.INSTANCE.resetFile(MINECRAFT_JSON.toPath());
				}
			} else {
				throw new RuntimeException("Failed to find minecraft version: " + minecraftVersion);
			}
		}

		return MINECRAFT_JSON;
	}

	private static void downloadJar(Logger logger, String minecraftVersion, MinecraftVersionInfo versionInfo, File to, String name) throws IOException {
		downloadJar(logger, minecraftVersion, new URL(versionInfo.downloads.get(name).url), to, name, versionInfo.downloads.get(name).sha1);
	}

	private static void downloadJar(Logger logger, String minecraftVersion, URL from, File to, String name, String hash) throws IOException {
		if (!to.exists() || !Checksum.equals(to, hash) && StaticPathWatcher.INSTANCE.hasFileChanged(to.toPath())) {
			logger.debug("Downloading Minecraft {} {} jar", minecraftVersion, name);

			int attempt = 1;
			do {
				DownloadUtil.delete(to); //Clear the existing (wrong) contents out of the way
				DownloadUtil.downloadIfChanged(from, to, logger);
			} while (attempt++ <= DOWNLOAD_ATTEMPTS && !Checksum.equals(to, hash));

			if (attempt > DOWNLOAD_ATTEMPTS) {//Apparently we just couldn't get a jar which had the right hash
				throw new IllegalStateException("Unable to successfully download an intact " + minecraftVersion + ' ' + name + " jar!");
			}

			StaticPathWatcher.INSTANCE.resetFile(to.toPath());
		}
	}

	private static void mergeJars(Logger logger, File MINECRAFT_CLIENT_JAR, File MINECRAFT_SERVER_JAR, File MINECRAFT_MERGED_JAR) throws IOException {
		logger.lifecycle(":merging jars");

		try (JarMerger jarMerger = new JarMerger(MINECRAFT_CLIENT_JAR, MINECRAFT_SERVER_JAR, MINECRAFT_MERGED_JAR)) {
			jarMerger.enableSyntheticParamsOffset();
			jarMerger.merge();
		}
	}


	@Override
	public String getName() {
		return minecraftVersion;
	}

	@Override
	public JarMergeOrder getMergeStrategy() {
		return version.getMergeStrategy();
	}

	public Set<String> getNeededHeaders() {
		switch (version.getMergeStrategy()) {
		case FIRST:
			return ImmutableSet.of("official", "intermediary");

		case LAST:
			return ImmutableSet.of("client", "server", "intermediary");

		case CLIENT_ONLY:
			return ImmutableSet.of("client", "intermediary");

		case SERVER_ONLY:
			return ImmutableSet.of("server", "intermediary");

		case INDIFFERENT:
		default:
			throw new IllegalStateException("Unexpected jar merge order " + version.getMergeStrategy());
		}
	}

	@Override
	public boolean needsIntermediaries() {
		return version.needsIntermediaries();
	}

	@Override
	public void giveIntermediaries(Path mappings) {
		version.giveIntermediaries(mappings);
	}

	@Override
	public Path getMergedJar() {
		//Strictly this is only a problem if the main thread did this, but that's a pain to detect so we'll just be safe
		if (needsIntermediaries()) throw new IllegalStateException("Impending deadlock blocked");

		return version.getMergedJar();
	}

	public MinecraftLibraryProvider getLibraryProvider() {
		return getProvider(MinecraftLibraryProvider.class);
	}

	public Collection<Library> getLibraries() {
		return version.getLibraries();
	}

	@Override
	public Set<File> getJavaLibraries(Project project) {
		return version.getJavaLibraries(project);
	}

	public AssetIndex getAssetIndex() {
		return version.getAssetIndex();
	}

	@Override
	public String getTargetConfig() {
		return Constants.MINECRAFT;
	}

	@Override
	public boolean isRequired() {
		return true;
	}

	@Override
	public boolean isUnique() {
		return true;
	}
}