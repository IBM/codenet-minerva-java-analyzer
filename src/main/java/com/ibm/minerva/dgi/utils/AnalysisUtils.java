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

package com.ibm.minerva.dgi.utils;

import com.ibm.minerva.analyzer.LoggingUtil;
import com.ibm.wala.classLoader.IClass;
import com.ibm.wala.classLoader.IMethod;
import com.ibm.wala.ipa.callgraph.Entrypoint;
import com.ibm.wala.ipa.callgraph.impl.DefaultEntrypoint;
import com.ibm.wala.ipa.cha.IClassHierarchy;
import com.ibm.wala.types.ClassLoaderReference;

import static com.ibm.minerva.analyzer.MessageFormatter.formatMessage;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class AnalysisUtils {
	
  private static final Logger logger = LoggingUtil.getLogger(AnalysisUtils.class);

  public static Map<String, Map<String, Object>> classAttr = new HashMap<String, Map<String, Object>>();

  /**
   * Verfy if a class is an application class.
   *
   * @param _class
   * @return Boolean
   */
  public static Boolean isApplicationClass(IClass _class) {
    if (_class.getClassLoader().getReference().equals(ClassLoaderReference.Application)) {
      return true;
    } else {
      return false;
    }
  }

  public static long getNumberOfApplicationClasses(IClassHierarchy cha) {
    return StreamSupport.stream(cha.spliterator(), false)
            .filter(AnalysisUtils::isApplicationClass)
            .count();
  }
  /**
   * Use all public methods of all application classes as entrypoints.
   *
   * @param cha
   * @return Iterable<Entrypoint>
   */
  public static Iterable<Entrypoint> getEntryPoints(IClassHierarchy cha) {
    List<Entrypoint> entrypoints = StreamSupport.stream(cha.spliterator(), true)
            .filter(AnalysisUtils::isApplicationClass)
            .flatMap(c -> {
              try {
                return c.getDeclaredMethods().stream();
              } catch (Throwable t) {
				logger.severe(() -> formatMessage("CallGraphBuildError", t.getMessage()));
                return Stream.empty();
              }
            })
            .filter(method -> method.isPublic()
                    || method.isPrivate()
                    || method.isProtected()
                    || method.isStatic())
            .map(method -> new DefaultEntrypoint(method, cha))
            .collect(Collectors.toList());
    return entrypoints;
  }

  public static void expandSymbolTable(IClassHierarchy cha) {
    StreamSupport.stream(cha.spliterator(), true)
            .filter(AnalysisUtils::isApplicationClass)
            .forEach(c -> {
              String className = c.getName().getClassName().toString();
              Map<String, Object> classAttributeMap = new HashMap<>();
              classAttributeMap.put("isPrivate", Boolean.toString(c.isPrivate()));
              classAttributeMap.put("source_file_name", c.getSourceFileName());
              classAttributeMap.put("num_fields", c.getAllFields().size());
              classAttributeMap.put("num_static_fields", c.getAllStaticFields().size());
              long num_static_methods = c.getDeclaredMethods().stream().filter(IMethod::isStatic).count();
              classAttributeMap.put("num_static_methods", num_static_methods);
              classAttributeMap.put("num_declared_methods", c.getDeclaredMethods().size());
              classAttr.put(className, classAttributeMap);
    });
  }
}
