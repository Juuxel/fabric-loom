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

package net.fabricmc.loom.task;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

final class DecompilerUtil {
    static FileSystem getJarFileSystem(File jar, boolean create) throws IOException {
        try {
            return FileSystems.newFileSystem(new URI("jar:" + jar.toURI()), ImmutableMap.of("create", String.valueOf(create)));
        } catch (URISyntaxException e) {
            throw new IOException("Could not create URI", e);
        }
    }

    static List<String> sortImports(String[] lines) {
        boolean foundImports = false;
        int importStart = -1; // inclusive
        int importEnd = -1;   // exclusive

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            if (line.startsWith("import ")) {
                if (!foundImports) {
                    foundImports = true;
                    importStart = i;
                }
            } else if (foundImports) {
                importEnd = i;
            }
        }

        if (!foundImports) {
            return ImmutableList.copyOf(lines);
        }

        String[] imports = new String[importEnd - importStart];
        System.arraycopy(lines, importStart, imports, 0, imports.length);
        Arrays.sort(imports);

        List<String> result = new ArrayList<>();

        for (int i = 0; i < importStart; i++) {
            result.add(lines[i]);
        }

        for (String line :imports){
            result.add(line);
        }

        for (int i = importEnd; i < lines.length; i++) {
            result.add(lines[i]);
        }

        return result;
    }
}
