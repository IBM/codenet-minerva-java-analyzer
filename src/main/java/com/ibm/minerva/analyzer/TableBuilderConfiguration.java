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

public enum TableBuilderConfiguration {
    
    ALL {
        @Override
        public boolean generateSymRefTables() {
            return true;
        }
        @Override
        public boolean generateInstrumentationConfig() {
            return true;
        }
    },
    SYM_REF_TABLES {
        @Override
        public boolean generateSymRefTables() {
            return true;
        }
        @Override
        public boolean generateInstrumentationConfig() {
            return false;
        }
    },
    INSTRUMENTATION_CONFIG {
        @Override
        public boolean generateSymRefTables() {
            return false;
        }
        @Override
        public boolean generateInstrumentationConfig() {
            return true;
        }
    },
    NONE {
        @Override
        public boolean generateSymRefTables() {
            return false;
        }
        @Override
        public boolean generateInstrumentationConfig() {
            return false;
        }
    };
    
    public abstract boolean generateSymRefTables();
    public abstract boolean generateInstrumentationConfig();

}
