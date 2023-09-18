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

package com.ibm.minerva.dgi.utils.graph;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;

import com.ibm.wala.classLoader.IMethod;

public class MethodNode extends AbstractGraphNode {

	private static final long serialVersionUID = 1L;
	public final String id;
    public final String method;
    public final String returnType;
    public final List<String> arguments;
    public final String className;
    public final String classShortName;

    public MethodNode(IMethod method) {
        this.method = method.getName().toString();
        this.className = method.getDeclaringClass()
                .getName()
                .toString()
                .substring(1)
                .replace("/", ".");
        this.classShortName = className
                .substring(className.lastIndexOf('/') + 1)
                .replace("$", "_");
        this.id = generateNodeId();
        this.returnType = method.getReturnType()
                .getName()
                .toString()
                .substring(1)
                .replace("/", ".");
        this.arguments = getArgumentsList(method);
    }

    private List<String> getArgumentsList(IMethod method) {
        return IntStream.range(0, method.getNumberOfParameters())
                .mapToObj(i -> method.getParameterType(i)
                        .getName()
                        .toString()
                        .substring(1)
                        .replace("/", "."))
                .collect(Collectors.toList());
    }

    private String generateNodeId() {
        return getMethodSignature();
    }

    public String getId() {
        return id;
    }

    public String getMethodSignature() {
        return className + "." + method;
    }
    public String getClassSignature() {
        return className;
    }

    @Override
    public String toString() {
        return method;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof MethodNode) && (toString().equals(o.toString()));
    }

    public String getMethod() {
        return method;
    }

    public String getClassName() {
        return classShortName;
    }

    public List<String> getArguments() {
        return arguments;
    }

    public String getReturnType() {
        return returnType;
    }

    @Override
    public Map<String, Attribute> getAttributes() {
        Map<String, Attribute> map = new LinkedHashMap<>();
        map.put("id", DefaultAttribute.createAttribute(getId()));
        map.put("class", DefaultAttribute.createAttribute(getClassName()));
        map.put("method", DefaultAttribute.createAttribute(getMethod()));
        map.put("arguments", DefaultAttribute.createAttribute(getArguments().toString()));
        map.put("returnType", DefaultAttribute.createAttribute(getReturnType()));
        return map;
    }
}

