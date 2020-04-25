/*
 * Copyright 2019, 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers.mappings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.StringJoiner;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.collect.Iterables;

import net.fabricmc.stitch.util.Pair;

import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping;
import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping.Field;
import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping.Method;
import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping.Method.Arg;
import net.fabricmc.loom.providers.mappings.MappingSplat.CombinedMapping;
import net.fabricmc.loom.providers.mappings.MappingSplat.CombinedMapping.CombinedField;
import net.fabricmc.loom.providers.mappings.MappingSplat.CombinedMapping.CombinedMethod;
import net.fabricmc.loom.util.ThrowingIntObjConsumer;

public class MappingSplat implements Iterable<CombinedMapping> {
	public static class CombinedMapping {
		public static class CombinedField {
			public final String from, fallback, to;
			public final String fromDesc, fallbackDesc, toDesc;
			public final String comment;

			public CombinedField(String from, String fromDesc, String fallback, String fallbackDesc, String to, String toDesc, String comment) {
				this.from = from;
				this.fromDesc = fromDesc;
				this.fallback = fallback;
				this.fallbackDesc = fallbackDesc;
				this.to = to;
				this.toDesc = toDesc;
				this.comment = comment;
			}

			public boolean hasNameChange() {
				return !from.equals(fallback);
			}

			public boolean hasComment() {
				return comment != null;
			}
		}
		public static class CombinedMethod extends CombinedField {
			private final Arg[] args;

			CombinedMethod(String name, String fromDesc, String fallbackDesc, String toDesc, String comment, Arg[] args) {
				this(name, fromDesc, name, fallbackDesc, name, toDesc, comment, args);
			}

			public CombinedMethod(String from, String fromDesc, String fallback, String fallbackDesc, String to, String toDesc, String comment, Arg[] args) {
				super(from, fromDesc, fallback, fallbackDesc, to, toDesc, comment);

				this.args = args != null ? args : new Arg[0];
			}

			public boolean hasAnyComments() {
				return hasComment() || hasArgComments();
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

			public String arg(int index) {
				return args.length > index && args[index] != null ? args[index].name : null;
			}

			public String argComment(int index) {
				return args.length > index && args[index] != null ? args[index].comment : null;
			}

			public <T extends Throwable> void iterateArgs(ThrowingIntObjConsumer<String, T> argConsumer) throws T {
				for (int i = args.length - 1; i >= 0; i--) {
					if (args[i] != null && args[i].name != null) argConsumer.accept(i, args[i].name);
				}
			}

			public <T extends Throwable> void iterateArgComments(ThrowingIntObjConsumer<String, T> argCommentConsumer) throws T {
				for (int i = 0; i < args.length; i++) {
					if (args[i] != null && args[i].comment != null) argCommentConsumer.accept(i, args[i].comment);
				}
			}
		}

		public final String from, fallback, to, comment;
		final Map<String, CombinedMethod> methods = new HashMap<>();
		final Map<String, CombinedField> fields = new HashMap<>();

		public CombinedMapping(String from, String fallback, String to, String comment) {
			this.from = from;
			this.fallback = fallback;
			this.to = to;
			this.comment = comment;
		}

		public boolean hasNameChange() {
			return !from.equals(fallback);
		}

		public boolean hasComment() {
			return comment != null;
		}

		public boolean hasAnyComments() {
			if (hasComment()) return true;

			for (CombinedMethod method : methods()) {
				if (method.hasAnyComments()) {
					return true;
				}
			}

			for (CombinedField field : fields()) {
				if (field.hasComment()) {
					return true;
				}
			}

			return false;
		}

		public Iterable<CombinedMethod> methods() {
			return methods.values();
		}

		Stream<CombinedMethod> methodStream() {
			return methods.values().stream();
		}

		public Iterable<CombinedMethod> methodsWithNames() {
			return Iterables.filter(methods(), CombinedMethod::hasNameChange);
		}

		public Iterable<CombinedMethod> methodsWithArgs() {
			return Iterables.filter(methods(), CombinedMethod::hasArgs);
		}

		public Iterable<CombinedField> fields() {
			return fields.values();
		}

		public Iterable<CombinedField> fieldsWithNames() {
			return Iterables.filter(fields(), CombinedField::hasNameChange);
		}
	}

	private final Map<String, CombinedMapping> mappings = new HashMap<>();

	/** Note: {@code mappings} will get mutated to gain any classes or members it lacks relative to {@code fallback} */
	public MappingSplat(MappingBlob mappings, MappingBlob fallback) {
		UnaryOperator<String> fallbackRemapper = className -> {
			String triedMapping = fallback.tryMapName(className);
			return triedMapping != null ? triedMapping : className;
		};
		UnaryOperator<String> remapper = className -> {
			String triedMapping = mappings.tryMapName(className);
			return triedMapping != null ? triedMapping : fallbackRemapper.apply(className);
		};

		//Fallback should cover all of mapping's class names, with the potential for more it doesn't have
		for (Mapping mapping : fallback) {
			String notch = mapping.from;
			Mapping other = mappings.getOrDummy(notch);

			String inter = mapping.toOr(notch);
			String name = findName(other.to, inter, notch, mappings);
			assert !inter.equals(notch) || name.equals(notch);
			String comment = (other.comment().isPresent() ? other : mapping).comment().orElse(null);

			CombinedMapping combined = new CombinedMapping(notch, inter, name, comment);
			this.mappings.put(notch, combined);

			for (Method method : mapping.methods()) {
				Method otherMethod = other.method(method);
				notch = method.fromName;
				inter = method.nameOr(notch);

				if (notch.charAt(0) == '<') {
					//Constructors (and static blocks) shouldn't appear in fallback from intermediary mappings not assigning constructor names
					throw new AssertionError(String.format("Tried to map special method in %s (%s): %s -> %s -> %s", mapping.from, name, notch, inter, otherMethod.nameOr(inter)));
				} else {
					name = otherMethod.nameOr(inter);
				}
				String interDesc = makeDesc(method, fallbackRemapper);
				String nameDesc = makeDesc(otherMethod, remapper);
				comment = (otherMethod.comment().isPresent() ? otherMethod : method).comment().orElse(null);

				CombinedMethod combinedMethod = new CombinedMethod(notch, method.fromDesc, inter, interDesc, name, nameDesc, comment, otherMethod.extendArgs(method));
				combined.methods.put(notch + method.fromDesc, combinedMethod);
			}

			for (Field field : mapping.fields()) {
				Field otherField = other.field(field);
				notch = field.fromName;

				inter = field.nameOr(notch);
				String interDesc = makeDesc(field, fallbackRemapper);
				name = otherField.nameOr(inter);
				String nameDesc = makeDesc(otherField, remapper);
				comment = (otherField.comment().isPresent() ? otherField : field).comment().orElse(null);

				CombinedField combinedField = new CombinedField(notch, field.fromDesc, inter, interDesc, name, nameDesc, comment);
				combined.fields.put(notch + ";;" + field.fromDesc, combinedField);
			}
		}

		//Sometimes Yarn versions include their own mappings without Intermediary backing (which is bad really)
		Map<String, Pair<String, Map<String, String>>> yarnOnlyMappings = new HashMap<>();

		for (Mapping mapping : mappings) {
			String notch = mapping.from;

			CombinedMapping combined = this.mappings.get(notch);
			if (combined == null) {
				assert fallback.tryMapName(notch) == null;
				throw new IllegalStateException("Extra class mapping missing from fallback! Unable to find " + notch + " (mapped as " + mapping.to + ')');
			}
			Mapping other = fallback.getOrDummy(notch); //Won't be a dummy if combined is not null

			for (Method method : mapping.methods()) {
				if (other.hasMethod(method)) continue;
				notch = method.fromName;

				if (notch.charAt(0) != '<' && !notch.equals(method.nameOr(notch))) {
					//Changing Notch names without intermediaries to back it up is not cross-version safe and shouldn't be done
					//throw new IllegalStateException("Extra mappings missing from fallback! Unable to find " + mapping.from + '#' + method.fromName + method.fromDesc + " (" + mapping.to + '#' + method.name() + ')');

					//Yarn sometimes does however, so we'll just the cases where it does and not use them
					yarnOnlyMappings.computeIfAbsent(mapping.from, k -> Pair.of(mapping.to, new HashMap<>())).getRight().put(method.fromName + method.fromDesc, method.name());
				}

				if (method.comment().isPresent() || method.hasArgs()) {
					String interDesc = remapDesc(method.fromDesc, fallbackRemapper);
					String nameDesc = makeDesc(method, remapper);
					String comment = method.comment().orElse(null);

					CombinedMethod bonusMethod = new CombinedMethod(notch, method.fromDesc, interDesc, nameDesc, comment, method.cloneArgs());
					combined.methods.put(notch + method.fromDesc, bonusMethod);
				}
			}

			for (Field field : mapping.fields()) {
				if (other.hasField(field)) continue;

				yarnOnlyMappings.computeIfAbsent(mapping.from, k -> Pair.of(mapping.to, new HashMap<>())).getRight().put(field.fromDesc + ' ' + field.fromName, field.name());
				//throw new IllegalStateException("Extra mapping missing from fallback! Unable to find " + mapping.from + '#' + field.fromName + " (" + field.fromDesc + ')');
			}
		}

		if (!yarnOnlyMappings.isEmpty()) {//We should crash from this, but that's a nuisance as Yarn has to get fixed
			System.out.println("Invalid Yarn mappings (ie missing Intermediaries) found:");

			for (Entry<String, Pair<String, Map<String, String>>> entry : yarnOnlyMappings.entrySet()) {
				String notch = entry.getKey();
				String yarn = entry.getValue().getLeft();
				System.out.println("\tIn " + notch + " (" + yarn + ')');

				//Split the methods apart from the fields
				Map<Boolean, List<Entry<String, String>>> extras = entry.getValue().getRight().entrySet().stream().collect(Collectors.partitioningBy(extra -> extra.getKey().contains("(")));

				printExtras(extras.get(Boolean.TRUE), "methods");
				printExtras(extras.get(Boolean.FALSE), "fields");

				System.out.println();
			}
		}
	}

	private static void printExtras(List<Entry<String, String>> extras, String type) {
		if (!extras.isEmpty()) {
			System.out.println("\t\tExtra mapped " + type + ':');

			for (Entry<String, String> extra : extras) {
				System.out.println("\t\t\t" + extra.getKey() + " => " + extra.getValue());
			}
		}
	}

	@Override
	public Iterator<CombinedMapping> iterator() {
		return mappings.values().iterator();
	}

	public boolean hasComments() {
		return mappings.values().stream().anyMatch(CombinedMapping::hasAnyComments);
	}

	public boolean hasArgs() {
		return mappings.values().stream().flatMap(CombinedMapping::methodStream).anyMatch(CombinedMethod::hasArgs);
	}

	public boolean hasArgNames() {
		return mappings.values().stream().flatMap(CombinedMapping::methodStream).anyMatch(CombinedMethod::hasArgNames);
	}

	public boolean hasArgComments() {
		return mappings.values().stream().flatMap(CombinedMapping::methodStream).anyMatch(CombinedMethod::hasArgComments);
	}

	private static String findName(String name, String inter, String notch, MappingBlob mappings) {
		if (name != null) {
			return name;
		} else {
			String[] segments = notch.split("\\$");

			if (segments.length > 1) {
				for (int end = segments.length - 1, depth = 1; end > 0; end--, depth++) {
					StringJoiner nameBits = new StringJoiner("$");
					for (int i = 0; i < end; i++) {
						nameBits.add(segments[i]);
					}

					String parent = mappings.tryMapName(nameBits.toString());
					if (parent != null) {
						String[] extra = inter.split("\\$");

						nameBits = new StringJoiner("$");
						nameBits.add(parent);
						for (int extraEnd = extra.length, i = extraEnd - depth; i < extraEnd; i++) {
							nameBits.add(extra[i]);
						}
						return nameBits.toString();
					}
				}
			}

			return inter;
		}
	}

	static String makeDesc(Field method, UnaryOperator<String> remapper) {
		if (method.desc() != null) {
			return method.desc();
		} else {
			return remapDesc(method.fromDesc, remapper);
		}
	}

	private static final Pattern CLASS_FINDER = Pattern.compile("L([^;]+);");
	public static String remapDesc(String desc, UnaryOperator<String> classRemapper) {
		StringBuffer buf = new StringBuffer();

		Matcher matcher = CLASS_FINDER.matcher(desc);
		while (matcher.find()) {
			matcher.appendReplacement(buf, Matcher.quoteReplacement('L' + classRemapper.apply(matcher.group(1)) + ';'));
		}
		matcher.appendTail(buf);

		return buf.toString();
	}
}