package net.fabricmc.loom.util.gradle;

import java.io.File;
import java.util.NoSuchElementException;
import java.util.Set;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;

import net.fabricmc.loom.configuration.DependencyProvider;

/**
 * @author Juuz
 */
public class DependencyDownloader {
	public static DownloadResult download(Project project, String dependencyNotation) {
		Dependency dependency = project.getDependencies().create(dependencyNotation);
		Configuration config = project.getConfigurations().detachedConfiguration(dependency);
		Set<File> files = DependencyProvider.DependencyInfo.create(project, dependency, config).resolve();
		return new DownloadResult(files);
	}

	public static final class DownloadResult {
		private final Set<File> files;

		public DownloadResult(Set<File> files) {
			this.files = files;
		}

		public Set<File> getFiles() {
			return files;
		}

		public File getSingleFile() {
			if (files.size() != 1) {
				throw new NoSuchElementException("Requested a single file, found: " + files.size());
			}

			return files.iterator().next();
		}
	}
}
