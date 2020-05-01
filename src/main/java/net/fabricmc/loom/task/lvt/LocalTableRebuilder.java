/*
 * This file is part of Mixin, licensed under the MIT License (MIT).
 *
 * Copyright (c) SpongePowered <https://www.spongepowered.org>
 * Copyright (c) contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package net.fabricmc.loom.task.lvt;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.analysis.Analyzer;
import org.objectweb.asm.tree.analysis.AnalyzerException;
import org.objectweb.asm.tree.analysis.BasicValue;
import org.objectweb.asm.tree.analysis.Frame;

public class LocalTableRebuilder {
	public static Map<MethodNode, List<LocalVariableNode>> generateLocalVariableTable(ClassNode classNode) {
		List<Type> interfaces = null;
		if (classNode.interfaces != null) {
			interfaces = new ArrayList<>();
			for (String iface : classNode.interfaces) {
				interfaces.add(Type.getObjectType(iface));
			}
		}

		Type objectType = null;
		if (classNode.superName != null) {
			objectType = Type.getObjectType(classNode.superName);
		}

		Verifier verifier = new Verifier(Opcodes.ASM7, Type.getObjectType(classNode.name), objectType, interfaces, false);
		return classNode.methods.stream().collect(Collectors.toMap(Function.identity(), method -> generateLocalVariableTable(verifier, method)));
	}

	private static List<LocalVariableNode> generateLocalVariableTable(Verifier verifier, MethodNode method) {
        // Use Analyzer to generate the bytecode frames
        Analyzer<BasicValue> analyzer = new Analyzer<>(verifier);
        try {
            analyzer.analyze(verifier.currentClass.getInternalName(), method);
        } catch (AnalyzerException ex) {
        	System.err.println("Error analysing in " + verifier.currentClass.getInternalName() + '#' + method.name + method.desc);
            ex.printStackTrace();
            return Collections.emptyList();
        }

        // Get frames from the Analyzer
        Frame<BasicValue>[] frames = analyzer.getFrames();

        // Record the original size of the method
        int methodSize = method.instructions.size();

        // List of LocalVariableNodes to return
        List<LocalVariableNode> localVariables = new ArrayList<>();

        LocalVariableNode[] localNodes = new LocalVariableNode[method.maxLocals]; // LocalVariableNodes for current frame
        BasicValue[] locals = new BasicValue[method.maxLocals]; // locals in previous frame, used to work out what changes between frames
        LabelNode[] labels = new LabelNode[methodSize]; // Labels to add to the method, for the markers
        String[] lastKnownType = new String[method.maxLocals];

        // Traverse the frames and work out when locals begin and end
        for (int i = 0; i < methodSize; i++) {
            Frame<BasicValue> f = frames[i];
            if (f == null) {
                continue;
            }
            LabelNode label = null;

            for (int j = 0; j < f.getLocals(); j++) {
                BasicValue local = f.getLocal(j);
                if (local == null && locals[j] == null) {
                    continue;
                }
                if (local != null && local.equals(locals[j])) {
                    continue;
                }

                if (label == null) {
                    AbstractInsnNode existingLabel = method.instructions.get(i);
                    if (existingLabel instanceof LabelNode) {
                        label = (LabelNode) existingLabel;
                    } else {
                        labels[i] = label = new LabelNode();
                    }
                }

                if (local == null && locals[j] != null) {
                    localVariables.add(localNodes[j]);
                    localNodes[j].end = label;
                    localNodes[j] = null;
                } else if (local != null) {
                    if (locals[j] != null) {
                        localVariables.add(localNodes[j]);
                        localNodes[j].end = label;
                        localNodes[j] = null;
                    }

                    String desc = local.getType() != null ? local.getType().getDescriptor() : lastKnownType[j];
                    localNodes[j] = new LocalVariableNode("var" + j, desc, null, label, null, j);
                    if (desc != null) {
                        lastKnownType[j] = desc;
                    }
                }

                locals[j] = local;
            }
        }

        // Reached the end of the method so flush all current locals and mark the end
        LabelNode label = null;
        for (int k = 0; k < localNodes.length; k++) {
            if (localNodes[k] != null) {
                if (label == null) {
                    label = new LabelNode();
                    method.instructions.add(label);
                }

                localNodes[k].end = label;
                localVariables.add(localNodes[k]);
            }
        }

        // Insert generated labels into the method body
        for (int n = methodSize - 1; n >= 0; n--) {
            if (labels[n] != null) {
                method.instructions.insert(method.instructions.get(n), labels[n]);
            }
        }

        return localVariables;
    }
}