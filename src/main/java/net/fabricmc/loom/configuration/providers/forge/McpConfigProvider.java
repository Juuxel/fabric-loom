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

package net.fabricmc.loom.configuration.providers.forge;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Consumer;

import com.google.gson.JsonObject;
import org.gradle.api.Project;

import net.fabricmc.loom.LoomGradlePlugin;
import net.fabricmc.loom.configuration.DependencyProvider;
import net.fabricmc.loom.util.Constants;
import net.fabricmc.loom.util.JarUtil;

public class McpConfigProvider extends DependencyProvider {
	private File mcp;
	private File config;
	private File srg;
	private String specialSourceDependency = Constants.Forge.SPECIAL_SOURCE_FALLBACK;

	public McpConfigProvider(Project project) {
		super(project);
	}

	@Override
	public void provide(DependencyInfo dependency, Consumer<Runnable> postPopulationScheduler) throws Exception {
		init(dependency.getDependency().getVersion());

		if (mcp.exists() && config.exists() && srg.exists() && !isRefreshDeps()) {
			return; // No work for us to do here
		}

		Path mcpZip = dependency.resolveFile().orElseThrow(() -> new RuntimeException("Could not resolve MCPConfig")).toPath();

		if (!mcp.exists() || isRefreshDeps()) {
			Files.copy(mcpZip, mcp.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		if (!config.exists() || isRefreshDeps()) {
			JarUtil.extractFile(mcp, "config.json", config);
		}

		String srgLocation = Constants.Forge.SRG_LOCATION_FALLBACK;

		try (Reader reader = new FileReader(config)) {
			JsonObject json = LoomGradlePlugin.GSON.fromJson(reader, JsonObject.class);

			if (!json.has("spec")) {
				throw new RuntimeException("MCPConfig JSON does not have key \"spec\"");
			} else if (json.getAsJsonPrimitive("spec").getAsInt() != 1) {
				throw new RuntimeException("MCPConfig JSON has unsupported spec: " + json.getAsJsonPrimitive("spec").getAsInt());
			}

			try {
				srgLocation = json.getAsJsonObject("data").getAsJsonPrimitive("mappings").getAsString();
				specialSourceDependency = json.getAsJsonObject("functions")
						.getAsJsonObject("rename")
						.getAsJsonPrimitive("version")
						.getAsString();
			} catch (Exception e) {
				getProject().getLogger().warn(":could not read MCPConfig", e);
			}
		}

		if (!srg.exists() || isRefreshDeps()) {
			JarUtil.extractFile(mcp, srgLocation, srg);
		}
	}

	private void init(String version) {
		mcp = new File(getExtension().getUserCache(), "mcp-" + version + ".zip");
		config = new File(getExtension().getUserCache(), "mcpconfig-" + version + ".json");
		srg = new File(getExtension().getUserCache(), "srg-" + version + ".tsrg");
	}

	public File getSrg() {
		return srg;
	}

	public String getSpecialSourceDependency() {
		return specialSourceDependency;
	}

	@Override
	public String getTargetConfig() {
		return Constants.Configurations.MCP_CONFIG;
	}
}
