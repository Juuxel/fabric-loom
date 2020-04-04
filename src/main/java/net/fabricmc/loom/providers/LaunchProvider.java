/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2016, 2017, 2018 FabricMC
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package net.fabricmc.loom.providers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Consumer;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.dependencies.DependencyProvider;
import net.fabricmc.loom.dependencies.LogicalDependencyProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.GradleSupport;

public class LaunchProvider extends LogicalDependencyProvider {
	@Override
	public Set<Class<? extends DependencyProvider>> getDependencies() {
		return Collections.singleton(MinecraftProvider.class);
	}

	@Override
	public void provide(Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws IOException {
		final LaunchConfig launchConfig = new LaunchConfig()
				.property("fabric.development", "true")
				.property("log4j.configurationFile", getLog4jConfigFile(extension).getAbsolutePath())

				.argument("client", "--assetIndex")
				.argument("client", extension.getMinecraftProvider().versionInfo.assetIndex.getFabricId(extension.getMinecraftProvider().minecraftVersion))
				.argument("client", "--assetsDir")
				.argument("client", new File(extension.getUserCache(), "assets").getAbsolutePath());

		//Enable ansi by default for idea and vscode
		if (new File(project.getRootDir(), ".vscode").exists()
				|| new File(project.getRootDir(), ".idea").exists()
				|| (Arrays.stream(project.getRootDir().listFiles()).anyMatch(file -> file.getName().endsWith(".iws")))) {
			launchConfig.property("fabric.log.disableAnsi", "false");
		}

		writeLog4jConfig(extension);
		FileUtils.writeStringToFile(extension.getDevLauncherConfig(), launchConfig.asString(), StandardCharsets.UTF_8);

		if (GradleSupport.extractNatives(project)) {
			launchConfig.property("client", "java.library.path", extension.getNativesDirectory().getAbsolutePath())
						.property("client", "org.lwjgl.librarypath", extension.getNativesDirectory().getAbsolutePath());
		}

		FileUtils.writeStringToFile(extension.getDevLauncherConfig(), launchConfig.asString(), StandardCharsets.UTF_8);

		addDependency("net.fabricmc:dev-launch-injector:" + Constants.DEV_LAUNCH_INJECTOR_VERSION, project, "runtimeOnly");
		addDependency("net.minecrell:terminalconsoleappender:" + Constants.TERMINAL_CONSOLE_APPENDER_VERSION, project, "runtimeOnly");
	}

	private File getLog4jConfigFile(LoomGradleExtension extension) {
		return new File(extension.getDevLauncherConfig().getParentFile(), "log4j.xml");
	}

	private void writeLog4jConfig(LoomGradleExtension extension) {
		try (InputStream is = LaunchProvider.class.getClassLoader().getResourceAsStream("log4j2.fabric.xml")) {
			Files.deleteIfExists(getLog4jConfigFile(extension).toPath());
			Files.copy(is, getLog4jConfigFile(extension).toPath());
		} catch (IOException e) {
			throw new RuntimeException("Failed to generate log4j config", e);
		}
	}

	public static class LaunchConfig {
		private final Map<String, List<String>> values = new HashMap<>();

		public LaunchConfig property(String key, String value) {
			return property("common", key, value);
		}

		public LaunchConfig property(String side, String key, String value) {
			values.computeIfAbsent(side + "Properties", s -> new ArrayList<>())
					.add(String.format("%s=%s", key, value));
			return this;
		}

		public LaunchConfig argument(String value) {
			return argument("common", value);
		}

		public LaunchConfig argument(String side, String value) {
			values.computeIfAbsent(side + "Args", s -> new ArrayList<>())
					.add(value);
			return this;
		}

		public String asString() {
			StringJoiner stringJoiner = new StringJoiner("\n");

			for (Map.Entry<String, List<String>> entry : values.entrySet()) {
				stringJoiner.add(entry.getKey());

				for (String s : entry.getValue()) {
					stringJoiner.add("\t" + s);
				}
			}

			return stringJoiner.toString();
		}
	}
}
