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

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.json.JSONExporter;

import com.google.gson.JsonObject;
import com.ibm.wala.cast.ir.ssa.AstIRFactory;
import com.ibm.wala.cast.java.client.impl.ZeroOneCFABuilderFactory;
import com.ibm.wala.cast.java.ipa.callgraph.JavaSourceAnalysisScope;
import com.ibm.wala.cast.java.translator.jdt.ecj.ECJClassLoaderFactory;
import com.ibm.wala.classLoader.CallSiteReference;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.classLoader.Module;
import com.ibm.wala.classLoader.ModuleEntry;
import com.ibm.wala.core.util.strings.Atom;
import com.ibm.wala.ipa.callgraph.AnalysisCacheImpl;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.AnalysisOptions.ReflectionOptions;
import com.ibm.wala.ipa.callgraph.AnalysisScope;
import com.ibm.wala.ipa.callgraph.CallGraph;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.ClassHierarchyFactory;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.properties.WalaProperties;
import com.ibm.wala.ssa.SymbolTable;
import com.ibm.wala.types.ClassLoaderReference;

public final class CallGraphBuilder {

    private final AnalysisScope scope;
    private final Set<ModuleEntry> classes = new LinkedHashSet<>();
    private final Module module = new Module() {
        @Override
        public Iterator<? extends ModuleEntry> getEntries() {
            return classes.iterator();
        }
    };
    private final Set<File> tempClassFiles = new LinkedHashSet<>();

    @FunctionalInterface
    interface InputStreamFactory {
        public InputStream createInputStream();
    }

    static final class ClassNode implements Serializable {

        private final String className;
        private final boolean isPrivate;
        private final int fields;
        private final int staticFields;
        private final int instanceFields;
        private final int methods;
        private final int staticMethods;
        private final int instanceMethods;
        private static final long serialVersionUID = 8705110576322386742L;
        
        public ClassNode(IClass clazz) {
            this.className = clazz.getName().toString().substring(1).replace("/", ".");
            this.isPrivate = clazz.isPrivate();
            this.fields = size(clazz.getAllFields());
            this.staticFields = size(clazz.getAllStaticFields());
            this.instanceFields = size(clazz.getAllInstanceFields());
            final Collection<? extends IMethod> methods = clazz.getDeclaredMethods();
            this.methods = size(methods);
            this.staticMethods = (methods != null) ? (int) methods.stream().filter(m -> m.isStatic()).count() : 0;
            this.instanceMethods = this.methods - this.staticMethods;
        }

        public String getClassName() {
            return className;
        }
        
        public boolean isPrivate() {
            return isPrivate;
        }
        
        public int getFieldCount() {
            return fields;
        }
        
        public int getStaticFieldCount() {
            return staticFields;
        }
        
        public int getInstanceFieldCount() {
            return instanceFields;
        }
        
        public int getMethodCount() {
            return methods;
        }
        
        public int getStaticMethodCount() {
            return staticMethods;
        }
        
        public int getInstanceMethodCount() {
            return instanceMethods;
        }

        @Override
        public String toString() {
            return getClassName();
        }

        @Override
        public int hashCode() {
            return getClassName().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof ClassNode) && (toString().equals(o.toString()));
        }
        
