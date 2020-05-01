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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;

import org.zeroturnaround.zip.ZipUtil;

/**
 * Information about a class, used as a way of keeping track of class hierarchy
 * information needed to support more complex mixin behaviour such as detached
 * superclass and mixin inheritance.
 */
class ClassInfo {
	/**
     * When using {@link ClassInfo#forType}, determines whether an array type
     * should be returned as declared (eg. as <tt>Object</tt>) or whether the
     * element type should be returned instead.
     */
    public static enum TypeLookup {
        /**
         * Return the type as declared in the descriptor. This means that array
         * types will be treated <tt>Object</tt> for the purposes of type
         * hierarchy lookups returning the correct member methods.
         */
        DECLARED_TYPE,

        /**
         * Legacy behaviour. A lookup by type will return the element type.
         */
        ELEMENT_TYPE
    }

    private static final String JAVA_LANG_OBJECT = "java/lang/Object";

    /**
     * Loading and parsing classes is expensive, so keep a cache of all the
     * information we generate
     */
    private static final Map<String, ClassInfo> cache = new HashMap<>();
    static final Set<File> EXTRA_LOOKUPS = new HashSet<>();

    private static final ClassInfo OBJECT = new ClassInfo();

    static {
        ClassInfo.cache.put(ClassInfo.JAVA_LANG_OBJECT, ClassInfo.OBJECT);
    }

    public final boolean isObject;
    /**
     * Class name (binary name)
     */
    private final String name;

    /**
     * Class superclass name (binary name)
     */
    private final String superName;

    /**
     * Interfaces
     */
    private final Set<String> interfaces;

    /**
     * True if this is an interface
     */
    private final boolean isInterface;

    /**
     * Superclass reference, not initialised until required
     */
    private ClassInfo superClass;

    /**
     * Private constructor used to initialise the ClassInfo for {@link Object}
     */
    private ClassInfo() {
    	isObject = true;
        this.name = ClassInfo.JAVA_LANG_OBJECT;
        this.superName = null;
        this.isInterface = false;
        this.interfaces = Collections.<String>emptySet();
    }

    /**
     * Initialise a ClassInfo from the supplied {@link ClassNode}
     *
     * @param classNode Class node to inspect
     */
    private ClassInfo(ClassNode classNode) {
    	isObject = false;
    	this.name = classNode.name;
        this.superName = classNode.superName != null ? classNode.superName : ClassInfo.JAVA_LANG_OBJECT;
        this.isInterface = (classNode.access & Opcodes.ACC_INTERFACE) != 0;
        this.interfaces = new HashSet<>(classNode.interfaces);
    }

    /**
     * Get whether this is an interface or not
     */
    public boolean isInterface() {
        return this.isInterface;
    }

    /**
     * Returns the answer to life, the universe and everything
     */
    public Set<String> getInterfaces() {
        return Collections.<String>unmodifiableSet(this.interfaces);
    }

    /**
     * Get the class name (binary name)
     */
    public String getName() {
        return this.name;
    }

    /**
     * Get the superclass info, can return null if the superclass cannot be
     * resolved
     */
    public ClassInfo getSuperClass() {
        if (this.superClass == null && this.superName != null) {
            this.superClass = ClassInfo.forName(this.superName);
        }

        return this.superClass;
    }

    /**
     * Test whether this class has the specified superclass in its hierarchy
     *
     * @param superClass Superclass to search for in the hierarchy
     * @return true if the specified class appears in the class's hierarchy
     *      anywhere
     */
    public boolean hasSuperClass(ClassInfo superClass) {
    	return hasSuperClass(superClass, false);
    }

    /**
     * Test whether this class has the specified superclass in its hierarchy
     *
     * @param superClass Superclass to search for in the hierarchy
     * @param includeInterfaces True to include interfaces in the lookup
     * @return true if the specified class appears in the class's hierarchy
     *      anywhere
     */
    public boolean hasSuperClass(ClassInfo superClass, boolean includeInterfaces) {
        if (ClassInfo.OBJECT == superClass) {
            return true;
        }

        return this.findSuperClass(superClass.name, includeInterfaces, new HashSet<String>()) != null;
    }

