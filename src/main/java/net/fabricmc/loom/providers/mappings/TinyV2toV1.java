/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers.mappings;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Collectors;

import net.fabricmc.loom.providers.mappings.MappingSplat.CombinedMapping;
import net.fabricmc.loom.providers.mappings.MappingSplat.CombinedMapping.CombinedField;
import net.fabricmc.loom.providers.mappings.MappingSplat.CombinedMapping.CombinedMethod;
import net.fabricmc.mappings.EntryTriple;
import net.fabricmc.mappings.ExtendedMappings;
import net.fabricmc.mappings.MappingsProvider;
import net.fabricmc.mappings.TinyV2Visitor;
import net.fabricmc.mappings.model.CommentEntry;
import net.fabricmc.mappings.model.CommentEntry.Class;
import net.fabricmc.mappings.model.CommentEntry.Field;
import net.fabricmc.mappings.model.CommentEntry.LocalVariableComment;
import net.fabricmc.mappings.model.CommentEntry.Method;
import net.fabricmc.mappings.model.CommentEntry.Parameter;
import net.fabricmc.mappings.model.Comments;
import net.fabricmc.mappings.model.LocalVariable;
import net.fabricmc.mappings.model.MethodParameter;
import net.fabricmc.mappings.visitor.ClassVisitor;
import net.fabricmc.mappings.visitor.FieldVisitor;
import net.fabricmc.mappings.visitor.LocalVisitor;
import net.fabricmc.mappings.visitor.MappingsVisitor;
import net.fabricmc.mappings.visitor.MethodVisitor;
import net.fabricmc.mappings.visitor.ParameterVisitor;

public class TinyV2toV1 {
	public static void convert(Path input, Path output) {
		convert(input, output, null, null);
	}

	public static void convert(Path input, Path output, Path params, Path comments) {
		try (Reader in = new InputStreamReader(Files.newInputStream(input), StandardCharsets.UTF_8);
				BufferedWriter out = Files.newBufferedWriter(output);
				BufferedWriter paramOut = params != null ? Files.newBufferedWriter(params) : null) {
			TinyV2Visitor.read(in, new MappingsVisitor() {
				private final boolean writeParams = paramOut != null;
				private List<String> namespaces;
				private Runnable finaliser;

				@Override
				public void visitVersion(int major, int minor) {
					assert major == 2;
				}

				@Override
				public void visitProperty(String name) {
					// Don't need to catch escaped-names as the visitor will do it
				}

				@Override
				public void visitProperty(String name, String value) {
				}

				@Override
				public void visitNamespaces(String... namespaces) {
					if (writeParams) this.namespaces = Arrays.asList(namespaces);

					try {
						out.write("v1");
						for (String namespace : namespaces) {
							out.write('\t');
							out.write(namespace);
						}
						out.newLine();
					} catch (IOException e) {
						throw new UncheckedIOException("Error writing tiny header", e);
					}
				}

				@Override
				public ClassVisitor visitClass(long offset, String[] names) {
					try {
						out.write("CLASS");
						for (String name : names) {
							out.write('\t');
							out.write(name);
						}
						out.newLine();
					} catch (IOException e) {
						throw new UncheckedIOException("Error writing tiny class", e);
					}

					if (finaliser != null) finaliser.run(); //Ensure last parameters have definitely been written
					return new ClassVisitor() {
						class ParamHolder implements MethodVisitor {
							private final int named = namespaces.indexOf("named"), official = namespaces.indexOf("official");
							private final String className = names[named];
							private final String method, desc;
							private String[] args;

							public ParamHolder(String[] methodNames, String desc) {
								this.method = methodNames[official];
								this.desc = desc;
								finaliser = this::write;
							}

							@Override
							public ParameterVisitor visitParameter(long offset, String[] names, int index) {
								if (args.length <= index) {
									args = Arrays.copyOf(args, index + 1);
								}

								args[index] = names[named];
								return null;
							}

							@Override
							public LocalVisitor visitLocalVariable(long offset, String[] names, int localVariableIndex, int localVariableStartOffset, int localVariableTableIndex) {
								return null;
							}

							@Override
							public void visitComment(String line) {
							}

							public void write() {
								try {
									paramOut.write(className);
									paramOut.write('/');
									paramOut.write(method);
									paramOut.write(desc);
									paramOut.newLine();

									for (int i = args.length - 1; i >= 0; i--) {
										if (args[i] != null) {
											paramOut.write('\t');
											paramOut.write(args[i]);
											paramOut.newLine();
										}
									}
								} catch (IOException e) {
									throw new UncheckedIOException("Error writing parameters in " + className + '#' + method + desc, e);
								}
							}
						}
						private ParamHolder currentMethod;

						@Override
						public MethodVisitor visitMethod(long offset, String[] names, String descriptor) {
							try {
								out.write("METHOD\t");
								out.write(descriptor);
								for (String name : names) {
									out.write('\t');
									out.write(name);
								}
								out.newLine();
							} catch (IOException e) {
								throw new UncheckedIOException("Error writing tiny method", e);
							}

							if (writeParams) {
								writeParams();
								return currentMethod = new ParamHolder(names, descriptor);
							} else {
								return null;
							}
						}

						@Override
						public FieldVisitor visitField(long offset, String[] names, String descriptor) {
							try {
								out.write("FIELD\t");
								out.write(descriptor);
								for (String name : names) {
									out.write('\t');
									out.write(name);
								}
								out.newLine();
							} catch (IOException e) {
								throw new UncheckedIOException("Error writing tiny field", e);
							}

							if (writeParams) writeParams();
							return null;
						}

						private void writeParams() {
							assert writeParams;
							if (currentMethod != null) currentMethod.write();
							finaliser = null;
						}

						@Override
						public void visitComment(String line) {
						}
					};
				}

				@Override
				public void finish() {
					if (finaliser != null) finaliser.run();
				}
			});
		} catch (IOException e) {
			throw new UncheckedIOException("Error preparing to convert " + input + " to " + output, e);
		}

		if (comments != null) writeCommentFile(input, comments);
	}

