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
import java.util.Collections;
import java.util.Set;
import java.util.function.Consumer;

import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.dependencies.DependencyProvider;
import net.fabricmc.loom.dependencies.LogicalDependencyProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.GradleSupport;
import net.fabricmc.loom.util.MinecraftVersionInfo;

public class MinecraftLibraryProvider extends LogicalDependencyProvider {
	private File MINECRAFT_LIBS;
	private Set<File> libs = Collections.emptySet();

	@Override
	public Set<Class<? extends DependencyProvider>> getDependencies() {
		return Collections.singleton(MinecraftProvider.class);
	}

	@Override
	public void provide(Project project, LoomGradleExtension extension, Consumer<Runnable> postPopulationScheduler) throws Exception {
		MinecraftProvider minecraftProvider = getProvider(MinecraftProvider.class);

		initFiles(extension, minecraftProvider);
		boolean useNatives = !GradleSupport.extractNatives(project);

		for (MinecraftVersionInfo.Library library : minecraftProvider.getLibraries()) {
			if (library.allowed() && (useNatives || !library.isNative()) && library.getFile(MINECRAFT_LIBS) != null) {
				// TODO: Add custom library locations

				// By default, they are all available on all sides
				/* boolean isClientOnly = false;

				if (library.name.contains("java3d") || library.name.contains("paulscode") || library.name.contains("lwjgl") || library.name.contains("twitch") || library.name.contains("jinput") || library.name.contains("text2speech") || library.name.contains("objc")) {
					isClientOnly = true;
				} */

				addDependency(library.getArtifactName(), project, Constants.MINECRAFT_LIBRARIES);
			}
		}

		libs = project.getConfigurations().getByName(Constants.MINECRAFT_LIBRARIES).getFiles();
	}

	public Set<File> getLibraries() {
		return Collections.unmodifiableSet(libs);
	}

	private void initFiles(LoomGradleExtension extension, MinecraftProvider minecraftProvider) {
		MINECRAFT_LIBS = new File(extension.getUserCache(), "libraries");
	}
}
