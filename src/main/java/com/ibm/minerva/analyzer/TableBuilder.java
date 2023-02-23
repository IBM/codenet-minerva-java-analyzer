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

import static com.ibm.minerva.analyzer.MessageFormatter.formatMessage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javassist.Modifier;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SignatureAttribute.ObjectType;

public final class TableBuilder implements ApplicationProcessor {

    private static final Logger logger = LoggingUtil.getLogger(TableBuilder.class);

    private static final String SYM_TABLE_FILE_NAME = "symTable.json";
    private static final String REF_TABLE_FILE_NAME = "refTable.json";
    private static final String AGENT_CONFIG_FILE_NAME = "instrumenter-config.json";
    private static final String CALL_GRAPH_FILE_NAME = "callGraph.json";

    private final File tableDir;

    private final JsonObject symTable;
    private final JsonObject refTable;

    private final Set<String> fqcns = new LinkedHashSet<>();
    private final Set<String> duplicateClasses = new LinkedHashSet<>();
    private final Set<String> skippedClasses = new LinkedHashSet<>();
    private final Map<String,Set<String>> duplicateClassMap = new LinkedHashMap<>();

    private CallGraphBuilder callGraphBuilder;
    private Set<String> packages;
    private boolean isPackageIncludeList;
    private boolean useSystemOut;

    public TableBuilder(File tableDir) {
        this.tableDir = tableDir;
        this.symTable = createSymTable();
        this.refTable = createRefTable();
    }

    public void process(ClassProcessor cp, byte[] bytes) {
        final String fqcn = cp.toFQCN();
        if (isIncludedPackage(cp) && cp.isStandardNamedClass()) {
            if (!fqcns.contains(fqcn)) {
                logger.info(() -> formatMessage("AnalyzingClass", cp.getCtClass().getName()));
                addToRefTable(cp, addToSymTable(cp));
                fqcns.add(fqcn);
                if (callGraphBuilder != null) {
                    callGraphBuilder.addToScope(cp, bytes);
                }
            }
            // This reduces reporting of duplicate classes if more than 
            // two instances of the same class exist in the archive.
            else if (!duplicateClasses.contains(fqcn)) {
                logger.warning(() -> formatMessage("DuplicateClass", cp.getCtClass().getName()));
                duplicateClasses.add(fqcn);
            }
        }
        // This reduces reporting of skipped classes if more than one
        // instance of the skipped class exists in the archive.
        else if (!skippedClasses.contains(fqcn)) {
            logger.finest(() -> formatMessage("SkippedClass", cp.getCtClass().getName()));
            skippedClasses.add(fqcn);
        }
    }

    private boolean isIncludedPackage(ClassProcessor cp) {
        // Check if the class is a member of an include or exclude list if one was specified.
        if (packages != null) {
            final String packageName = cp.getPackageName();
            final boolean match;
            // Handle the special case of the default package (represented by null).
            if (packageName != null) {
                match = packages.stream().filter(s -> s != null).anyMatch(s -> packageName.startsWith(s + ".") || packageName.equals(s));
            }
            else {
                match = packages.contains(null);
            }
            return isPackageIncludeList ? match : !match;
        }
        return true;
    }

    public void setCallGraphBuilder(CallGraphBuilder callGraphBuilder) {
        this.callGraphBuilder = callGraphBuilder;
    }

    public void setPackageRestrictions(Set<String> packages, boolean isPackageIncludeList) {
        this.packages = packages;
        this.isPackageIncludeList = isPackageIncludeList;
    }

    public void setAgentOutputStream(boolean useSystemOut) {
        this.useSystemOut = useSystemOut;
    }