	private static class FullClassComments {
		public final String className;
		final List<Class> classComments = new ArrayList<>();
		final List<Field> fieldComments = new ArrayList<>();
		final List<Method> methodComments = new ArrayList<>();
		final Map<EntryTriple, List<Parameter>> parameterComments = new HashMap<>();
		final Map<EntryTriple, List<LocalVariableComment>> localVariableComments = new HashMap<>();

		public FullClassComments(String className) {
			this.className = className;
		}
	}

	private static void writeCommentFile(Path from, Path to) {
		try (InputStream in = Files.newInputStream(from); BufferedWriter out = Files.newBufferedWriter(to)) {
			ExtendedMappings mappings = MappingsProvider.readFullTinyMappings(in, false);

			if (!(mappings.getNamespaces() instanceof List<?>)) {
				throw new AssertionError("Mapping namespaces in " + from + " not given in order?");
			}
			List<String> namespaces = (List<String>) mappings.getNamespaces();

			if (namespaces.isEmpty()) {//There should actually be some mappings to convert
				throw new IllegalArgumentException("Provided empty mappings at " + from);
			}
			String primary = namespaces.get(0);

			Map<String, String> classCorrection;
			Map<EntryTriple, EntryTriple> methodCorrection, fieldCorrection;
			Map<MethodParameter, MethodParameter> paramCorrection;
			Map<LocalVariable, LocalVariable> localCorrection;
			if ("named".equals(primary)) {//Ideal case, comments will already be using the right names
				classCorrection = Collections.emptyMap();
				methodCorrection = fieldCorrection = Collections.emptyMap();
				paramCorrection = Collections.emptyMap();
				localCorrection = Collections.emptyMap();
			} else {//Comments will be in the primary namespace instead of named, will have to remap them
				classCorrection = mappings.getClassEntries().stream().collect(Collectors.toMap(entry -> entry.get(primary), entry -> entry.get("named")));
				methodCorrection = mappings.getMethodEntries().stream().collect(Collectors.toMap(entry -> entry.get(primary), entry -> entry.get("named")));
				fieldCorrection = mappings.getFieldEntries().stream().collect(Collectors.toMap(entry -> entry.get(primary), entry -> entry.get("named")));
				paramCorrection = mappings.getMethodParameterEntries().stream().collect(Collectors.toMap(entry -> entry.get(primary), entry -> entry.get("named")));
				localCorrection = mappings.getLocalVariableEntries().stream().collect(Collectors.toMap(entry -> entry.get(primary), entry -> entry.get("named")));
			}

			Map<String, FullClassComments> classToComments = new HashMap<>();
			Comments comments = mappings.getComments();

			for (Class comment : comments.getClassComments()) {
				remapComment(className -> {
					return classToComments.computeIfAbsent(className, FullClassComments::new).classComments;
				}, comment, Class::getClassName, classCorrection, Class::new);
			}
			for (Method comment : comments.getMethodComments()) {
				remapComment(method -> {
					return classToComments.computeIfAbsent(method.getOwner(), FullClassComments::new).methodComments;
				}, comment, Method::getMethod, methodCorrection, Method::new);
			}
			for (Field comment : comments.getFieldComments()) {
				remapComment(field -> {
					return classToComments.computeIfAbsent(field.getOwner(), FullClassComments::new).fieldComments;
				}, comment, Field::getField, fieldCorrection, Field::new);
			}
			for (Parameter comment : comments.getMethodParameterComments()) {
				remapComment(param -> {
					FullClassComments fullComments = classToComments.computeIfAbsent(param.getMethod().getOwner(), FullClassComments::new);
					return fullComments.parameterComments.computeIfAbsent(param.getMethod(), k -> new ArrayList<>());
				}, comment, Parameter::getParameter, paramCorrection, Parameter::new);
			}
			for (LocalVariableComment comment : comments.getLocalVariableComments()) {
				remapComment(local -> {
					FullClassComments fullComments = classToComments.computeIfAbsent(local.getMethod().getOwner(), FullClassComments::new);
					return fullComments.localVariableComments.computeIfAbsent(local.getMethod(), k -> new ArrayList<>());
				}, comment, LocalVariableComment::getLocalVariable, localCorrection, LocalVariableComment::new);
			}

			writeMappings(out, classToComments.values());
		} catch (IOException e) {
			throw new UncheckedIOException("Error converting " + from + " to " + to, e);
		}
	}

