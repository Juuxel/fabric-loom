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

import com.strobel.assembler.metadata.*;
import com.strobel.decompiler.DecompilationOptions;
import com.strobel.decompiler.DecompilerSettings;
import com.strobel.decompiler.PlainTextOutput;
import com.strobel.decompiler.languages.java.BraceStyle;
import com.strobel.decompiler.languages.java.JavaFormattingOptions;
import cuchaz.enigma.source.procyon.typeloader.NoRetryMetadataSystem;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarFile;

public class ProcyonTask extends AbstractDecompileTask {
    @TaskAction
    public void doTask() throws Throwable {
        try (FileSystem outputFs = DecompilerUtil.getJarFileSystem(getOutput(), true)) {
            JarFile inputJar = new JarFile(getInput());
            List<ITypeLoader> typeLoaders = new ArrayList<>();
            typeLoaders.add(new JarTypeLoader(inputJar));

            for (File library : getLibraries()) {
                typeLoaders.add(new JarTypeLoader(new JarFile(library)));
            }

            ITypeLoader typeLoader = new CompositeTypeLoader(typeLoaders.toArray(new ITypeLoader[0]));

            DecompilerSettings settings = DecompilerSettings.javaDefaults();
            settings.setForceExplicitImports(true);
            settings.setShowSyntheticMembers(true);
            settings.setTypeLoader(typeLoader);

            JavaFormattingOptions format = settings.getJavaFormattingOptions();
            format.ClassBraceStyle = BraceStyle.EndOfLine;
            format.InterfaceBraceStyle = BraceStyle.EndOfLine;
            format.EnumBraceStyle = BraceStyle.EndOfLine;

            MetadataSystem metadataSystem = new NoRetryMetadataSystem(typeLoader);
            DecompilationOptions options = new DecompilationOptions();
            options.setSettings(settings);

            try (FileSystem inputFs = DecompilerUtil.getJarFileSystem(getInput(), false)) {
                for (Path rootDirectory : inputFs.getRootDirectories()) {
                    Files.find(rootDirectory, Integer.MAX_VALUE, (path, attributes) -> attributes.isRegularFile()).forEach(it -> {
                        try {
                            if (it.toString().endsWith(".class")) {
                                Path relative = rootDirectory.relativize(it);
                                String relativePath = relative.toString();
                                String className = relativePath.substring(0, relativePath.length() - ".class".length());

                                Path target = outputFs.getPath(className + ".java");
                                Path parent = target.getParent();

                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }

                                TypeReference type = metadataSystem.lookupType(className);
                                TypeDefinition resolvedType = type.resolve();

                                try (Writer writer = Files.newBufferedWriter(target)) {
                                    PlainTextOutput output = new PlainTextOutput(writer);
                                    settings.getLanguage().decompileType(resolvedType, output, options);
                                }
                            } else {
                                Path relative = rootDirectory.relativize(it);
                                Path target = outputFs.getPath(relative.toString());
                                Path parent = target.getParent();

                                if (parent != null) {
                                    Files.createDirectories(parent);
                                }

                                Files.copy(it, target);
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                }
            }
        }
    }
}
