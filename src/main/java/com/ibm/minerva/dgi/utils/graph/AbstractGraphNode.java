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

import java.io.Serializable;
import java.util.Map;
import org.jgrapht.nio.Attribute;


public abstract class AbstractGraphNode implements Serializable {

	private static final long serialVersionUID = 1L;

	public abstract String getId();

    public abstract Map<String, Attribute> getAttributes();
}