	private static <C extends CommentEntry, T> void remapComment(Function<T, List<C>> classToComments, C comment, Function<C, T> commentToTarget,
			Map<T, T> correction, BiFunction<List<String>, T, C> reconstructor) {
		T target = commentToTarget.apply(comment);
		target = correction.getOrDefault(target, target);
		classToComments.apply(target).add(reconstructor.apply(comment.getComments(), target));
	}

	public static void writeComments(BufferedWriter out, MappingSplat mappings) throws IOException {
		List<FullClassComments> comments = new ArrayList<>();

		for (CombinedMapping mapping : mappings) {
			if (!mapping.hasAnyComments()) continue; //Nothing to write

			FullClassComments comment = new FullClassComments(mapping.to);
			if (mapping.hasComment()) comment.classComments.add(new Class(Collections.singletonList(mapping.comment), mapping.to));

			for (CombinedMethod method : mapping.methods()) {
				if (!method.hasAnyComments()) continue;

				EntryTriple entry = new EntryTriple(mapping.to, method.to, method.toDesc);
				if (method.hasComment()) comment.methodComments.add(new Method(Collections.singletonList(method.comment), entry));

				method.iterateArgComments((index, argComment) -> {
					comment.parameterComments.computeIfAbsent(entry, k -> new ArrayList<>())
								.add(new Parameter(Collections.singletonList(argComment), new MethodParameter(entry, method.arg(index), index)));
				});
			}

			for (CombinedField field : mapping.fields()) {
				if (!field.hasComment()) continue;

				EntryTriple entry = new EntryTriple(mapping.to, field.to, field.toDesc);
				comment.fieldComments.add(new Field(Collections.singletonList(field.comment), entry));
			}

			comments.add(comment);
		}

		writeMappings(out, comments);
	}