        private static int size(Collection<?> c) {
            return (c != null) ? c.size() : 0;
        }
    }

    static final class CallGraphEdge implements Serializable {

        private final Atom source;
        private final Atom destination;
        private int weight;
        private static final long serialVersionUID = -8284030936836318929L;

        public CallGraphEdge(Atom source, Atom destination) {
            this(source, destination, 1);
        }
        
        private CallGraphEdge(Atom source, Atom destination, int weight) {
            this.source = source;
            this.destination = destination;
            this.weight = weight;
        }
        
        public int getWeight() {
            return weight;
        }
        
        public void incrementWeight() {
            ++weight;
        }

        @Override
        public String toString() {
            final JsonObject o = new JsonObject();
            o.addProperty("source", this.source.toString());
            o.addProperty("destination", this.destination.toString());
            return o.toString();
        }

        @Override
        public int hashCode() {
            return toString().hashCode();
        }

        @Override
        public boolean equals(Object o) {
            return (o instanceof CallGraphEdge) && (toString().equals(o.toString()));
        }
    }

    public CallGraphBuilder() throws IOException {
        scope = createScope();
    }

    public boolean write(File savePath) throws IOException {
        // Make class hierarchy
        try {
            if (classes.size() > 0) {
                // Create class hierarchy
                IClassHierarchy cha = ClassHierarchyFactory.make(scope, new ECJClassLoaderFactory(scope.getExclusions()));

                Collection<Entrypoint> entryPoints = getEntryPoints(cha);
                if (entryPoints.size() > 0) {
                    // Initialize analysis options
                    AnalysisOptions options = new AnalysisOptions();
                    options.setEntrypoints(entryPoints);
                    options.getSSAOptions().setDefaultValues(SymbolTable::getDefaultValue);
                    options.setReflectionOptions(ReflectionOptions.NONE);
                    IAnalysisCacheView cache = new AnalysisCacheImpl(AstIRFactory.makeDefaultFactory(), options.getSSAOptions());

                    // Build the call graph
                    com.ibm.wala.ipa.callgraph.CallGraphBuilder<?> builder = new ZeroOneCFABuilderFactory().make(options, cache, cha);
                    CallGraph callGraph = builder.makeCallGraph(options, null);

                    // Save the call graph as JSON
                    callgraph2JSON(callGraph, savePath);
                    return true;
                }
            }
        }
        catch (Throwable t) {
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException(t);
        }
        return false;
    }

    public void clean() {
        // Delete temporary class files when the JVM exits.
        tempClassFiles.forEach(f -> {
            f.deleteOnExit();
        });
        tempClassFiles.clear();
    }

    private Graph<ClassNode, CallGraphEdge> getDirectedGraph(CallGraph callGraph) {
        final Graph<ClassNode, CallGraphEdge> graph = new DefaultDirectedGraph<>(CallGraphEdge.class);
        callGraph.getEntrypointNodes().forEach(entrypointNode -> {
            final IMethod entryMethod = entrypointNode.getMethod();
            // Get call statements that may execute in a given method
            final Iterable<CallSiteReference> outGoingCalls = () -> entrypointNode.iterateCallSites();
            outGoingCalls.forEach(callSiteReference -> {
                callGraph.getPossibleTargets(entrypointNode, callSiteReference).forEach(callTarget -> {
                    if (isApplicationClass(callTarget.getMethod().getDeclaringClass())) {
                        final ClassNode source = new ClassNode(entryMethod.getDeclaringClass());
                        final ClassNode target = new ClassNode(callTarget.getMethod().getDeclaringClass());
                        if (!source.equals(target)) {
                            graph.addVertex(source);
                            graph.addVertex(target);
                            CallGraphEdge edge = graph.getEdge(source, target);
                            if (edge == null) {
                                graph.addEdge(
                                        source,
                                        target,
                                        new CallGraphEdge(entryMethod.getName(), callTarget.getMethod().getName()));
                            }
                            else {
                                edge.incrementWeight();
                            }
                        }
                    }
                });
            });
        });
        return graph;
    }

    private void callgraph2JSON(CallGraph callGraph, File savePath) {
        final Graph<ClassNode, CallGraphEdge> graph = getDirectedGraph(callGraph);
        final JSONExporter<ClassNode, CallGraphEdge> exporter = new JSONExporter<>(v -> v.getClassName());
        exporter.setVertexAttributeProvider((v) -> {
            final Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v.toString()));
            map.put("is_class_private", DefaultAttribute.createAttribute(v.isPrivate()));
            map.put("num_total_fields", DefaultAttribute.createAttribute(v.getFieldCount()));
            map.put("num_static_fields", DefaultAttribute.createAttribute(v.getStaticFieldCount()));
            map.put("num_instance_fields", DefaultAttribute.createAttribute(v.getInstanceFieldCount()));
            map.put("num_total_methods", DefaultAttribute.createAttribute(v.getMethodCount()));
            map.put("num_static_methods", DefaultAttribute.createAttribute(v.getStaticMethodCount()));
            map.put("num_instance_methods", DefaultAttribute.createAttribute(v.getInstanceMethodCount()));
            return map;
        });
        exporter.setEdgeAttributeProvider((e) -> {
            return Collections.singletonMap("weight", DefaultAttribute.createAttribute(e.getWeight()));
        });
        // Export the graph to JSON
        exporter.exportGraph(graph, savePath);
    }

    public void addToScope(ClassProcessor cp, byte[] clazz) {
        final String binaryPath = cp.getBinaryPath();
        final String className = cp.getClassName();
        final File tempClassFile = createTemporaryFile(clazz);
        final InputStreamFactory isf;
        if (tempClassFile != null) {
            isf = () -> {
                try {
                    return new FileInputStream(tempClassFile);
                }
                catch (IOException e) {
                    // TODO: Report an error using logging.
                    return new ByteArrayInputStream(new byte[0]);
                }
            };
        }
        else {
            isf = () -> new ByteArrayInputStream(clazz);
        }
        classes.add(new ModuleEntry() {
            @Override
            public boolean isSourceFile() {
                return false;
            }
            @Override
            public boolean isModuleFile() {
                return false;
            }
            @Override
            public boolean isClassFile() {
                return true;
            }
            @Override
            public String getName() {
                return binaryPath;
            }
            @Override
            public InputStream getInputStream() {
                return isf.createInputStream();
            }
            @Override
            public Module getContainer() {
                return module;
            }
            @Override
            public String getClassName() {
                return className;
            }
            @Override
            public Module asModule() {
                return null;
            }
        });
    }

    private File createTemporaryFile(byte[] clazz) {
        try {
            final File f = File.createTempFile("minerva", null);
            tempClassFiles.add(f);
            final FileOutputStream fos = new FileOutputStream(f);
            fos.write(clazz);
            fos.close();
            return f;
        }
        catch (Exception e) {
            // TODO: Report warning.
        }
        return null;
    }

    private AnalysisScope createScope() throws IOException {
        AnalysisScope scope = new JavaSourceAnalysisScope();
        try {
            // Add standard libraries to scope.
            final String[] stdlibs = WalaProperties.getJ2SEJarFiles();
            for (String stdlib : stdlibs) {
                scope.addToScope(ClassLoaderReference.Primordial, new JarFile(stdlib));
            }
            // Add application module to scope.
            scope.addToScope(ClassLoaderReference.Application, module);
        }
        catch (Throwable t) {
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException(t);
        }
        return scope;
    }

    private boolean isApplicationClass(IClass _class) {
        return _class.getClassLoader().getReference().equals(ClassLoaderReference.Application);
    }

    private Collection<Entrypoint> getEntryPoints(IClassHierarchy cha) {
        final Collection<Entrypoint> entrypoints = new ArrayList<>();
        cha.forEach(c -> {
            if (isApplicationClass(c)) {
                c.getDeclaredMethods().forEach(method -> {
                    if (method.isPublic()) {
                        entrypoints.add(new DefaultEntrypoint(method, cha));
                    }
                });
            }
        });
        return entrypoints;
    }
}
