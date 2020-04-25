/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers.mappings;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ObjIntConsumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

import com.google.common.collect.Streams;

import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping;
import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping.Field;
import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping.Method;

public class MappingBlob implements IMappingAcceptor, Iterable<Mapping> {
	public static class Mapping {
		public static class Method extends Field {
			static class Arg {
				String name, comment;
			}
			private Arg[] args = new Arg[0];

			public Method(String fromName, String fromDesc) {
				super(fromName, fromDesc);

				if (fromName.charAt(0) == '<') {
					//If we're an <init> (or theoretically a <clinit>) we'll never get a name so should do it now
					setMapping(fromName, null);
				}
			}

			private Arg extendTo(int index) {
				if (args.length <= index) {
					Arg[] longerArgs = new Arg[index + 1];
					System.arraycopy(args, 0, longerArgs, 0, args.length);
					args = longerArgs;
				}

				return args[index] == null ? args[index] = new Arg() : args[index];
			}

			void addArg(int index, String name) {
				extendTo(index).name = name;
			}

			void addArgComment(int index, String comment) {
				extendTo(index).comment = comment;
			}

			public boolean hasArgs() {
				return args.length > 0;
			}

			public boolean hasArgNames() {
				return Arrays.stream(args).filter(Objects::nonNull).anyMatch(arg -> arg.name != null);
			}

			public boolean hasArgComments() {
				return Arrays.stream(args).filter(Objects::nonNull).anyMatch(arg -> arg.comment != null);
			}

			Arg[] cloneArgs() {
				return Arrays.stream(args).map(arg -> {
					if (arg == null) return null;
					Arg clone = new Arg();

					clone.name = arg.name;
					clone.comment = arg.comment;

					return clone;
				}).toArray(Arg[]::new);
			}

			void cloneArgs(Method method) {
				if (method.args.length > args.length) args = new Arg[method.args.length];

				for (int i = 0; i < method.args.length; i++) {
					Arg arg = method.args[i];
					if (arg == null) continue;

					Arg clone = args[i] == null ? args[i] = new Arg() : args[i];
					clone.name = arg.name;
					clone.comment = arg.comment;
				}
			}

			Arg[] extendArgs(Method that) {
				Arg[] args = new Arg[Math.max(this.args.length, that.args.length)];

				Arg[] shorter = args.length == this.args.length ? that.args : this.args;
				assert shorter.length == Math.min(this.args.length, that.args.length);
				Arg[] longer = shorter == this.args ? that.args : this.args;
				assert longer.length == args.length;

				for (int i = 0; i < shorter.length; i++) {
					Arg existing = this.args[i];
					Arg other = that.args[i];
					if (existing == null && other == null) continue; //Neither have a suggestion

					Arg clone = args[i] = new Arg();
					clone.name = existing != null && existing.name != null ? existing.name : other != null ? other.name : null;
					clone.comment = existing != null && existing.comment != null ? existing.comment : other != null ? other.comment : null;
				}

				for (int i = shorter.length; i < longer.length; i++) {
					Arg only = longer[i];
					if (only == null) continue;

					Arg clone = args[i] = new Arg();
					clone.name = only.name;
					clone.comment = only.comment;
				}

				return args;
			}

			public String arg(int index) {
				return args.length > index && args[index] != null ? args[index].name : null;
			}

			public Optional<String> argComment(int index) {
				return args.length > index ? Optional.ofNullable(args[index]).map(arg -> arg.comment) : Optional.empty();
			}

			public void iterateArgs(ObjIntConsumer<String> argConsumer) {
				for (int i = 0; i < args.length; i++) {
					if (args[i] != null && args[i].name != null) argConsumer.accept(args[i].name, i);
				}
			}

			public void iterateArgComments(ObjIntConsumer<String> argCommentConsumer) {
				for (int i = 0; i < args.length; i++) {
					if (args[i] != null && args[i].comment != null) argCommentConsumer.accept(args[i].comment, i);
				}
			}
		}
		public static class Field {
			public final String fromName, fromDesc;
			private String toName, toDesc;
			String comment;

			public Field(String fromName, String fromDesc) {
				this.fromName = fromName;
				this.fromDesc = fromDesc;
			}

			void setMapping(String name, String desc) {
				this.toName = name;
				this.toDesc = desc;
			}

			public String name() {
				return toName;
			}

			public String nameOr(String alternative) {
				return toName != null ? toName : alternative;
			}

