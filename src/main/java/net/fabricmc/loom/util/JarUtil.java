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

package net.fabricmc.loom.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Working with jars.
 *
 * @author Juuz
 */
public final class JarUtil {
	/**
	 * Creates a new JAR file system. Remember to close it!
	 *
	 * @param jar    the JAR path
	 * @param create whether to create the JAR file
	 * @return the created file system
	 * @throws IOException if creating the file system threw an {@link IOException}
	 */
	public static FileSystem fs(Path jar, boolean create) throws IOException {
		return FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), ImmutableMap.of("create", create));
	}

	public static void extract(File jar, String filePath, File target) throws IOException {
		extract(jar.toPath(), ImmutableMap.of(filePath, target.toPath()));
	}

	public static void extract(Path jar, String filePath, Path target) throws IOException {
		extract(jar, ImmutableMap.of(filePath, target));
	}

	/**
	 * Extracts files from a JAR.
	 *
	 * @param jar   the JAR file
	 * @param paths A map of paths in JAR to paths outside the JAR. The keys will be copied to the values.
	 * @throws IOException if an {@link IOException} occurred while extracting
	 */
	public static void extract(Path jar, Map<String, Path> paths) throws IOException {
		try (FileSystem fs = fs(jar, false)) {
			for (Map.Entry<String, Path> entry : paths.entrySet()) {
				Path from = fs.getPath(entry.getKey().replace("/", fs.getSeparator()));
				Files.copy(from, entry.getValue(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}

	/**
	 * Writes the specified lines to a file. Existing files with the same path will be deleted.
	 *
	 * @param path  the target path
	 * @param lines the lines of the file
	 * @throws IOException if an {@link IOException} was thrown when writing
	 */
	public static void write(Path path, List<String> lines) throws IOException {
		Files.deleteIfExists(path);

		try (BufferedWriter writer = Files.newBufferedWriter(path)) {
			for (String line : lines) {
				writer.write(line);
				writer.write('\n');
			}
		}
	}
}
