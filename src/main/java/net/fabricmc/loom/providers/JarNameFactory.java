/*
 * Copyright 2020 Chocohead
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package net.fabricmc.loom.providers;

/** We're one strategy away from being proper enterprise Java code */
public enum JarNameFactory {
	CLIENT {
		@Override
		public String getJarName(String version) {
			return "minecraft-" + version + "-client.jar";
		}
	},
	CLIENT_INTERMEDIARY {
		@Override
		public String getJarName(String version) {
			return "minecraft-" + version + "-client-intermediary.jar";
		}
	},
	SERVER {
		@Override
		public String getJarName(String version) {
			return "minecraft-" + version + "-server.jar";
		}
	},
	SERVER_INTERMEDIARY {
		@Override
		public String getJarName(String version) {
			return "minecraft-" + version + "-server-intermediary.jar";
		}
	},
	MERGED {
		@Override
		public String getJarName(String version) {
			return "minecraft-" + version + "-merged.jar";
		}
	},
	MERGED_INTERMEDIARY {
		@Override
		public String getJarName(String version) {
			return "minecraft-" + version + "-intermediary.jar"; //FIXME: Attach the mapping name to the end?
		}
	};

	public abstract String getJarName(String version);
}