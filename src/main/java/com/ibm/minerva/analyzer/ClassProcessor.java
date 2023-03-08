/******************************************************************************* 
 * Copyright (c) contributors to the Minerva for Modernization project.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     IBM Corporation - initial implementation
 *******************************************************************************/

package com.ibm.minerva.analyzer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtField;
import javassist.Modifier;

public final class ClassProcessor {

    private static final String MODULE_INFO = "module-info";
    private static final String PACKAGE_INFO = "package-info";

    private final CtClass ctClass;
    private volatile List<FieldProcessor> fieldProcessors;
    private volatile List<MethodProcessor> methodProcessors;

    public ClassProcessor(CtClass ctClass) {
        this.ctClass = ctClass;
    }

    public CtClass getCtClass() {
        return ctClass;
    }

    public boolean isNestedClass() {
        return ctClass.getName().indexOf('$') != -1;
    }

    public boolean isStaticClass() {
        return Modifier.isStatic(ctClass.getModifiers());
    }

    public boolean isEntityClass() {
        return ctClass.hasAnnotation("javax.persistence.Entity");
    }

    public Set<String> getServerTypeAnnotations() {
        final Set<String> annotations = new LinkedHashSet<>();
        if (ctClass.hasAnnotation("javax.websocket.server.ServerEndpoint")) {
            annotations.add("@ServerEndpoint(\"\")");
        }
        if (ctClass.hasAnnotation("javax.servlet.annotation.WebServlet")) {
            annotations.add("@WebServlet(\"\")");
        }
        if (ctClass.hasAnnotation("javax.ws.rs.Path")) {
            annotations.add("@Path(\"\")");
        }
        if (ctClass.hasAnnotation("javax.enterprise.context.RequestScoped")) {
            annotations.add("@RequestScoped");
        }
        if (ctClass.hasAnnotation("javax.enterprise.context.SessionScoped")) {
            annotations.add("@SessionScoped");
        }
        return annotations;
    }

    public List<FieldProcessor> getFields() {
        if (fieldProcessors == null) {
            final List<FieldProcessor> fps = new ArrayList<>();
            final CtField[] fields = ctClass.getDeclaredFields();
            if (fields != null) {
                // Filter out fields with '$' in their name. These are likely generated
                // fields and not declared in the application source code.
                Arrays.stream(fields).filter(x -> !x.getName().contains("$")).forEach(x -> {
                    fps.add(new FieldProcessor(this, x));
                });
            }
            fieldProcessors = Collections.unmodifiableList(fps);
        }
        return fieldProcessors;
    }

    public List<MethodProcessor> getMethods() {
        if (methodProcessors == null) {
            final List<MethodProcessor> mps = new ArrayList<>();
            final CtBehavior[] methods = ctClass.getDeclaredBehaviors();
            if (methods != null) {
                // Filter out non-constructor methods with '$' in their name. These are 
                // likely generated methods and not declared in the application source code.
                Arrays.stream(methods).filter(x -> x.getMethodInfo().isConstructor() || 
                        (!x.getName().contains("$") && x.getMethodInfo().isMethod())).forEach(x -> {
                            mps.add(new MethodProcessor(this, x));
                        });
            }
            methodProcessors = Collections.unmodifiableList(mps);
        }
        return methodProcessors;
    }

    public String getClassName() {
        return ctClass.getName().replace('$', '.');
    }

    public String toFQCN() {
        return getCtClass().getName().replace("$", ".$");
    }

    public String getSuperClassName() {
        final String superClass = ctClass.getClassFile().getSuperclass();
        if (superClass != null) {
            return superClass.replace('$', '.');
        }
        return null;
    }

    public String[] getInterfaces() {
        // Javassist returns a read-only array here. We need to
        // copy the values into a new array instead of mutating it in place.
        String[] interfaces = ctClass.getClassFile().getInterfaces();
        if (interfaces != null && interfaces.length > 0) {
            final String[] temp = new String[interfaces.length];
            for (int i = 0; i < interfaces.length; ++i) {
                String _interface = interfaces[i];
                if (_interface != null) {
                    _interface = _interface.replace('$', '.');
                }
                temp[i] = _interface;
            }
            interfaces = temp;
        }
        return interfaces;
    }

    public int getModifiers() {
        return ctClass.getModifiers();
    }

    public String getPackageName() {
        return ctClass.getPackageName();
    }

    public String getSimpleName() {
        return ctClass.getSimpleName();
    }

    public String getLocalName() {
        final String simpleName = getSimpleName();
        final int index = simpleName.lastIndexOf('$');
        if (index >= 0) {
            return simpleName.substring(index + 1);
        }
        return simpleName;
    }

    public boolean isStandardNamedClass(final boolean allowAnyLegalClasses) {
        if (!allowAnyLegalClasses) {
            // Filter out interfaces, enums and annotations.
            final int mod = ctClass.getModifiers();
            if (Modifier.isInterface(mod) || Modifier.isEnum(mod) || Modifier.isAnnotation(mod)) {
                return false;
            }
        }
        String name = getSimpleName();
        // Filter out module and package descriptors.
        if (MODULE_INFO.equals(name) || PACKAGE_INFO.equals(name)) {
            return false;
        }
        if (!allowAnyLegalClasses) {
            // Filter out local and anonymous classes.
            final int index = name.lastIndexOf('$');
            if (index >= 0) {
                name = name.substring(index+1);
                if (name.length() > 0) {
                    final char c = name.charAt(0);
                    if (c >= '0' && c <= '9') {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public String getSourcePath() {
        String name = getCtClass().getName();
        // Extract top-level enclosing class name if
        // this is an inner, local or anonymous class.
        final int index = name.indexOf('$');
        if (index >= 0) {
            name = name.substring(0, index);
        }
        return "project/src/main/java/" + name.replace('.', '/') + ".java";
    }

    public String getBinaryPath() {
        String name = getCtClass().getName();
        return "project/src/main/java/" + name.replace('.', '/') + ".class";
    }
}
