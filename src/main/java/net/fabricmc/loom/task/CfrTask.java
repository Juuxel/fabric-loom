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

import com.google.common.collect.ImmutableMap;
import org.benf.cfr.reader.api.CfrDriver;
import org.benf.cfr.reader.api.OutputSinkFactory;
import org.benf.cfr.reader.api.SinkReturns;
import org.gradle.api.tasks.TaskAction;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class CfrTask extends AbstractDecompileTask {
    @TaskAction
    public void doTask() throws Throwable {
        try (FileSystem outputFs = FileSystems.newFileSystem(new URI("jar:" + getOutput().toURI()), ImmutableMap.of("create", "true"))) {
            OutputSinkFactory sink = new OutputSinkFactory() {
                @Override
                public List<SinkClass> getSupportedSinks(SinkType sinkType, Collection<SinkClass> available) {
                    return Collections.singletonList(SinkClass.DECOMPILED);
                }

                @SuppressWarnings("unchecked")
                @Override
                public <T> Sink<T> getSink(SinkType sinkType, SinkClass sinkClass) {
                    if (sinkType == SinkType.JAVA && sinkClass == SinkClass.DECOMPILED) {
                        return (Sink<T>) new FsSink(outputFs);
                    }

                    return ignored -> {
                    };
                }
            };

            StringBuilder extraClasspath = new StringBuilder();
            for (File library : getLibraries()) {
                if (extraClasspath.length() != 0) {
                    extraClasspath.append(File.pathSeparator);
                }

                extraClasspath.append(library.getAbsolutePath());
            }

            CfrDriver driver = new CfrDriver.Builder()
                    .withOptions(ImmutableMap.of("extraclasspath", extraClasspath.toString()))
                    .withOutputSink(sink)
                    .build();

            driver.analyse(Collections.singletonList(getInput().getAbsolutePath()));
        }
    }

    private final class FsSink implements OutputSinkFactory.Sink<SinkReturns.Decompiled> {
        private final FileSystem fs;

        private FsSink(FileSystem fs) {
            this.fs = fs;
        }

        @Override
        public void write(SinkReturns.Decompiled sinkable) {
            Path path = fs.getPath(sinkable.getPackageName().replace(".", fs.getSeparator()), sinkable.getClassName() + ".java");

            try {
                Files.createDirectories(path.getParent());
                Files.write(path, Collections.singleton(sinkable.getJava()));
            } catch (IOException e) {
                getProject().getLogger().error("Could not save class " + path, e);
            }
        }
    }
}
