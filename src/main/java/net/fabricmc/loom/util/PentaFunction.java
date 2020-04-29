/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.util;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Represents a function that accepts five arguments and produces a result.
 * This is the five-arity specialisation of {@link Function}.
 *
 * <p>This is a functional interface whose functional method is
 * 	{@link #apply(Object, Object, Object, Object, Object)}.
 *
 * @param <T> the type of the first argument to the function
 * @param <U> the type of the second argument to the function
 * @param <V> the type of the third argument to the function
 * @param <W> the type of the fourth argument to the function
 * @param <X> the type of the fifth argument to the function
 * @param <R> the type of the result of the function
 *
 * @see Function
 * @see BiFunction
 */
@FunctionalInterface
public interface PentaFunction<T, U, V, W, X, R> {
	/**
	 * Applies this function to the given arguments.
	 *
	 * @param t The first function argument
	 * @param u The second function argument
	 * @param v The third function argument
	 * @param w The fourth function argument
	 * @param x The fifth function argument
	 * @return the function result
	 */
	R apply(T t, U u, V v, W w, X x);

	/**
	 * Returns a composed function that first applies this function to its input,
	 * and then applies the {@code after} function to the result. If evaluation of
	 * either function throws an exception, it is relayed to the caller of the
	 * composed function.
	 *
	 * @param <Y> The type of output of the {@code after} function, and of the
	 *            composed function
	 * @param after The function to apply after this function is applied
	 *
	 * @return A composed function that first applies this function and then applies
	 *         the {@code after} function
	 *
	 * @throws NullPointerException If after is null
	 */
	default <Y> PentaFunction<T, U, V, W, X, Y> andThen(Function<? super R, ? extends Y> after) {
		Objects.requireNonNull(after);
		return (t, u, v, w, x) -> after.apply(apply(t, u, v, w, x));
	}
}