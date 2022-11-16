# ******************************************************************************* 
# * Copyright (c) contributors to the Minerva for Modernization project.
# * Licensed under the Apache License, Version 2.0 (the "License");
# * you may not use this file except in compliance with the License.
# * You may obtain a copy of the License at
# * 
# *     http://www.apache.org/licenses/LICENSE-2.0
# * 
# * Unless required by applicable law or agreed to in writing, software
# * distributed under the License is distributed on an "AS IS" BASIS,
# * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# * See the License for the specific language governing permissions and
# * limitations under the License.
# *
# * Contributors:
# *     IBM Corporation - initial implementation
# *******************************************************************************

######################################################################
# Build Minerva Analyzer
######################################################################
FROM maven:3.6.1-ibmjava-8 as builder
COPY /src /src
COPY pom.xml /
RUN mvn install

######################################################################
# Build Minerva Analyzer Installer
######################################################################
FROM busybox
RUN mkdir /analyzer
COPY --from=builder /target/minerva-analyzer-1.0-jar-with-dependencies.jar /analyzer/minerva-analyzer-1.0.jar
RUN mkdir /scripts
COPY install.sh /scripts
RUN chmod +x /scripts/install.sh
CMD ["sh", "/scripts/install.sh"]