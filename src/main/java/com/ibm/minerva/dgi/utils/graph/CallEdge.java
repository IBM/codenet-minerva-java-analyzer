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

import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;
import java.util.LinkedHashMap;
import java.util.Map;

public class CallEdge extends AbstractGraphEdge {
    public final String type;
    public static final long serialVersionUID = -8284030936836318929L;
    public CallEdge() {
        super();
        this.type = toString();
    }
    public CallEdge(String context) {
        super(context);
        this.type = toString();
    }
    @Override
    public String toString() {
        return "CALL_DEP";
    }
    @Override
    public boolean equals(Object o) {
        return (o instanceof CallEdge) && (toString().equals(o.toString()));
    }

    public Map<String, Attribute> getAttributes() {
        Map<String, Attribute> map = new LinkedHashMap<>();
        map.put("id", DefaultAttribute.createAttribute(getId()));
        map.put("context", DefaultAttribute.createAttribute(getContext()));
        map.put("type", DefaultAttribute.createAttribute(toString()));
        map.put("weight", DefaultAttribute.createAttribute(getWeight()));
        return map;
    }
}