    private ClassInfo findSuperClass(String superClass, boolean includeInterfaces, Set<String> traversed) {
        ClassInfo superTarget = this.getSuperClass();
        if (superTarget != null) {
        	if (superClass.equals(superTarget.getName())) {
                return superTarget;
            }

            ClassInfo found = superTarget.findSuperClass(superClass, includeInterfaces, traversed);
            if (found != null) {
                return found;
            }
        }

        if (includeInterfaces) {
            ClassInfo iface = this.findInterface(superClass);
            if (iface != null) {
                return iface;
            }
        }

        return null;
    }

    private ClassInfo findInterface(String superClass) {
        for (String ifaceName : this.getInterfaces()) {
            ClassInfo iface = ClassInfo.forName(ifaceName);
            if (superClass.equals(ifaceName)) {
                return iface;
            }
            ClassInfo superIface = iface.findInterface(superClass);
            if (superIface != null) {
                return superIface;
            }
        }
        return null;
    }

    private static InputStream findExtraClass(String className) throws IOException {
    	for (File file : EXTRA_LOOKUPS) {
    		if (file.isDirectory()) {
    			file = new File(file, className);
    			if (file.exists()) return new FileInputStream(file);
    		} else {
    			byte[] entry = ZipUtil.unpackEntry(file, className);
    			if (entry != null) return new ByteArrayInputStream(entry);
    		}
    	}

    	return null;
    }

    private static InputStream findClass(String className) throws IOException {
    	InputStream stream = ClassInfo.class.getResourceAsStream('/' + className);
    	if (stream != null) return stream;

    	stream = findExtraClass(className);
    	if (stream != null) return stream;

    	return null;
    }

    private static ClassNode getClassNode(String className) throws IOException {
    	try (InputStream in = findClass(className.replace('.', '/') + ".class")) {
			if (in == null) return null;

			ClassNode node = new ClassNode();
			new ClassReader(in).accept(node, 0);
			return node;
		}
    }

    /**
     * Return a ClassInfo for the specified class name, fetches the ClassInfo
     * from the cache where possible.
     *
     * @param className Binary name of the class to look up
     * @return ClassInfo for the specified class name or null if the specified
     *      name cannot be resolved for some reason
     */
    public static ClassInfo forName(String className) {
        className = className.replace('.', '/');

        ClassInfo info = ClassInfo.cache.get(className);
        if (info == null) {
            try {
                ClassNode classNode = getClassNode(className);
                if (classNode == null) throw new RuntimeException("Unable to find ClassInfo for " + className);
                info = new ClassInfo(classNode);
            } catch (IOException e) {
                throw new RuntimeException("Error getting ClassInfo for " + className, e);
            }

            // Put null in the cache if load failed
            ClassInfo.cache.put(className, info);
        }

        return info;
    }

    /**
     * Return a ClassInfo for the specified class type, fetches the ClassInfo
     * from the cache where possible and generates the class meta if not.
     *
     * @param type Type to look up
     * @param lookup Lookup type to use (literal/element)
     * @return ClassInfo for the supplied type or null if the supplied type
     *      cannot be found or is a primitive type
     */
    public static ClassInfo forType(Type type, TypeLookup lookup) {
        if (type.getSort() == Type.ARRAY) {
            if (lookup == TypeLookup.ELEMENT_TYPE) {
                return ClassInfo.forType(type.getElementType(), TypeLookup.ELEMENT_TYPE);
            }
            return ClassInfo.OBJECT;
        } else if (type.getSort() < Type.ARRAY) {
            return null;
        }
        return ClassInfo.forName(type.getClassName().replace('.', '/'));
    }

    /**
     * ASM logic applied via ClassInfo, returns first common superclass of
     * classes specified by <tt>type1</tt> and <tt>type2</tt>.
     *
     * @param type1 First type
     * @param type2 Second type
     * @return common superclass info
     */
    public static ClassInfo getCommonSuperClass(String type1, String type2) {
        if (type1 == null || type2 == null) {
            return ClassInfo.OBJECT;
        }
        return ClassInfo.getCommonSuperClass(ClassInfo.forName(type1), ClassInfo.forName(type2));
    }

    private static ClassInfo getCommonSuperClass(ClassInfo type1, ClassInfo type2) {
        if (type1.hasSuperClass(type2)) {
            return type2;
        } else if (type2.hasSuperClass(type1)) {
            return type1;
        } else if (type1.isInterface() || type2.isInterface()) {
            return ClassInfo.OBJECT;
        }

        do {
            type1 = type1.getSuperClass();
            if (type1 == null) {
                return ClassInfo.OBJECT;
            }
        } while (!type2.hasSuperClass(type1));

        return type1;
    }
}