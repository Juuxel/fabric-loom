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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

/**
 * Working with jars.
 *
 * @author Juuz
 */
public final class JarUtil {
	public static void extract(File jar, String filePath, File target) throws IOException {
		extract(jar.toPath(), ImmutableMap.of(filePath, target.toPath()));
	}

	public static void extract(Path jar, String filePath, Path target) throws IOException {
		extract(jar, ImmutableMap.of(filePath, target));
	}

	public static void extract(Path jar, Map<String, Path> paths) throws IOException {
		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), ImmutableMap.of("create", false))) {
			for (Map.Entry<String, Path> entry : paths.entrySet()) {
				Path from = fs.getPath(entry.getKey().replace("/", fs.getSeparator()));
				Files.copy(from, entry.getValue(), StandardCopyOption.REPLACE_EXISTING);
			}
		}
	}
}
