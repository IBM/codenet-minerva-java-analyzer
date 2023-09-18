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

import java.io.Serializable;
import java.util.Map;

import org.jgrapht.nio.Attribute;

import com.ibm.wala.ipa.callgraph.CGNode;
import com.ibm.wala.ipa.slicer.Statement;
import com.ibm.wala.shrike.shrikeCT.InvalidClassFileException;
import com.ibm.wala.ssa.IR;
import com.ibm.wala.ssa.SSAInstruction;


public abstract class AbstractGraphEdge implements Serializable {
	private static final long serialVersionUID = 1L;
	public Integer weight = 1;
    public final String context;
    protected AbstractGraphEdge() {
        this(null);
    }
    protected AbstractGraphEdge(String context) {
        this.context = context;
    }
    public void incrementWeight() { this.weight += 1; }
    public String getContext() { return this.context; }
    public Integer getId() { return this.hashCode(); }
    public Integer getWeight() { return this.weight; }
    Integer getStatementPosition(Statement statement) {
        CGNode statementNode = statement.getNode();
        IR statementIR = statementNode.getIR();
        Integer pos = null;
        // TODO: check this assumption: the same source instruction maps to several SSAInstructions,
        //  therefore it is sufficient to return the position of the first statement.
        for (SSAInstruction inst : statementNode.getIR().getInstructions()) {
            try {
                pos = statementIR.getMethod().getSourcePosition(inst.iIndex()).getLastLine();
                return pos;
            } catch (InvalidClassFileException e) {
                throw new RuntimeException(e);
            } catch (NullPointerException npe) {
                return -1;
            }
        }
        return pos;
    }

    public abstract Map<String, Attribute> getAttributes();
}
