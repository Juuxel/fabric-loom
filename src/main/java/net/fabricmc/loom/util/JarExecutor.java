package net.fabricmc.loom.util;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Manifest;

import com.google.common.collect.ImmutableMap;

public final class JarExecutor {
	public static void executeJar(Path jar, String... args) throws Exception {
		// Use the JDK class loader as the parent. We don't need Gradle here.
		ClassLoader jdkClassLoader = Object.class.getClassLoader();
		URLClassLoader classLoader = new URLClassLoader(new URL[] { jar.toUri().toURL() }, jdkClassLoader);

		// Read the manifest
		Manifest manifest = new Manifest();

		try (FileSystem fs = FileSystems.newFileSystem(URI.create("jar:" + jar.toUri()), ImmutableMap.of("create", false));
				InputStream in = Files.newInputStream(fs.getPath("META-INF", "MANIFEST.MF"))) {
			manifest.read(in);
		}

		String mainClassName = manifest.getMainAttributes().getValue("Main-Class");

		if (mainClassName == null) {
			throw new IllegalStateException("Could not run jar '" + jar + "': it has no main class!");
		}

		Class<?> mainClass = classLoader.loadClass(mainClassName);
		Method main = mainClass.getMethod("main", String[].class);
		main.invoke(null, (Object) args);
	}
}
