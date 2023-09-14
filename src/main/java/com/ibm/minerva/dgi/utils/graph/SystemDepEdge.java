/*
Copyright IBM Corporation 2023

Licensed under the Apache Public License 2.0, Version 2.0 (the "License");
you may not use this file except in compliance with the License.

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package com.ibm.minerva.dgi.utils.graph;

import com.ibm.wala.ipa.slicer.Statement;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.jgrapht.nio.Attribute;
import org.jgrapht.nio.DefaultAttribute;

import java.util.LinkedHashMap;
import java.util.Map;

public class SystemDepEdge extends AbstractGraphEdge {
    public final Integer sourcePos;
    public final Integer destinationPos;
    public final String type;
    public static final long serialVersionUID = -8284030936836318929L;

    public SystemDepEdge(Statement sourceStatement, Statement destinationStatement, String type) {
        super();
        this.type = type;
        this.sourcePos = getStatementPosition(sourceStatement);
        this.destinationPos = getStatementPosition(destinationStatement);
    }
    public SystemDepEdge(Statement sourceStatement, Statement destinationStatement, String type, String context) {
        super(context);
        this.type = type;
        this.sourcePos = getStatementPosition(sourceStatement);
        this.destinationPos = getStatementPosition(destinationStatement);
    }
    @Override
    public int hashCode() {
        return new HashCodeBuilder(17, 37).append(sourcePos).append(destinationPos).append(context).append(type).build();
    }

    @Override
    public boolean equals(Object o) {
        return (o instanceof SystemDepEdge) && (this.toString().equals(o.toString())) && Integer.valueOf(this.hashCode()).equals(o.hashCode()) && this.type.equals(((SystemDepEdge) o).getType());
    }
    public String getType() { return type; }
    public Integer getSourcePos() {
        return sourcePos;
    }

    public Integer getDestinationPos() {
        return destinationPos;
    }

    public Map<String, Attribute> getAttributes() {
        Map<String, Attribute> map = new LinkedHashMap<>();
        map.put("id", DefaultAttribute.createAttribute(getId()));
        map.put("type", DefaultAttribute.createAttribute(getType()));
        map.put("weight", DefaultAttribute.createAttribute(getWeight()));
        map.put("context", DefaultAttribute.createAttribute(getContext()));
        map.put("sourcePos", DefaultAttribute.createAttribute(getSourcePos()));
        map.put("destinationPos", DefaultAttribute.createAttribute(getDestinationPos()));
        return map;
    }
}