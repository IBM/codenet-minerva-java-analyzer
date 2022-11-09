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

import static com.ibm.minerva.analyzer.MessageFormatter.formatMessage;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Analyzer {
    
    private static final Logger logger = LoggingUtil.getLogger(Analyzer.class);
    
    private final File archive;
    private final File outputDir;
    private final ApplicationProcessor ap;
    
    private Set<String> packages;
    private boolean isPackageIncludeList;
    private boolean useSystemOut;
    
    public Analyzer(File archive, File outputDir) {
        if (archive == null || outputDir == null) {
            throw new NullPointerException();
        }
        this.archive = archive;
        this.outputDir = outputDir;
        this.ap = new TableBuilder(outputDir);
    }
    
    public Analyzer setPackageRestrictions(Set<String> packages, boolean isPackageIncludeList) {
        this.packages = packages;
        this.isPackageIncludeList = isPackageIncludeList;
        ap.setPackageRestrictions(packages, isPackageIncludeList);
        return this;
    }
    
    public Analyzer setAgentOutputStream(boolean useSystemOut) {
        this.useSystemOut = useSystemOut;
        ap.setAgentOutputStream(useSystemOut);
        return this;
    }
    
    public void run() throws IOException {
        logger.info(() -> formatMessage("StartingAnalyzer"));
        logger.info(() -> formatMessage("AnalyzingArchive", archive));
        logger.config(() -> formatMessage("OutputDirectory", outputDir));
        if (packages != null) {
            logger.config(() -> formatMessage(isPackageIncludeList ?
                    "PackageIncludeList" : "PackageExcludeList", packages));
        }
        logger.config(() -> formatMessage("AgentOutputStream", 
                useSystemOut ? "System.out" : "System.err"));
        final ArchiveProcessor archiveProcessor = new ArchiveProcessor(ap);
        archiveProcessor.processBinaryFile(archive);
        ap.write();
    }
    
    public static void setLoggingLevel(Level level) {
        LoggingUtil.setLoggingLevel(level);
    }
    
    public static void main(String[] args) {
        if (args.length > 1) {
            final Analyzer analyzer = new Analyzer(new File(args[0]), new File(args[1]));
            if (args.length > 2) {
                final Set<String> packages = new LinkedHashSet<>();
                final StringTokenizer st = new StringTokenizer(args[2], ",");
                while (st.hasMoreTokens()) {
                    final String token = st.nextToken().trim();
                    if (!token.isEmpty()) {
                        packages.add(token);
                    }
                }
                analyzer.setPackageRestrictions(packages, false);
            }
            try {
                analyzer.setAgentOutputStream(false).run();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
