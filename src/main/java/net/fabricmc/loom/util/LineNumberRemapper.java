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

import static java.text.MessageFormat.format;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import org.zeroturnaround.zip.ZipUtil;
import org.zeroturnaround.zip.transform.ByteArrayZipEntryTransformer;
import org.zeroturnaround.zip.transform.ZipEntryTransformerEntry;

import net.fabricmc.loom.util.progress.ProgressLogger;

/**
 * TODO, Move to stitch.
 * Created by covers1624 on 18/02/19.
 */
public class LineNumberRemapper {
	private final Map<String, RClass> lineMap = new HashMap<>();

	public void readMappings(File lineMappings) {
		try (BufferedReader reader = new BufferedReader(new FileReader(lineMappings))) {
			RClass clazz = null;
			String line = null;
			int i = 0;

			try {
				while ((line = reader.readLine()) != null) {
					if (line.isEmpty()) {
						continue;
					}

					String[] segs = line.trim().split("\t");

					if (line.charAt(0) != '\t') {
						clazz = lineMap.computeIfAbsent(segs[0], RClass::new);
						clazz.maxLine = Integer.parseInt(segs[1]);
						clazz.maxLineDest = Integer.parseInt(segs[2]);
					} else {
						clazz.lineMap.put(Integer.parseInt(segs[0]), Integer.parseInt(segs[1]));
					}

					i++;
				}
			} catch (Exception e) {
				throw new RuntimeException(format("Exception reading mapping line @{0}: {1}", i, line), e);
			}
		} catch (IOException e) {
			throw new RuntimeException("Exception reading LineMappings file.", e);
		}
	}

	public void process(ProgressLogger logger, File jar) {
		ZipEntryTransformerEntry[] transformers = lineMap.entrySet().stream().map(entry -> {
			String className = entry.getKey();

			return new ZipEntryTransformerEntry(className + ".class", new ByteArrayZipEntryTransformer() {
				private final RClass rClass = entry.getValue();

				@Override
				protected byte[] transform(ZipEntry zipEntry, byte[] input) throws IOException {
					if (logger != null) logger.progress("Remapping " + className);

					ClassReader reader = new ClassReader(input);
					ClassWriter writer = new ClassWriter(reader, 0);

					reader.accept(new LineNumberVisitor(Opcodes.ASM7, writer, rClass), 0);
					return writer.toByteArray();
				}

				@Override
				protected boolean preserveTimestamps() {
					return true; //Why not?
				}
			});
		}).toArray(ZipEntryTransformerEntry[]::new);

		ZipUtil.transformEntries(jar, transformers);
	}

	private static class LineNumberVisitor extends ClassVisitor {
		private final RClass rClass;

		LineNumberVisitor(int api, ClassVisitor classVisitor, RClass rClass) {
			super(api, classVisitor);
			this.rClass = rClass;
		}

		@Override
		public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
			return new MethodVisitor(api, super.visitMethod(access, name, descriptor, signature, exceptions)) {
				@Override
				public void visitLineNumber(int line, Label start) {
					int tLine = line;

					if (tLine <= 0) {
						super.visitLineNumber(line, start);
					} else if (tLine >= rClass.maxLine) {
						super.visitLineNumber(rClass.maxLineDest, start);
					} else {
						Integer matchedLine = null;

						while (tLine <= rClass.maxLine && (matchedLine = rClass.lineMap.get(tLine)) == null) {
							tLine++;
						}

						super.visitLineNumber(matchedLine != null ? matchedLine : rClass.maxLineDest, start);
					}
				}
			};
		}
	}

	private static class RClass {
		@SuppressWarnings("unused")
		private final String name;
		private int maxLine;
		private int maxLineDest;
		private final Map<Integer, Integer> lineMap = new HashMap<>();

		private RClass(String name) {
			this.name = name;
		}
	}
}
