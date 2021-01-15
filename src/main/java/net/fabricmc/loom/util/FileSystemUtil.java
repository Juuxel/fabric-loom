/*
 * This file is part of fabric-loom, licensed under the MIT License (MIT).
 *
 * Copyright (c) 2019 Chocohead
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
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.Map;

import com.google.common.collect.ImmutableMap;

public final class FileSystemUtil {
	public static class FileSystemDelegate implements AutoCloseable {
		private final FileSystem fileSystem;
		private final boolean owner;

		public FileSystemDelegate(FileSystem fileSystem, boolean owner) {
			this.fileSystem = fileSystem;
			this.owner = owner;
		}

		public FileSystem get() {
			return fileSystem;
		}

		@Override
		public void close() throws IOException {
			if (owner) {
				fileSystem.close();
			}
		}
	}

	private FileSystemUtil() {
	}

	private static final Map<String, Object> JFS_ARGS_CREATE = ImmutableMap.of("create", true);
	private static final Map<String, Object> JFS_ARGS_EXISTING = ImmutableMap.of("create", false);

	public static FileSystemDelegate getJarFileSystem(File file, boolean create) throws IOException {
		return getJarFileSystem(file.toURI(), create);
	}

	public static FileSystemDelegate getJarFileSystem(Path path, boolean create) throws IOException {
		return getJarFileSystem(path.toUri(), create);
	}

	public static FileSystemDelegate getJarFileSystem(URI uri, boolean create) throws IOException {
		URI jarUri;

		try {
			jarUri = new URI("jar:" + uri.getScheme(), uri.getHost(), uri.getPath(), uri.getFragment());
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}

		try {
			return new FileSystemDelegate(FileSystems.newFileSystem(jarUri, create ? JFS_ARGS_CREATE : JFS_ARGS_EXISTING), true);
		} catch (FileSystemAlreadyExistsException e) {
			return new FileSystemDelegate(FileSystems.getFileSystem(jarUri), false);
		}
	}
}
