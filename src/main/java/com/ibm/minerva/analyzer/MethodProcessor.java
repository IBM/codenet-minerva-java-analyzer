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

import javassist.CtBehavior;
import javassist.bytecode.SignatureAttribute;
import javassist.bytecode.SignatureAttribute.MethodSignature;
import javassist.bytecode.SignatureAttribute.ObjectType;

public class MethodProcessor {
    
    private final ClassProcessor classProcessor;
    private final CtBehavior ctBehavior;
    
    public MethodProcessor(ClassProcessor classProcessor, CtBehavior ctBehavior) {
        this.classProcessor = classProcessor;
        this.ctBehavior = ctBehavior;
    }
    
    public ClassProcessor getClassProcessor() {
        return classProcessor;
    }
    
    public CtBehavior getCtBehavior() {
        return ctBehavior;
    }
    
    public String getMethodName() {
        final String methodName = ctBehavior.getName();
        // Handle the special case of a constructor of an inner class.
        // Return the inner most class name.
        if (ctBehavior.getMethodInfo().isConstructor()) {
            final ClassProcessor classProcessor = getClassProcessor();
            if (classProcessor.isNestedClass()) {
                final int idx = methodName.lastIndexOf('$');
                if (idx >= 0) {
                    return methodName.substring(idx + 1);
                }
            }
        }
        return methodName;
    }
    
    public int getModifiers() {
        return ctBehavior.getModifiers();
    }
    
    public SignatureAttribute.Type[] getParameterTypes() {
        final MethodSignature ms = getMethodSignatureObject();
        if (ms != null) {
            final SignatureAttribute.Type[] paramTypes = ms.getParameterTypes();
            final int paramTypesLen = paramTypes != null ? paramTypes.length : 0;
            // Handle the special case of a constructor of a non-static inner class.
            // Ignore the implicit outer class parameter.
            if (ctBehavior.getMethodInfo().isConstructor()) {
                final ClassProcessor classProcessor = getClassProcessor();
                if (classProcessor.isNestedClass() && !classProcessor.isStaticClass()) {
                    if (paramTypesLen > 0) {
                        if (paramTypesLen > 1) {
                            final SignatureAttribute.Type[] modParamTypes = new SignatureAttribute.Type[paramTypesLen - 1];
                            System.arraycopy(paramTypes, 1, modParamTypes, 0, modParamTypes.length);
                            return modParamTypes;
                        }
                        return null;
                    }
                }
            }
            return paramTypes;
        }
        return null;
    }
    
    public SignatureAttribute.Type getReturnType() {
        final MethodSignature ms = getMethodSignatureObject();
        if (ms != null) {
            return ms.getReturnType();
        }
        return null;
    }
    
    public ObjectType[] getExceptionTypes() {
        final MethodSignature ms = getMethodSignatureObject();
        if (ms != null) {
            return ms.getExceptionTypes();
        }
        return null;
    }
    
    public String getMethodSignature() {
        final StringBuilder sb = new StringBuilder();
        sb.append(getMethodName());
        sb.append('(');
        final SignatureAttribute.Type[] params = getParameterTypes();
        boolean paramProcessed = false;
        if (params != null && params.length > 0) {
            for (SignatureAttribute.Type param : params) {
                if (paramProcessed) {
                    sb.append(", ");
                }
                sb.append(toClassName(param.toString()));
                paramProcessed = true;
            }
        }
        sb.append(')');
        return sb.toString();
    }
    
    private MethodSignature getMethodSignatureObject() {
        final String sig = ctBehavior.getSignature();
        if (sig != null) {
            try {
                return SignatureAttribute.toMethodSignature(sig);
            }
            catch (Exception e) {
                // TODO: Add logging message.
            }
        }
        return null;
    }
    
    private static String toClassName(String jvmType) {
        return jvmType.replace('$', '.');
    }
}
