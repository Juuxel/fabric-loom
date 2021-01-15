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

package net.fabricmc.loom.util.srg;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.gradle.api.tasks.JavaExec;
import org.gradle.util.GradleVersion;
import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.loom.LoomGradleExtension;
import net.fabricmc.loom.util.JarUtil;

public class SpecialSourceExecutor {
	public static Path produceSrgJar(Project project, File specialSourceJar, Path officialJar, Path srgPath) throws Exception {
		Set<String> filter = Files.readAllLines(srgPath, StandardCharsets.UTF_8).stream()
				.filter(s -> !s.startsWith("\t"))
				.map(s -> s.split(" ")[0] + ".class")
				.collect(Collectors.toSet());
		Path stripped = project.getExtensions().getByType(LoomGradleExtension.class).getProjectBuildCache().toPath().resolve(officialJar.getFileName().toString().substring(0, officialJar.getFileName().toString().length() - 3) + "-filtered.jar");
		Files.deleteIfExists(stripped);

		try (FileSystem strippedFs = JarUtil.fs(stripped, true)) {
			ZipUtil.iterate(officialJar.toFile(), (in, zipEntry) -> {
				if (filter.contains(zipEntry.getName())) {
					Path path = strippedFs.getPath(zipEntry.getName());

					if (path.getParent() != null) {
						Files.createDirectories(path.getParent());
					}

					Files.write(path, IOUtils.toByteArray(in), StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW);
				}
			});
		}

		Path output = tmpFile();

		String[] args = new String[]{
				"--in-jar",
				stripped.toAbsolutePath().toString(),
				"--out-jar",
				output.toAbsolutePath().toString(),
				"--srg-in",
				srgPath.toAbsolutePath().toString()
		};

		JavaExec java = project.getTasks().create("PleaseIgnore_JavaExec_" + UUID.randomUUID().toString().replace("-", ""), JavaExec.class);
		java.setArgs(Arrays.asList(args));
		java.setClasspath(project.files(specialSourceJar));
		java.setWorkingDir(tmpDir().toFile());
		java.setMain("net.md_5.specialsource.SpecialSource");
		java.setStandardOutput(System.out);
		java.exec();

		if (GradleVersion.current().compareTo(GradleVersion.version("6.0.0")) >= 0) {
			java.setEnabled(false);
		} else {
			project.getTasks().remove(java);
		}

		// TODO: Figure out why doing this produces a different result? Possible different class path?
		// SpecialSource.main(args);

		Files.deleteIfExists(stripped);
		return output;
	}

	private static Path tmpFile() throws IOException {
		return Files.createTempFile(null, null);
	}

	private static Path tmpDir() throws IOException {
		return Files.createTempDirectory(null);
	}
}
