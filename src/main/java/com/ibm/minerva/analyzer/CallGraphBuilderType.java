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

import com.ibm.wala.cast.java.client.impl.ZeroCFABuilderFactory;
import com.ibm.wala.cast.java.client.impl.ZeroOneCFABuilderFactory;
import com.ibm.wala.ipa.callgraph.AnalysisOptions;
import com.ibm.wala.ipa.callgraph.IAnalysisCacheView;
import com.ibm.wala.ipa.callgraph.impl.Util;
import com.ibm.wala.ipa.cha.IClassHierarchy;

public enum CallGraphBuilderType {
    
    RTA((options,cache,cha) -> Util.makeRTABuilder(options, cache, cha)),
    ZERO_CFA((options,cache,cha) -> new ZeroCFABuilderFactory().make(options, cache, cha)),
    ZERO_ONE_CFA((options,cache,cha) -> new ZeroOneCFABuilderFactory().make(options, cache, cha));
    
    @FunctionalInterface
    interface CallGraphBuilderFactory {
        public com.ibm.wala.ipa.callgraph.CallGraphBuilder<?> createCallGraphBuilder(
                AnalysisOptions options, 
                IAnalysisCacheView cache, 
                IClassHierarchy cha);
    }
    
    private final CallGraphBuilderFactory factory;
    
    private CallGraphBuilderType(CallGraphBuilderFactory factory) {
        this.factory = factory;
    }
    
    com.ibm.wala.ipa.callgraph.CallGraphBuilder<?> createCallGraphBuilder(AnalysisOptions options, 
            IAnalysisCacheView cache, 
            IClassHierarchy cha) {
        return factory.createCallGraphBuilder(options, cache, cha);
    }
}