    public void write() throws IOException {
        resolveDuplicateClassMappings();
        if (tableDir.mkdirs()) {
            logger.info(() -> formatMessage("DirectoryCreated", tableDir));
        }
        Gson gson = new GsonBuilder().serializeNulls().create();
        // Write symTable.json.
        try (Writer symTableWriter = createWriter(SYM_TABLE_FILE_NAME)) {
            gson.toJson(symTable, symTableWriter);
        }
        // Write refTable.json.
        try (Writer refTableWriter = createWriter(REF_TABLE_FILE_NAME)) {
            gson.toJson(refTable, refTableWriter);
        }
        gson = new GsonBuilder().serializeNulls().setPrettyPrinting().create();
        // Write instrumenter-config.json.
        try (Writer agentConfigWriter = createWriter(AGENT_CONFIG_FILE_NAME)) {
            gson.toJson(createAgentConfiguration(), agentConfigWriter);
        }
        if (callGraphBuilder != null) {
            // Write callGraph.json.
            if (!writeCallGraph(CALL_GRAPH_FILE_NAME)) {
                // Write an empty JSON document if no call graph was generated.
                try (Writer agentConfigWriter = createWriter(CALL_GRAPH_FILE_NAME, false)) {
                    gson.toJson(new JsonObject(), agentConfigWriter);
                }
            }
        }
    }

    public void clean() {
        if (callGraphBuilder != null) {
            callGraphBuilder.clean();
        }
    }

    private Writer createWriter(String file) throws IOException {
        return createWriter(file, true);
    }
    
    private Writer createWriter(String file, boolean writeMessage) throws IOException {
        File f = new File(tableDir, file);
        if (writeMessage) {
            logger.info(() -> formatMessage("WritingFile", f));
        }
        OutputStream os = new FileOutputStream(f);
        return new OutputStreamWriter(os, "UTF-8");
    }

    private boolean writeCallGraph(String file) throws IOException {
        return callGraphBuilder.write(new File(tableDir, file));
    }

    private String addToSymTable(ClassProcessor cp) {
        // Compute the symbol table key for the class.
        String symTableKey = cp.getSimpleName().replace("$", "::");
        JsonObject o = createSymTableClassObject(cp);
        if (symTable.has(symTableKey)) {
            Set<String> duplicates = duplicateClassMap.get(symTableKey);
            if (duplicates == null) {
                duplicates = new LinkedHashSet<>();
                duplicates.add(symTableKey);
                duplicateClassMap.put(symTableKey, duplicates);
            }
            String dupeKey = symTableKey;
            int i = 1;
            do {
                dupeKey = symTableKey + " [Duplicate_#00" + (i++) + "]";
            }
            while (symTable.has(dupeKey));
            symTableKey = dupeKey;
            duplicates.add(symTableKey);
        }
        symTable.add(symTableKey, o);
        return symTableKey;
    }

    private void addToRefTable(ClassProcessor cp, String symTableKey) {
        final JsonObject files = refTable.get("Files").getAsJsonObject();
        final String sourcePath = cp.getSourcePath();
        final String fqcn = cp.toFQCN();
        final String localName = cp.getLocalName();

        JsonElement e = files.get(sourcePath);
        JsonObject fileObject = (e != null) ? e.getAsJsonObject() : null;
        if (fileObject == null) {
            fileObject = new JsonObject();
            files.add(sourcePath, fileObject);
            final String pkg = cp.getPackageName();
            if (pkg != null) {
                fileObject.addProperty("package", pkg);
            }
            fileObject.add("import", new JsonArray());
        }
        String localNameValue = symTableKey;
        final JsonElement localElement = fileObject.get(localName);
        if (localElement != null && localElement.isJsonPrimitive()) {
            localNameValue = localElement.getAsString() + "," + symTableKey;
        }
        fileObject.addProperty(localName, localNameValue);

        final JsonObject fqcns = refTable.get("FQCN").getAsJsonObject();
        e = fqcns.get(fqcn);
        JsonArray fqcnArray = (e != null) ? e.getAsJsonArray() : null;
        if (fqcnArray == null) {
            fqcnArray = new JsonArray();
            fqcns.add(fqcn, fqcnArray);
        }
        fqcnArray.add(symTableKey);

        final JsonObject jParser = refTable.get("Jparser").getAsJsonObject();
        e = jParser.get(sourcePath);
        fileObject = (e != null) ? e.getAsJsonObject() : null;
        if (fileObject == null) {
            fileObject = new JsonObject();
            jParser.add(sourcePath, fileObject);
        }
        fileObject.addProperty(cp.getClassName(), symTableKey);
    }

    private JsonObject createSymTable() {
        return new JsonObject();
    }

