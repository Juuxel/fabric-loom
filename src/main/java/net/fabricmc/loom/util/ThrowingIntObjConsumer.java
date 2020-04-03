/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.util;

import java.util.function.ObjIntConsumer;

public interface ThrowingIntObjConsumer<O, T extends Throwable> {
	void accept(int value, O object) throws T;

	interface IntObjConsumer<T> extends ThrowingIntObjConsumer<T, RuntimeException>, ObjIntConsumer<T> {
		@Override
		default void accept(T object, int value) {
			accept(value, object);
		}
	}
}