			public String desc() {
				return toDesc;
			}

			public Optional<String> comment() {
				return Optional.ofNullable(comment);
			}
		}

		public final String from;
		String to;
		String comment;
		final Map<String, Method> methods = new HashMap<>();
		final Map<String, Field> fields = new HashMap<>();

		public Mapping(String from) {
			this.from = from;
		}

		public String to() {
			return to;
		}

		public String toOr(String alternative) {
			return to != null ? to : alternative;
		}

		public Optional<String> comment() {
			return Optional.ofNullable(comment);
		}

		public Iterable<Method> methods() {
			return methods.values();
		}

		public boolean hasMethod(Method other) {
			return methods.containsKey(other.fromName + other.fromDesc);
		}

		public Method method(Method other) {
			return method(other.fromName, other.fromDesc);
		}

		Method method(String srcName, String srcDesc) {
			return methods.computeIfAbsent(srcName + srcDesc, k -> new Method(srcName, srcDesc));
		}

		public Iterable<Field> fields() {
			return fields.values();
		}

		public boolean hasField(Field other) {
			return fields.containsKey(other.fromName + ";;" + other.fromDesc);
		}

		public Field field(Field other) {
			return field(other.fromName, other.fromDesc);
		}

		Field field(String srcName, String srcDesc) {
			return fields.computeIfAbsent(srcName + ";;" + srcDesc, k -> new Field(srcName, srcDesc));
		}
	}

	private final Map<String, Mapping> mappings = new HashMap<>();

	public Mapping get(String srcName) {
		return mappings.computeIfAbsent(srcName, Mapping::new);
	}

	public Mapping getOrDummy(String srcName) {
		Mapping mapping = mappings.get(srcName);
		return mapping != null ? mapping : new DummyMapping(srcName);
	}

	public String tryMapName(String srcName) {
		Mapping mapping = mappings.get(srcName);
		return mapping != null ? mapping.to : null;
	}

	@Override
	public void acceptClass(String srcName, String dstName) {
		get(srcName).to = dstName;
	}

	@Override
	public void acceptClassComment(String className, String comment) {
		get(className).comment = comment;
	}

	@Override
	public void acceptMethod(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		get(srcClsName).method(srcName, srcDesc).setMapping(dstName, dstDesc);
	}

	@Override
	public void acceptMethodComment(String className, String methodName, String desc, String comment) {
		get(className).method(methodName, desc).comment = comment;
	}

	@Override
	public void acceptMethodArg(String srcClsName, String srcMethodName, String srcMethodDesc, int lvIndex, String dstArgName) {
		get(srcClsName).method(srcMethodName, srcMethodDesc).addArg(lvIndex, dstArgName);
	}

	@Override
	public void acceptMethodArgComment(String className, String methodName, String desc, int lvIndex, String comment) {
		get(className).method(methodName, desc).addArgComment(lvIndex, comment);
	}

	@Override
	public void acceptField(String srcClsName, String srcName, String srcDesc, String dstClsName, String dstName, String dstDesc) {
		get(srcClsName).field(srcName, srcDesc).setMapping(dstName, dstDesc);
	}

	@Override
	public void acceptFieldComment(String className, String fieldName, String desc, String comment) {
		get(className).field(fieldName, desc).comment = comment;
	}

	@Override
	public Iterator<Mapping> iterator() {
		return mappings.values().iterator();
	}

	public Stream<Mapping> stream() {
		return mappings.values().stream();
	}

	public Stream<Method> streamMethods() {
		return stream().map(Mapping::methods).flatMap(Streams::stream);
	}

	public Stream<Field> streamFields() {
		return stream().map(Mapping::fields).flatMap(Streams::stream);
	}

	public enum InvertionTarget {
		FIELDS, METHODS, MEMBERS, METHOD_ARGS, ALL;
	}