    private JsonObject createSymTableClassObject(ClassProcessor cp) {
        final JsonObject o = new JsonObject();
        o.add("classVar", createSymTableVariablesObject(cp));
        o.add("funcL", createSymTableMethodsObject(cp));
        o.addProperty("file", cp.getSourcePath());
        o.add("super", createSymTableSuperClassObject(cp));
        final JsonArray modifiers = createSymTableClassModifierArray(cp);
        o.add("modifier", modifiers);
        o.addProperty("header", createSymTableClassObjectHeader(cp, modifiers));
        o.addProperty("rawEnd", 0);
        o.add("Enum", new JsonArray());
        o.addProperty("FQCN", cp.toFQCN());
        o.addProperty("Jparser", cp.getClassName());
        o.add("funcSig", createSymTableFuncSigObject(o));
        return o;
    }

    private String createSymTableClassObjectHeader(ClassProcessor cp, JsonArray modifiers) {
        final StringBuilder sb = new StringBuilder();
        if (cp.isEntityClass()) {
            sb.append("@Entity ");
        }
        final Set<String> serverTypeAnnotations = cp.getServerTypeAnnotations();
        serverTypeAnnotations.forEach(x -> {
            sb.append(x);
            sb.append(" ");
        });
        modifiers.forEach(x -> {
            sb.append(x.getAsString());
            sb.append(" ");
        });
        sb.append("class ");
        sb.append(cp.getLocalName());
        final String superClass = cp.getSuperClassName();
        if (superClass != null && !superClass.equals("java.lang.Object")) {
            sb.append(" extends ");
            sb.append(superClass);
        }
        final String[] interfaces = cp.getInterfaces();
        if (interfaces != null && interfaces.length > 0) {
            sb.append(" implements");
            for (int i = 0; i < interfaces.length; ++i) {
                if (i == 0) {
                    sb.append(" ");
                }
                else {
                    sb.append(", ");
                }
                sb.append(interfaces[i]);
            }
        }
        sb.append(" {");
        return sb.toString();
    }

    private JsonObject createSymTableVariablesObject(ClassProcessor cp) {
        final JsonObject o = new JsonObject();
        cp.getFields().forEach(x -> {
            o.add(x.getFieldName(), createSymTableVariableObject(x));
        });
        return o;
    }

    private JsonObject createSymTableVariableObject(FieldProcessor fp) {
        final JsonObject o = new JsonObject();
        final SignatureAttribute.Type type = fp.getType();
        final String typeName = type != null ? type.toString() : "java.lang.Object";
        o.addProperty("Var", typeName);
        final JsonArray coreTypeArray = new JsonArray();
        coreTypeArray.add(typeName);
        o.add("CoreType", coreTypeArray);
        o.add("VarAST", createTypeAST(typeName));
        o.addProperty("Start", 0);
        o.addProperty("End", 0);
        final JsonArray modifiers = createSymTableFieldModifierArray(fp);
        o.add("modifier", modifiers);
        o.addProperty("RawStr", createSymTableVariableObjectHeader(fp, typeName, modifiers));
        return o;
    }

    private String createSymTableVariableObjectHeader(FieldProcessor fp, String typeName, JsonArray modifiers) {
        final StringBuilder sb = new StringBuilder();
        modifiers.forEach(x -> {
            sb.append(x.getAsString());
            sb.append(" ");
        });
        sb.append(typeName);
        sb.append(" ");
        sb.append(fp.getFieldName());
        sb.append(";");
        return sb.toString();
    }

    private JsonObject createSymTableMethodsObject(ClassProcessor cp) {
        final JsonObject o = new JsonObject();
        final AtomicInteger count = new AtomicInteger(1);
        final Set<String> overloadedMethods = computeOverloadedMethodSet(cp);
        cp.getMethods().forEach(x -> {
            String methodName = x.getMethodName();
            if (overloadedMethods.contains(methodName)) {
                methodName = generateOverloadedName(methodName, count);
            }
            o.add(methodName, createSymTableMethodObject(x));
        });
        return o;
    }

