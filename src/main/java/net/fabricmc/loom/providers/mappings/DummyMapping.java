/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers.mappings;

import java.util.Optional;
import java.util.function.ObjIntConsumer;

import net.fabricmc.loom.providers.mappings.MappingBlob.Mapping;

class DummyMapping extends Mapping {
	private static class DummyField extends Field {
		public DummyField(String fromName, String fromDesc) {
			super(fromName, fromDesc);
		}

		@Override
		public String name() {
			return null;
		}

		@Override
		public String nameOr(String alternative) {
			return alternative;
		}

		@Override
		void setMapping(String name, String desc) {
			throw new UnsupportedOperationException("Cannot set a name for a dummy field");
		}

		@Override
		public Optional<String> comment() {
			return Optional.empty();
		}
	}
	private static class DummyMethod extends Method {
		public DummyMethod(String fromName, String fromDesc) {
			super(fromName, fromDesc);
		}

		@Override
		void setMapping(String name, String desc) {
			throw new UnsupportedOperationException("Cannot set a name for a dummy method");
		}

		@Override
		void addArg(int index, String name) {
			throw new UnsupportedOperationException("Cannot add an argument to a dummy method");
		}

		@Override
		void addArgComment(int index, String comment) {
			throw new UnsupportedOperationException("Cannot add an argument comment to a dummy method");
		}

		@Override
		public boolean hasArgs() {
			return false;
		}

		@Override
		public boolean hasArgNames() {
			return false;
		}

		@Override
		public boolean hasArgComments() {
			return false;
		}

		@Override
		Arg[] cloneArgs() {
			return new Arg[0];
		}

		@Override
		void cloneArgs(Method method) {
			throw new UnsupportedOperationException("Cannot add an argument to a dummy method");
		}

		@Override
		Arg[] extendArgs(Method that) {
			return that.cloneArgs();
		}

		@Override
		public String arg(int index) {
			return null;
		}

		@Override
		public Optional<String> argComment(int index) {
			return Optional.empty();
		}

		@Override
		public void iterateArgs(ObjIntConsumer<String> argConsumer) {
		}

		@Override
		public void iterateArgComments(ObjIntConsumer<String> argCommentConsumer) {
		}
	}

	public DummyMapping(String from) {
		super(from);
	}

	@Override
	public String to() {
		return null;
	}

	@Override
	public String toOr(String alternative) {
		return alternative;
	}

	@Override
	public Optional<String> comment() {
		return Optional.empty();
	}

	@Override
	public boolean hasMethod(Method other) {
		return false;
	}

	@Override
	Method method(String srcName, String srcDesc) {
		return new DummyMethod(srcName, srcDesc);
	}

	@Override
	public boolean hasField(Field other) {
		return false;
	}

	@Override
	Field field(String srcName, String srcDesc) {
		return new DummyField(srcName, srcDesc);
	}
}