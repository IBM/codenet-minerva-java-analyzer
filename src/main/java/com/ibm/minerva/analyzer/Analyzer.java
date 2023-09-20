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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Analyzer {

    private static final Logger logger = LoggingUtil.getLogger(Analyzer.class);

    private final File[] archives;
    private final List<File> additionalLibraries;
    private final File outputDir;
    private final TableBuilderConfiguration config;
    private final ApplicationProcessor ap;

    private Set<String> packages;
    private boolean isPackageIncludeList;
    private boolean useSystemOut;
    private CallGraphBuilderType callGraphBuilderType;

    public Analyzer(File archive, File outputDir) {
        this(archive, outputDir, TableBuilderConfiguration.ALL);
    }
    
    public Analyzer(File archive, File outputDir, TableBuilderConfiguration config) {
        this(new File[] {archive}, outputDir, config);
    }
    
    public Analyzer(File[] archives, File outputDir) {
        this(archives, outputDir, TableBuilderConfiguration.ALL);
    }
    
    public Analyzer(File[] archives, File outputDir, TableBuilderConfiguration config) {
    	this(archives, outputDir, config, null);
    }
    
    public Analyzer(File[] archives, File outputDir, File[] additionalLibs) {
    	this(archives, outputDir, TableBuilderConfiguration.ALL, additionalLibs);
    }
    
    public Analyzer(File[] archives, File outputDir, TableBuilderConfiguration config, File[] additionalLibs) {
        if (archives == null || Arrays.stream(archives).anyMatch(x -> x == null) || outputDir == null) {
            throw new NullPointerException();
        }
        this.archives = archives;
        this.outputDir = outputDir;
        this.config = (config != null) ? config : TableBuilderConfiguration.NONE;
        this.ap = new TableBuilder(outputDir, config);
        this.additionalLibraries = new ArrayList<File>();
        
        if (additionalLibs != null) {
        	addExtraLibraries(additionalLibs);
        }
        
    }

    public Analyzer setPackageRestrictions(Set<String> packages, boolean isPackageIncludeList) {
        this.packages = packages;
        this.isPackageIncludeList = isPackageIncludeList;
        ap.setPackageRestrictions(packages, isPackageIncludeList);
        return this;
    }
    
    public Analyzer setAllowAnyLegalClasses(boolean allowAnyLegalClasses) {
        ap.setAllowAnyLegalClasses(allowAnyLegalClasses);
        return this;
    }

    public Analyzer setAgentOutputStream(boolean useSystemOut) {
        this.useSystemOut = useSystemOut;
        ap.setAgentOutputStream(useSystemOut);
        return this;
    }

    public Analyzer setCallGraphBuilder(boolean useCallGraphBuilder) throws IOException {
        return setCallGraphBuilder(useCallGraphBuilder ? CallGraphBuilderType.ZERO_ONE_CFA : null);
    }
    
    public Analyzer setCallGraphBuilder(CallGraphBuilderType type) throws IOException {
        this.callGraphBuilderType = type;
        ap.setCallGraphBuilder(type != null ? new CallGraphBuilder(type) : null);
        return this;
    }
    
    public void addExtraLibrary(File extraLib) {
    	if (extraLib != null) {
    		final String name = extraLib.getName().toLowerCase(Locale.ENGLISH);
            final BinaryType bt = BinaryType.getBinaryType(name);
            if (bt == BinaryType.JAR) {
            	this.additionalLibraries.add(extraLib);
            }
    	}
    }
    
    public void addExtraLibraries (File[] extraLibs) {
		// Add extra user provided JARS to scope (i.e., JEE libraries)
        if (extraLibs != null) {
        	for (File extraLib : extraLibs) {
        		// if is a directory, try to add any JAR inside the path as an extra library
        		if (extraLib.isDirectory()) {
        			File[] listOfExtraLibs = extraLib.listFiles();
        			for (File e : listOfExtraLibs) {
        				addExtraLibrary(e);
        			}
        		}
        		else {
	            	addExtraLibrary(extraLib);
	            }
			}
        }
    }

    public void run() throws IOException {
        try {
            logger.info(() -> formatMessage("StartingAnalyzer"));
            logger.config(() -> formatMessage("OutputDirectory", outputDir));
            if (packages != null) {
                logger.config(() -> formatMessage(isPackageIncludeList ?
                        "PackageIncludeList" : "PackageExcludeList", packages));
            }
            logger.config(() -> formatMessage("AgentOutputStream", 
                    useSystemOut ? "System.out" : "System.err"));
            if (callGraphBuilderType != null) {
                logger.config(() -> formatMessage("CallGraphAlgorithm",
                        callGraphBuilderType));
            }
            // Skips archive processing if there are no consumers (e.g. if only
            // the "instrumenter-config.json" is being generated).
            if (config.generateSymRefTables() || callGraphBuilderType != null) {
                final ArchiveProcessor archiveProcessor = new ArchiveProcessor(ap);
                for (File archive : archives) {
                    logger.info(() -> formatMessage("AnalyzingArchive", archive));
                    archiveProcessor.processBinaryFile(archive);
                }
                
                // Add extra libraries, if is there any
                if (additionalLibraries != null && additionalLibraries.size() > 0) {
                	archiveProcessor.processExtraLibs(additionalLibraries.toArray(new File[additionalLibraries.size()]));
                }
            }
            
            ap.write();
        }
        finally {
            // Schedule any temporary files created during the process for deletion.
            ap.clean();
        }
    }

    public static void setLoggingLevel(Level level) {
        LoggingUtil.setLoggingLevel(level);
    }

    // [0] : archive path(s)
    // [1] : output directory
    // [2] : additional libraries (especially JEE libraries)
    // [3] : package exclusion list
    // [4] : build call graph (true|false|<algorithm-name>)
    public static void main(String[] args) {
        if (args.length > 1) {
            final Analyzer analyzer;
            final List<File> archives = new ArrayList<>();
            final List<File> extraLibs = new ArrayList<>();
            
            final StringTokenizer st = new StringTokenizer(args[0], File.pathSeparator);
            while (st.hasMoreTokens()) {
                final String file = st.nextToken().trim();
                if (!file.isEmpty()) {
                    archives.add(new File(file));
                }
            }
            if (args.length > 2) {
                final StringTokenizer st2 = new StringTokenizer(args[2], File.pathSeparator);
                while (st2.hasMoreTokens()) {
                    final String file = st2.nextToken().trim();
                    if (!file.isEmpty()) {
                    	extraLibs.add(new File(file));
                    }
                }
            }
            
            analyzer = new Analyzer(archives.toArray(new File[archives.size()]), new File(args[1]), extraLibs.toArray(new File[extraLibs.size()]));
            
            if (args.length > 3) {
                final Set<String> packages = new LinkedHashSet<>();
                final StringTokenizer st3 = new StringTokenizer(args[3], ",");
                while (st3.hasMoreTokens()) {
                    final String token = st3.nextToken().trim();
                    if (!token.isEmpty()) {
                        packages.add(token);
                    }
                }
                analyzer.setPackageRestrictions(packages, false);
            }
            try {
                if (args.length > 4) {
                    final boolean generateCallGraph = Boolean.parseBoolean(args[4]);
                    if (generateCallGraph) {
                        analyzer.setCallGraphBuilder(generateCallGraph);
                    }
                    else {
                        // Try to match the name of one of the CallGraphBuilderType enum values.
                        Optional<CallGraphBuilderType> o = CallGraphBuilderType.find(args[4]);
                        if (o.isPresent()) {
                            analyzer.setCallGraphBuilder(o.get());
                        }
                    }
                }
                analyzer.setAgentOutputStream(false).run();
            }
            catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
