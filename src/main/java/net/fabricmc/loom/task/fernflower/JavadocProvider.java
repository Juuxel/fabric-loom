/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.task.fernflower;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.jetbrains.java.decompiler.struct.StructClass;
import org.jetbrains.java.decompiler.struct.StructField;
import org.jetbrains.java.decompiler.struct.StructMethod;

import net.fabricmc.fernflower.api.IFabricJavadocProvider;
import net.fabricmc.mappings.EntryTriple;
import net.fabricmc.mappings.MappingsProvider;
import net.fabricmc.mappings.model.CommentEntry.Class;
import net.fabricmc.mappings.model.CommentEntry.Field;
import net.fabricmc.mappings.model.CommentEntry.Method;
import net.fabricmc.mappings.model.CommentEntry.Parameter;
import net.fabricmc.mappings.model.Comments;

public class JavadocProvider implements IFabricJavadocProvider {
	private final Map<String, List<String>> classComments;
	private final Map<EntryTriple, List<String>> methodComments;
	private final Map<EntryTriple, List<Parameter>> paramComments;
	private final Map<EntryTriple, List<String>> fieldComments;

	public JavadocProvider(File mappings) {
		assert mappings.exists();

		Comments comments;
		try (InputStream in = new FileInputStream(mappings)) {
			comments = MappingsProvider.readFullTinyMappings(in, true).getComments();
		} catch (IOException e) {
			throw new RuntimeException("Error reading decompiler mappings at " + mappings, e);
		}

		classComments = comments.getClassComments().stream().collect(Collectors.toMap(Class::getClassName, Class::getComments));
		methodComments = comments.getMethodComments().stream().collect(Collectors.toMap(Method::getMethod, Method::getComments));
		paramComments = comments.getMethodParameterComments().stream().collect(Collectors.groupingBy(comment -> comment.getParameter().getMethod()));
		fieldComments = comments.getFieldComments().stream().collect(Collectors.toMap(Field::getField, Field::getComments));
	}

	@Override
	public String getClassDoc(StructClass structClass) {
		return Optional.ofNullable(classComments.get(structClass.qualifiedName)).map(comments -> String.join("\n", comments)).orElse(null);
	}

	@Override
	public String getMethodDoc(StructClass structClass, StructMethod structMethod) {
		EntryTriple method = new EntryTriple(structClass.qualifiedName, structMethod.getName(), structMethod.getDescriptor());
		List<String> comment = methodComments.get(method);
		List<Parameter> params = paramComments.get(method);

		if (comment == null) {
			if (params == null) {
				return null; //No comment for the method or any of it's parameters
			} else {
				comment = new ArrayList<>(); //Only parameter comments
			}
		} else if (params != null) {//Both method and parameter comments
			comment = new ArrayList<>(comment);
			comment.add(""); //Leave space between the method comment and the parameter comment(s)
		}

		if (params != null) {
			params.sort(Comparator.comparingInt(param -> param.getParameter().getLocalVariableIndex()));

			for (Parameter param : params) {
				assert method.equals(param.getParameter().getMethod());
				comment.add(String.format("@param %s %s", param.getParameter().getName(), String.join("\n\t", param.getComments())));
			}
		}

		return String.join("\n", comment);
	}

	@Override
	public String getFieldDoc(StructClass structClass, StructField structField) {
		return Optional.ofNullable(fieldComments.get(new EntryTriple(structClass.qualifiedName, structField.getName(), structField.getDescriptor()))).map(comments -> String.join("\n", comments)).orElse(null);
	}
}