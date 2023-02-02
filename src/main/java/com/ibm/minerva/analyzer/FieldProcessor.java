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

import javassist.CtField;
import javassist.bytecode.SignatureAttribute;

public class FieldProcessor {

    private final ClassProcessor classProcessor;
    private final CtField ctField;

    public FieldProcessor(ClassProcessor classProcessor, CtField ctField) {
        this.classProcessor = classProcessor;
        this.ctField = ctField;
    }

    public ClassProcessor getClassProcessor() {
        return classProcessor;
    }

    public CtField getCtField() {
        return ctField;
    }

    public String getFieldName() {
        return ctField.getName();
    }

    public SignatureAttribute.Type getType() {
        final String sig = ctField.getSignature();
        if (sig != null) {
            try {
                return SignatureAttribute.toTypeSignature(sig);
            }
            catch (Exception e) {
                // TODO: Add logging message.
            }
        }
        return null;
    }

    public int getModifiers() {
        return ctField.getModifiers();
    }
}
