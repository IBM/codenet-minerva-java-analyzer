# Project Minerva for Modernization - Java Binary Analyzer

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

Project Minerva for Modernization is a set of libraries to analyze Java applications using AI and provide recommendations for refactoring them into partitions, which can be starting points for microservices. This binary analyzer component enables the static analysis of Java application code in order to collect data for AI refactoring consideration.

# Build (Maven Based)
mvn install

# Build (Docker Based)
docker build -t minerva-analyzer .

docker run --rm -it -v [target dir]:/var/install minerva-analyzer

# Usage

java -classpath minerva-analyzer-1.0.jar com.ibm.minerva.analyzer.Analyzer [archive path] [output dir] {optional package exclusion list; comma separated}

The supported archives include .jar, .war, .ear, .zip and .rar.

e.g. java -classpath minerva-analyzer-1.0.jar com.ibm.minerva.analyzer.Analyzer /c/daytrader.ear /c/analyzer-data

The above command line invocation would analyze /c/daytrader.ear and write output to /c/analyzer-data.

e.g. java -classpath minerva-analyzer-1.0.jar com.ibm.minerva.analyzer.Analyzer /c/petstore.war /c/analyzer-data javax,org.apache

The above command line invocation would analyze /c/petstore.war (excluding all classes found within javax and org.apache) and write output to /c/analyzer-data.