    private JsonObject createSymTableMethodObject(MethodProcessor mp) {
        final JsonObject o = new JsonObject();
        final SignatureAttribute.Type retType = mp.getReturnType();
        final String returnType;
        if (retType == null || "void".equals(retType.toString())) {
            o.addProperty("RetType", "void");
            o.add("RetCoreType", new JsonArray());
            o.addProperty("RetTypeAST", "");
            returnType = "void";
        }
        else {
            final String typeName = retType.toString();
            o.addProperty("RetType", typeName);
            final JsonArray coreTypeArray = new JsonArray();
            coreTypeArray.add(typeName);
            o.add("RetCoreType", coreTypeArray);
            o.add("RetTypeAST", createTypeAST(typeName));
            returnType = typeName;
        }
        o.add("Throws", createSymTableMethodThrowsArray(mp));
        o.add("Args", createSymTableMethodArgsObject(mp));
        o.add("Locals", new JsonObject());
        o.add("modifier", createSymTableMethodModifierArray(mp));
        final String methodSignature = mp.getMethodSignature();
        o.addProperty("header", returnType + " " + methodSignature + " {");
        o.addProperty("rawEnd", 0);
        o.addProperty("signature", methodSignature);
        return o;
    }

    private JsonArray createSymTableMethodThrowsArray(MethodProcessor mp) {
        final ObjectType[] ot = mp.getExceptionTypes();
        if (ot != null && ot.length > 0) {
            final JsonArray array = new JsonArray();
            Arrays.stream(ot).forEach(x -> {
                array.add(x.toString());
            });
            return array;
        }
        return null;
    }

    private JsonObject createSymTableMethodArgsObject(MethodProcessor mp) {
        final JsonObject o = new JsonObject();
        final AtomicInteger i = new AtomicInteger(0);
        final SignatureAttribute.Type[] paramTypes = mp.getParameterTypes();
        if (paramTypes != null) {
            Arrays.stream(paramTypes).forEach(x -> {
                final String typeName = x.toString();
                final JsonObject arg = new JsonObject();
                o.add("arg" + i.getAndIncrement(), arg);
                arg.addProperty("Type", typeName);
                JsonArray coreTypeArray = new JsonArray();
                coreTypeArray.add(typeName);
                arg.add("CoreType", coreTypeArray);
                arg.add("TypeAST", createTypeAST(typeName));
            });
        }
        return o;
    }

    private JsonObject createSymTableSuperClassObject(ClassProcessor cp) {
        JsonObject o = null;
        final String[] interfaces = cp.getInterfaces();
        if (interfaces != null && interfaces.length > 0) {
            o = new JsonObject();
            JsonObject implementsObj = new JsonObject();
            o.add("implements", implementsObj);
            implementsObj.addProperty("impl_start", 0);
            JsonArray valuesArray = new JsonArray();
            Arrays.stream(interfaces).forEach(x -> {
                valuesArray.add(x);
            });
            implementsObj.add("values", valuesArray);
        }
        final String superClass = cp.getSuperClassName();
        if (superClass != null && !superClass.equals("java.lang.Object")) {
            if (o == null) {
                o = new JsonObject();
            }
            JsonObject extendsObj = new JsonObject();
            o.add("extends", extendsObj);
            extendsObj.addProperty("ext_start", 0);
            JsonArray valuesArray = new JsonArray();
            valuesArray.add(superClass);
            extendsObj.add("values", valuesArray);
            JsonArray coreTypeArray = new JsonArray();
            coreTypeArray.add(valuesArray);
            extendsObj.add("CoreType", coreTypeArray);
        }
        return o;
    }

    private JsonObject createTypeAST(String typeName) {
        final JsonObject typeAST = new JsonObject();
        typeAST.addProperty("name", typeName);
        typeAST.add("dimensions", new JsonArray());
        typeAST.add("arguments", null);
        typeAST.add("sub_type", null);
        return typeAST;
    }

    private JsonArray createSymTableClassModifierArray(ClassProcessor cp) {
        return createModifierArray(cp.getModifiers());
    }

    private JsonArray createSymTableFieldModifierArray(FieldProcessor fp) {
        return createModifierArray(fp.getModifiers());
    }

    private JsonArray createSymTableMethodModifierArray(MethodProcessor mp) {
        return createModifierArray(mp.getModifiers());
    }

