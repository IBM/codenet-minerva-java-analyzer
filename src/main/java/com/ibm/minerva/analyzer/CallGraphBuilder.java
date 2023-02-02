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
        private static final long serialVersionUID = 8705110576322386742L;

        public ClassNode(String name) {
            this.className = name.substring(1).replace("/", ".");
        }

        public String getClassName() {
            return className;
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
    }

    static final class CallGraphEdge implements Serializable {

        private final Atom from;
        private final Atom to;
        private static final long serialVersionUID = -8284030936836318929L;

        public CallGraphEdge(Atom from, Atom to) {
            this.from = from;
            this.to = to;
        }

        @Override
        public String toString() {
            final JsonObject o = new JsonObject();
            o.addProperty("from", this.from.toString());
            o.addProperty("to", this.to.toString());
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

    public void write(File savePath) throws IOException {
        // Make class hierarchy
        try {
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
            }
        }
        catch (Throwable t) {
            if (t instanceof IOException) {
                throw (IOException) t;
            }
            throw new IOException(t);
        }
    }

    public void clean() {
        // Delete temporary class files with the JVM exits.
        tempClassFiles.forEach(f -> {
            f.deleteOnExit();
        });
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
                        final ClassNode source = new ClassNode(entryMethod.getDeclaringClass().getName().toString());
                        final ClassNode target = new ClassNode(callTarget.getMethod().getDeclaringClass().getName().toString());
                        if (!source.equals(target)) {
                            graph.addVertex(source);
                            graph.addVertex(target);
                            graph.addEdge(
                                    source,
                                    target,
                                    new CallGraphEdge(entryMethod.getName(), callTarget.getMethod().getName()));
                        }
                    }
                });
            });
        });
        return graph;
    }

    public void callgraph2JSON(CallGraph callGraph, File savePath) {
        final Graph<ClassNode, CallGraphEdge> graph = getDirectedGraph(callGraph);
        final JSONExporter<ClassNode, CallGraphEdge> exporter = new JSONExporter<>(v -> v.getClassName());
        exporter.setVertexAttributeProvider((v) -> {
            final Map<String, Attribute> map = new LinkedHashMap<>();
            map.put("label", DefaultAttribute.createAttribute(v.toString()));
            return map;
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
            final FileOutputStream fos = new FileOutputStream(f);
            fos.write(clazz);
            fos.close();
            tempClassFiles.add(f);
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
