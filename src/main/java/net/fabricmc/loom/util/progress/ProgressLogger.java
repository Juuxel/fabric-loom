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

package net.fabricmc.loom.util.progress;

import java.lang.reflect.Method;

import org.gradle.api.Project;
import org.gradle.api.logging.Logger;

/**
 * Wrapper to ProgressLogger internal API.
 */
public class ProgressLogger {
	private final Logger logger;
	private final Object logFactory;
	private final Method getDescription, setDescription, start, started, startedArg, progress, completed, completedArg;

	private ProgressLogger(Logger logger, Object logFactory) {
		this.logger = logger;
		this.logFactory = logFactory;
		this.getDescription = getMethod("getDescription");
		this.setDescription = getMethod("setDescription", String.class);
		this.start = getMethod("start", String.class, String.class);
		this.started = getMethod("started");
		this.startedArg = getMethod("started", String.class);
		this.progress = getMethod("progress", String.class);
		this.completed = getMethod("completed");
		this.completedArg = getMethod("completed", String.class, boolean.class);
	}

	private static Class<?> getFactoryClass() {
		Class<?> progressLoggerFactoryClass = null;

		try {
			//Gradle 2.14 and higher
			progressLoggerFactoryClass = Class.forName("org.gradle.internal.logging.progress.ProgressLoggerFactory");
		} catch (ClassNotFoundException e) {
			//prior to Gradle 2.14
			try {
				progressLoggerFactoryClass = Class.forName("org.gradle.logging.ProgressLoggerFactory");
			} catch (ClassNotFoundException oldE) {
				// Unsupported Gradle version
			}
		}

		return progressLoggerFactoryClass;
	}

	private Method getMethod(String methodName, Class<?>... args) {
		if (logFactory != null) {
			try {
				return logFactory.getClass().getMethod(methodName, args);
			} catch (NoSuchMethodException e) {
				logger.warn("Error reflecting ProgressLoggerFactory#" + methodName, e);
			}
		}

		return null;
	}

	private Object invoke(Method method, Object... args) {
		if (logFactory != null) {
			try {
				method.setAccessible(true);
				return method.invoke(logFactory, args);
			} catch (ReflectiveOperationException e) {
				logger.warn("Error reflecting ProgressLoggerFactory#" + method, e);
			}
		}

		return null;
	}

	/**
	 * Get a Progress logger from the Gradle internal API.
	 *
	 * @param project The project
	 * @param category The logger category
	 * @return In any case a progress logger
	 */
	public static ProgressLogger getProgressFactory(Project project, String category) {
		try {
			Method getServices = project.getClass().getMethod("getServices");
			Object serviceFactory = getServices.invoke(project);
			Method get = serviceFactory.getClass().getMethod("get", Class.class);
			Object progressLoggerFactory = get.invoke(serviceFactory, getFactoryClass());
			Method newOperation = progressLoggerFactory.getClass().getMethod("newOperation", String.class);
			return new ProgressLogger(project.getLogger(), newOperation.invoke(progressLoggerFactory, category));
		} catch (Exception e) {
			project.getLogger().error("Unable to get progress logger. Download progress will not be displayed.");
			return new ProgressLogger(project.getLogger(), null);
		}
	}

	/**
	 * Returns the description of the operation.
	 *
	 * @return the description, must not be empty.
	 */
	public String getDescription() {
		return (String) invoke(getDescription);
	}

	/**
	 * Sets the description of the operation. This should be a full, stand-alone description of the operation.
	 *
	 * <p>This must be called before {@link #started()}
	 *
	 * @param description The description.
	 */
	public ProgressLogger setDescription(String description) {
		invoke(setDescription, description);
		return this;
	}

	/**
	 * Convenience method that sets descriptions and logs started() event.
	 *
	 * @return this logger instance
	 */
	public ProgressLogger start(String description, String shortDescription) {
		invoke(start, description, shortDescription);
		return this;
	}

	/**
	 * Logs the start of the operation, with no initial status.
	 */
	public void started() {
		invoke(started);
	}

	/**
	 * Logs the start of the operation, with the given status.
	 *
	 * @param status The initial status message. Can be null or empty.
	 */
	public void started(String status) {
		invoke(startedArg, status);
	}

	/**
	 * Logs some progress, indicated by a new status.
	 *
	 * @param status The new status message. Can be null or empty.
	 */
	public void progress(String status) {
		invoke(progress, status);
	}

	/**
	 * Logs the completion of the operation, with no final status.
	 */
	public void completed() {
		invoke(completed);
	}

	/**
	 * Logs the completion of the operation, with a final status. This is generally logged along with the description.
	 *
	 * @param status The final status message. Can be null or empty.
	 * @param failed Whether the operation failed.
	 */
	public void completed(String status, boolean failed) {
		invoke(completedArg, status, failed);
	}
}
