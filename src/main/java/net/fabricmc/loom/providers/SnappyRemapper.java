/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers;

import static net.fabricmc.loom.providers.MappingsProvider.INTERMEDIARY;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.logging.Logger;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.MinecraftVersionInfo;
import net.fabricmc.stitch.util.Pair;
import net.fabricmc.tinyremapper.NonClassCopyMode;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import net.fabricmc.tinyremapper.TinyUtils;

public class SnappyRemapper {
	public static Pair<MinecraftVersionInfo, Path> makeMergedJar(Project project, LoomGradleExtension extension, String version) throws IOException {
		boolean offline = project.getGradle().getStartParameter().isOffline();

		MinecraftVersionInfo versionInfo;
		try (FileReader reader = new FileReader(MinecraftProvider.downloadMcJson(project.getLogger(), extension, version, offline))) {
			versionInfo = MinecraftProvider.GSON.fromJson(reader, MinecraftVersionInfo.class);
		}

		File clientJar = new File(extension.getUserCache(), "minecraft-" + version + "-client.jar");
		File serverJar = new File(extension.getUserCache(), "minecraft-" + version + "-server.jar");
		File mergedJar = new File(extension.getUserCache(), "minecraft-" + version + "-merged.jar");

		if (offline) {
			if (clientJar.exists() && serverJar.exists()) {
				project.getLogger().debug("Found client and server " + version + " jars, presuming up-to-date");
			} else if (mergedJar.exists()) {
				//Strictly we don't need the split jars if the merged one exists, let's try go on
				project.getLogger().warn("Missing game jar(s) but merged " + version + " jar present, things might end badly");
			} else {
				throw new GradleException("Missing " + version + " jar(s); Client: " + clientJar.exists() + ", Server: " + serverJar.exists());
			}
		} else {
			MinecraftProvider.downloadJar(project.getLogger(), version, versionInfo, clientJar, "client");
			MinecraftProvider.downloadJar(project.getLogger(), version, versionInfo, serverJar, "server");
		}

		if (!mergedJar.exists()) {
			MinecraftProvider.mergeJars(project.getLogger(), clientJar, serverJar, mergedJar);
		}

		return Pair.of(versionInfo, mergedJar.toPath());
	}

	public static Path makeInterJar(Project project, LoomGradleExtension extension, String version, Optional<Path> intermediaryMappings) throws IOException {
		Pair<MinecraftVersionInfo, Path> versionInfo = makeMergedJar(project, extension, version);
		return remapJar(project, extension, versionInfo.getLeft(), versionInfo.getRight(), intermediaryMappings, version);
	}

	public static Path remapCurrentJar(Project project, LoomGradleExtension extension, MinecraftProvider provider, Optional<Path> intermediaryMappings) {
		return remapJar(project, extension, provider.versionInfo,  provider.getMergedJar().toPath(), intermediaryMappings, provider.minecraftVersion);
	}

	public static Path remapJar(Project project, LoomGradleExtension extension, MinecraftVersionInfo version, Path mergedJar, Optional<Path> intermediaryMappings, String minecraftVersion) {
		Path remappedJar = extension.getUserCache().toPath().resolve("minecraft-" + minecraftVersion + "-intermediary-net.fabricmc.yarn.jar");

		if (Files.notExists(remappedJar)) {
			remapJar(project, version, mergedJar, intermediaryMappings.orElseGet(() -> getIntermediaries(extension, minecraftVersion)), remappedJar);
		}

		return remappedJar;
	}

	static Path getIntermediaries(LoomGradleExtension extension, String version) {
		File intermediaryNames = new File(extension.getUserCache(), "mappings/" + version + '/' + INTERMEDIARY + "-intermediary.tiny");

		if (!intermediaryNames.exists()) {
			try {
				FileUtils.copyURLToFile(new URL(SpecialCases.intermediaries(version)), intermediaryNames);
			} catch (IOException e) {
				throw new UncheckedIOException("Error downloading Intermediary mappings for " + version, e);
			}
		}

		return intermediaryNames.toPath();
	}

	static Set<File> libsForVersion(Project project, MinecraftVersionInfo version) {
		return project.getConfigurations().detachedConfiguration(version.libraries.stream().filter(library -> library.allowed() && !library.isNative())
				.map(library -> project.getDependencies().module(library.getArtifactName())).toArray(Dependency[]::new)).getFiles();
	}

	private static void remapJar(Project project, MinecraftVersionInfo version, Path mergedJar, Path intermediaryMappings, Path remappedJar) {
		remapJar(project.getLogger(), libsForVersion(project, version), mergedJar, "official", intermediaryMappings, remappedJar);
	}

	static void remapJar(Logger logger, Collection<File> libraries, Path originJar, String originMappings, Path intermediaryMappings, Path remappedJar) {
		logger.lifecycle("Remapping minecraft (TinyRemapper, " + originMappings + " -> intermediary)");

		TinyRemapper remapper = TinyRemapper.newRemapper()
				.withMappings(TinyUtils.createTinyMappingProvider(intermediaryMappings, originMappings, "intermediary"))
				.build();

		try (OutputConsumerPath outputConsumer = new OutputConsumerPath(remappedJar)) {
			remapper.readClassPath(libraries.stream().map(File::toPath).distinct().toArray(Path[]::new));
			remapper.readInputs(originJar);
			remapper.apply(outputConsumer);
			outputConsumer.addNonClassFiles(originJar, NonClassCopyMode.FIX_META_INF, remapper);
		} catch (IOException e) {
			throw new RuntimeException("Failed to remap JAR " + originJar + " with mappings from " + intermediaryMappings, e);
		} finally {
			remapper.finish();
		}
	}
}