	private static void writeMappings(BufferedWriter out, Collection<FullClassComments> allComments) throws IOException {
		out.write("tiny\t2\t0\tnamed");
		out.newLine();

		for (FullClassComments fullComments : allComments) {
			out.write("c\t");
			out.write(fullComments.className);
			out.newLine();

			for (Class comment : fullComments.classComments) {
				assert fullComments.className.equals(comment.getClassName());

				for (String line : comment.getComments()) {//Slightly dubious whether repeated comments is supported or not
					out.write("\tc\t");
					writeEscaped(out, line);
					out.newLine();
				}
			}

			for (Method comment : fullComments.methodComments) {
				out.write("\tm\t");
				out.write(comment.getMethod().getDesc());
				out.write('\t');
				out.write(comment.getMethod().getName());
				out.newLine();

				for (String line : comment.getComments()) {
					out.write("\t\tc\t");
					writeEscaped(out, line);
					out.newLine();
				}

				List<Parameter> params = fullComments.parameterComments.remove(comment.getMethod());
				if (params != null) {
					for (Parameter param : params) {
						assert comment.getMethod().equals(param.getParameter().getMethod());

						out.write("\t\tp\t");
						out.write(Integer.toString(param.getParameter().getLocalVariableIndex()));
						out.write('\t');
						out.write(param.getParameter().getName());
						out.newLine();

						for (String line : param.getComments()) {
							out.write("\t\t\tc\t");
							writeEscaped(out, line);
							out.newLine();
						}
					}
				}

				List<LocalVariableComment> locals = fullComments.localVariableComments.remove(comment.getMethod());
				if (locals != null) {
					for (LocalVariableComment local : locals) {
						assert comment.getMethod().equals(local.getLocalVariable().getMethod());

						out.write("\t\tv\t");
						out.write(Integer.toString(local.getLocalVariable().getLocalVariableIndex()));
						out.write('\t');
						out.write(Integer.toString(local.getLocalVariable().getLocalVariableStartOffset()));
						out.write('\t');
						out.write(Integer.toString(local.getLocalVariable().getLocalVariableTableIndex()));
						out.write('\t');
						out.write(local.getLocalVariable().getName());
						out.newLine();

						for (String line : local.getComments()) {
							out.write("\t\t\tc\t");
							writeEscaped(out, line);
							out.newLine();
						}
					}
				}
			}

			for (Entry<EntryTriple, List<Parameter>> entry : fullComments.parameterComments.entrySet()) {
				out.write("\tm\t");
				out.write(entry.getKey().getDesc());
				out.write('\t');
				out.write(entry.getKey().getName());
				out.newLine();

				for (Parameter param : entry.getValue()) {
					assert entry.getKey().equals(param.getParameter().getMethod());

					out.write("\t\tp\t");
					out.write(Integer.toString(param.getParameter().getLocalVariableIndex()));
					out.write('\t');
					out.write(param.getParameter().getName());
					out.newLine();

					for (String line : param.getComments()) {
						out.write("\t\t\tc\t");
						writeEscaped(out, line);
						out.newLine();
					}
				}

				List<LocalVariableComment> locals = fullComments.localVariableComments.remove(entry.getKey());
				if (locals != null) {
					for (LocalVariableComment local : locals) {
						assert entry.getKey().equals(local.getLocalVariable().getMethod());

						out.write("\t\tv\t");
						out.write(Integer.toString(local.getLocalVariable().getLocalVariableIndex()));
						out.write('\t');
						out.write(Integer.toString(local.getLocalVariable().getLocalVariableStartOffset()));
						out.write('\t');
						out.write(Integer.toString(local.getLocalVariable().getLocalVariableTableIndex()));
						out.write('\t');
						out.write(local.getLocalVariable().getName());
						out.newLine();

						for (String line : local.getComments()) {
							out.write("\t\t\tc\t");
							writeEscaped(out, line);
							out.newLine();
						}
					}
				}
			}

			for (Entry<EntryTriple, List<LocalVariableComment>> entry : fullComments.localVariableComments.entrySet()) {
				out.write("\tm\t");
				out.write(entry.getKey().getDesc());
				out.write('\t');
				out.write(entry.getKey().getName());
				out.newLine();

				for (LocalVariableComment local : entry.getValue()) {
					assert entry.getKey().equals(local.getLocalVariable().getMethod());

					out.write("\t\tv\t");
					out.write(Integer.toString(local.getLocalVariable().getLocalVariableIndex()));
					out.write('\t');
					out.write(Integer.toString(local.getLocalVariable().getLocalVariableStartOffset()));
					out.write('\t');
					out.write(Integer.toString(local.getLocalVariable().getLocalVariableTableIndex()));
					out.write('\t');
					out.write(local.getLocalVariable().getName());
					out.newLine();

					for (String line : local.getComments()) {
						out.write("\t\t\tc\t");
						writeEscaped(out, line);
						out.newLine();
					}
				}
			}

			for (Field comment : fullComments.fieldComments) {
				out.write("\tf\t");
				out.write(comment.getField().getDesc());
				out.write('\t');
				out.write(comment.getField().getName());
				out.newLine();

				for (String line : comment.getComments()) {
					out.write("\t\tc\t");
					writeEscaped(out, line);
					out.newLine();
				}
			}
		}
	}

	private static final String TO_ESCAPE = "\\\n\r\0\t";
	private static final String ESCAPED = "\\nr0t";
	private static void writeEscaped(Writer out, String text) throws IOException {
		final int len = text.length();
		int start = 0;

		for (int pos = 0; pos < len; pos++) {
			char c = text.charAt(pos);
			int idx = TO_ESCAPE.indexOf(c);

			if (idx >= 0) {
				out.write(text, start, pos - start);
				out.write('\\');
				out.write(ESCAPED.charAt(idx));
				start = pos + 1;
			}
		}

		out.write(text, start, len - start);
	}
}