	public MappingBlob invert(InvertionTarget... targets) {
		Set<InvertionTarget> aims = EnumSet.noneOf(InvertionTarget.class);
		MappingBlob invertion = new MappingBlob();

		for (InvertionTarget target : targets) {
			switch (target) {
			case METHOD_ARGS:
				aims.add(InvertionTarget.METHODS);
			case FIELDS:
			case METHODS:
				aims.add(target);
				break;

			case ALL:
				aims.add(InvertionTarget.METHOD_ARGS);
			case MEMBERS:
				aims.add(InvertionTarget.FIELDS);
				aims.add(InvertionTarget.METHODS);
				break;
			}
		}

		boolean doFields = aims.contains(InvertionTarget.FIELDS);
		boolean doMethods = aims.contains(InvertionTarget.METHODS);
		boolean doArgs = aims.contains(InvertionTarget.METHOD_ARGS);

		UnaryOperator<String> classRemapper = name -> {
			String mapping = tryMapName(name);
			return mapping != null ? mapping : name;
		};

		for (Mapping mapping : mappings.values()) {
			if (mapping.to == null) {//If there is no mapped class name there is nothing for it to invert to
				assert Streams.stream(mapping.fields()).map(Field::name).allMatch(Objects::isNull): mapping.from + " doesn't change name but has fields which do";
				assert Streams.stream(mapping.methods()).map(Method::name).allMatch(Objects::isNull): mapping.from + " doesn't change name but has methods which do";
				continue;
			}

			invertion.acceptClass(mapping.to, mapping.from);
			invertion.acceptClassComment(mapping.to, mapping.comment);

			if (doFields) {
				for (Field field : mapping.fields()) {
					if (field.name() == null) continue;
					//assert field.desc() != null: mapping.from + '#' + field.fromName + " (" + field.fromDesc + ") changes name without a changed descriptor";

					String desc = MappingSplat.makeDesc(field, classRemapper);
					invertion.acceptField(mapping.to, field.name(), desc, mapping.from, field.fromName, field.fromDesc);
					invertion.acceptFieldComment(mapping.to, field.name(), desc, field.comment);
				}
			}

			if (doMethods) {
				for (Method method : mapping.methods()) {
					if (method.name() == null) continue;
					//assert method.desc() != null: mapping.from + '#' + method.fromName + method.fromDesc + " changes name without a changed descriptor";

					String desc = MappingSplat.makeDesc(method, classRemapper);
					invertion.acceptMethod(mapping.to, method.name(), desc, mapping.from, method.fromName, method.fromDesc);
					invertion.acceptMethodComment(mapping.to, method.name(), desc, method.comment);
					if (doArgs) invertion.get(mapping.to).method(method.name(), desc).cloneArgs(method);
				}
			}
		}

		return invertion;
	}

	public MappingBlob rename(MappingBlob blob) {
		MappingBlob remap = new MappingBlob();

		UnaryOperator<String> classRemapper = name -> {
			String mapping = blob.tryMapName(name);
			return mapping != null ? mapping : name;
		};

		for (Mapping mapping : mappings.values()) {
			Mapping bridge = blob.mappings.get(mapping.from);
			boolean useBridge = bridge != null;

			String className = useBridge ? bridge.to : mapping.from;
			remap.acceptClass(className, mapping.to);
			remap.acceptClassComment(className, mapping.comment);

			for (Field field : mapping.fields()) {
				if (useBridge && bridge.hasField(field)) {
					Field bridged = bridge.field(field);

					if (bridged.name() != null) {
						assert bridged.desc() != null;
						remap.acceptField(className, bridged.name(), bridged.desc(), mapping.to, field.name(), field.desc());
						remap.acceptFieldComment(className, bridged.name(), bridged.desc(), field.comment);
						continue;
					}
				}

				String desc = MappingSplat.remapDesc(field.fromDesc, classRemapper);
				remap.acceptField(className, field.fromName, desc, mapping.to, field.name(), field.desc());
				remap.acceptFieldComment(className, field.fromName, desc, field.comment);
			}

			for (Method method : mapping.methods()) {
				if (useBridge && bridge.hasMethod(method)) {
					Method bridged = bridge.method(method);

					if (bridged.name() != null) {
						assert bridged.desc() != null;
						remap.acceptMethod(className, bridged.name(), bridged.desc(), mapping.to, method.name(), method.desc());
						remap.acceptMethodComment(className, bridged.name(), bridged.desc(), method.comment);
						remap.get(className).method(bridged.name(), bridged.desc()).cloneArgs(method);
						continue;
					}
				}

				String desc = MappingSplat.remapDesc(method.fromDesc, classRemapper);
				remap.acceptMethod(className, method.fromName, desc, mapping.to, method.name(), method.desc());
				remap.acceptMethodComment(className, method.fromName, desc, method.comment);
				remap.get(className).method(method.fromName, desc).cloneArgs(method);
			}

		}

		return remap;
	}
}