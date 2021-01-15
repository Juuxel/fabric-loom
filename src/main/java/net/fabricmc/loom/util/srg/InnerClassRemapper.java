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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.zeroturnaround.zip.ZipUtil;

import net.fabricmc.mapping.tree.TinyTree;
import net.fabricmc.tinyremapper.IMappingProvider;

public class InnerClassRemapper {
	public static IMappingProvider of(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		Map<String, String> map = buildInnerClassRemap(fromJar, mappingsWithSrg, from, to);
		return sink -> {
			for (Map.Entry<String, String> entry : map.entrySet()) {
				sink.acceptClass(entry.getKey(), entry.getValue());
			}
		};
	}

	private static Map<String, String> buildInnerClassRemap(Path fromJar, TinyTree mappingsWithSrg, String from, String to) throws IOException {
		Map<String, String> remapInnerClasses = new HashMap<>();

		try (InputStream inputStream = Files.newInputStream(fromJar)) {
			Set<String> availableClasses = mappingsWithSrg.getClasses().stream()
					.map(classDef -> classDef.getName(from))
					.collect(Collectors.toSet());
			ZipUtil.iterate(inputStream, (in, zipEntry) -> {
				if (!zipEntry.isDirectory() && zipEntry.getName().contains("$") && zipEntry.getName().endsWith(".class")) {
					String className = zipEntry.getName().substring(0, zipEntry.getName().length() - 6);

					if (!availableClasses.contains(className)) {
						String parentName = className.substring(0, className.indexOf('$'));
						String childName = className.substring(className.indexOf('$') + 1);
						String remappedParentName = mappingsWithSrg.getClasses().stream()
								.filter(classDef -> Objects.equals(classDef.getName(from), parentName))
								.findFirst()
								.map(classDef -> classDef.getName(to))
								.orElse(parentName);
						String remappedName = remappedParentName + "$" + childName;

						if (!className.equals(remappedName)) {
							remapInnerClasses.put(className, remappedName);
						}
					}
				}
			});
		}

		return remapInnerClasses;
	}
}
