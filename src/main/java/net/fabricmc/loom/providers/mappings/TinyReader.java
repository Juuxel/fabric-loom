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
package net.fabricmc.loom.providers.mappings;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.zip.GZIPInputStream;

import net.fabricmc.mappings.TinyV2Visitor;
import net.fabricmc.mappings.visitor.ClassVisitor;
import net.fabricmc.mappings.visitor.FieldVisitor;
import net.fabricmc.mappings.visitor.LocalVisitor;
import net.fabricmc.mappings.visitor.MappingsVisitor;
import net.fabricmc.mappings.visitor.MethodVisitor;
import net.fabricmc.mappings.visitor.ParameterVisitor;
import net.fabricmc.tinyremapper.TinyUtils;

public class TinyReader {
	static BufferedReader getMappingReader(Path file) throws IOException {
		InputStream in = Files.newInputStream(file);

		if (file.getFileName().toString().endsWith(".gz")) {
			in = new GZIPInputStream(in);
		}

		return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
	}

	public static List<String> readHeaders(Path file) throws IOException {
		try (BufferedReader reader = getMappingReader(file)) {
			return readHeaders(reader);
		}
	}

	static List<String> readHeaders(BufferedReader reader) throws IOException {
		String header = reader.readLine();

		if (header == null) {
			return Collections.emptyList(); //No headers in an empty file
		} else if (header.startsWith("v1\t")) {
			return Arrays.asList(header.substring(3).split("\t"));
		} else if (header.startsWith("tiny\t2\t")) {
			String[] bits;
			return Arrays.asList(bits = header.split("\t")).subList(3, bits.length);
		} else {
			throw new IOException("Unlikely tiny file given " + header);
		}
	}

	public static void readTiny(Path file, String from, String to, IMappingAcceptor mappingAcceptor) throws IOException {
		try (BufferedReader reader = getMappingReader(file)) {
			Map<String, String> reverser = new HashMap<>();

			TinyUtils.read(reader, from, to, (classFrom, name) -> {
				mappingAcceptor.acceptClass(classFrom, name);
				reverser.put(name, classFrom);
			}, (fieldFrom, name) -> {
				mappingAcceptor.acceptField(fieldFrom.owner, fieldFrom.name, fieldFrom.desc, null, name, null);
			}, (methodFrom, name) -> {
				mappingAcceptor.acceptMethod(methodFrom.owner, methodFrom.name, methodFrom.desc, null, name, null);
			}, (methodFrom, args) -> {
				for (int i = args.length - 1; i >= 0; i--) {
					if (args[i] != null) {
						mappingAcceptor.acceptMethodArg(reverser.getOrDefault(methodFrom.owner, methodFrom.owner), methodFrom.name, methodFrom.desc, i, args[i]);
					}
				}
			});
		}
	}

	public static void readComments(Path file, String from, UnaryOperator<String> classRemapper, IMappingAcceptor mappingAcceptor) throws IOException {
		try (Reader in = new InputStreamReader(Files.newInputStream(file), StandardCharsets.UTF_8)) {
			TinyV2Visitor.read(in, new MappingsVisitor() {
				private int index;

				@Override
				public void visitVersion(int major, int minor) {
					assert major == 2;
				}

				@Override
				public void visitProperty(String name) {
				}

				@Override
				public void visitProperty(String name, String value) {
				}

				@Override
				public void visitNamespaces(String... namespaces) {
					index = Arrays.asList(namespaces).indexOf(from);
					if (index < 0) throw new IllegalArgumentException("Provided namespace " + from + " was not in " + file);
				}

				@Override
				public ClassVisitor visitClass(long offset, String[] names) {
					return new ClassVisitor() {
						private final String className = names[index];

						@Override
						public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
							return new MethodVisitor() {
								private final String name = names[index];
								private final String desc = index == 0 ? descriptor : MappingSplat.remapDesc(descriptor, classRemapper);

								@Override
								public ParameterVisitor visitParameter(long offset, String[] names, int localVariableIndex) {
									return new ParameterVisitor() {
										@Override
										public void visitComment(String line) {
											mappingAcceptor.acceptMethodArgComment(className, name, desc, localVariableIndex, line);
										}
									};
								}

								@Override
								public LocalVisitor visitLocalVariable(long offset, String[] names, int localVariableIndex, int localVariableStartOffset, int localVariableTableIndex) {
									assert false;
									return null; //Yarn doesn't publish these, and we don't handle them
								}

								@Override
								public void visitComment(String line) {
									mappingAcceptor.acceptMethodComment(className, name, desc, line);
								}
							};
						}

						@Override
						public FieldVisitor visitField(long offset, String[] names, String descriptor) {
							return new FieldVisitor() {
								private final String name = names[index];
								private final String desc = index == 0 ? descriptor : MappingSplat.remapDesc(descriptor, classRemapper);

								@Override
								public void visitComment(String line) {
									mappingAcceptor.acceptFieldComment(className, name, desc, line);
								}
							};
						}

						@Override
						public void visitComment(String line) {
							mappingAcceptor.acceptClassComment(className, line);
						}
					};
				}
			});
		}
	}
}