    private JsonArray createModifierArray(int modifiers) {
        final JsonArray array = new JsonArray();
        if (Modifier.isAbstract(modifiers)) {
            array.add("abstract");
        }
        if (Modifier.isAnnotation(modifiers)) {
            array.add("annotation");
        }
        if (Modifier.isEnum(modifiers)) {
            array.add("enum");
        }
        if (Modifier.isFinal(modifiers)) {
            array.add("final");
        }
        if (Modifier.isInterface(modifiers)) {
            array.add("interface");
        }
        if (Modifier.isNative(modifiers)) {
            array.add("native");
        }
        if (Modifier.isPrivate(modifiers)) {
            array.add("private");
        }
        if (Modifier.isProtected(modifiers)) {
            array.add("protected");
        }
        if (Modifier.isPublic(modifiers)) {
            array.add("public");
        }
        if (Modifier.isStatic(modifiers)) {
            array.add("static");
        }
        if (Modifier.isStrict(modifiers)) {
            array.add("strictfp");
        }
        if (Modifier.isSynchronized(modifiers)) {
            array.add("synchronized");
        }
        if (Modifier.isTransient(modifiers)) {
            array.add("transient");
        }
        if (Modifier.isVarArgs(modifiers)) {
            array.add("varargs");
        }
        if (Modifier.isVolatile(modifiers)) {
            array.add("volatile");
        }
        return array;
    }

    private JsonObject createSymTableFuncSigObject(JsonObject classObject) {
        final JsonObject o = new JsonObject();
        final JsonObject methodsObject = classObject.get("funcL").getAsJsonObject();
        methodsObject.entrySet().forEach(x -> {
            final String methodName = x.getKey();
            final String signature = x.getValue().getAsJsonObject().get("signature").getAsString();
            o.addProperty(signature, methodName);
        });
        return o;
    }

    private JsonObject createRefTable() {
        final JsonObject o = new JsonObject();
        o.add("Files", new JsonObject());
        o.add("Dup_Class", new JsonObject());
        o.addProperty("Version", "v2.0.0r44");
        o.add("Enums", new JsonObject());
        o.add("FQCN", new JsonObject());
        o.add("Jparser", new JsonObject());
        return o;
    }

    private JsonObject createAgentConfiguration() {
        final JsonObject o = new JsonObject();
        o.add("filter", createTypedFactoryConfiguration("sym-ref-tables", "1.0", "."));
        o.add("generator", createTypedFactoryConfiguration("println", "1.0", useSystemOut ? "out" : "err"));
        o.addProperty("logging", "info");
        return o;
    }

    private JsonObject createTypedFactoryConfiguration(String type, String version, String config) {
        final JsonObject o = new JsonObject();
        o.addProperty("type", type);
        o.addProperty("version", version);
        o.addProperty("config", config);
        return o;
    }

    private Set<String> computeOverloadedMethodSet(ClassProcessor cp) {
        final Set<String> methods = new HashSet<>();
        final Set<String> overloadedMethods = new HashSet<>();
        cp.getMethods().forEach(x -> {
            final String methodName = x.getMethodName();
            if (methods.contains(methodName)) {
                overloadedMethods.add(methodName);
            }
            else {
                methods.add(methodName);
            }
        });
        return overloadedMethods;
    }

    private String generateOverloadedName(String baseName, AtomicInteger count) {
        return baseName + " [overloaded_#00" + count.getAndIncrement() + "]";
    }

    private void resolveDuplicateClassMappings() {
        if (!duplicateClassMap.isEmpty()) {
            // Fill in the Dup_Class object with file mappings.
            final JsonObject dupeClasses = refTable.get("Dup_Class").getAsJsonObject();
            if (dupeClasses.size() == 0) {
                duplicateClassMap.forEach((k, v) -> {
                    final JsonObject fileMap = new JsonObject();
                    v.forEach(x -> {
                        JsonElement e = symTable.get(x);
                        if (e != null && e.isJsonObject()) {
                            JsonObject classObj = e.getAsJsonObject();
                            fileMap.addProperty(classObj.get("file").getAsString(), x);
                        }
                    });
                    dupeClasses.add(k, fileMap);
                });
            }
        }
    }
}
