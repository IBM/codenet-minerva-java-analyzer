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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.Locale;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarInputStream;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.runtime.Desc;
import javassist.scopedpool.ScopedClassPoolFactoryImpl;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;

public final class ArchiveProcessor {

    private static final Logger logger = LoggingUtil.getLogger(ArchiveProcessor.class);

    private final ApplicationProcessor ap;

    public ArchiveProcessor(ApplicationProcessor ap) {
        this.ap = ap;
    }
    
    public void processExtraLibs(File[] extraLibs) {
    	ap.processExtraLibs(extraLibs);
    }

    public void processBinaryFile(File thisBinaryFile) throws IOException {
        if (!thisBinaryFile.exists()) {
            // Warning message for non-existent file.
            logger.warning(() -> formatMessage("ArchiveDoesNotExist", thisBinaryFile));
            return;
        }
        if (thisBinaryFile.length() == 0) {
            // Warning message for an empty file.
            logger.warning(() -> formatMessage("ArchiveEmpty", thisBinaryFile));
            return;
        }
        final String name = thisBinaryFile.getName().toLowerCase(Locale.ENGLISH);
        final BinaryType bt = BinaryType.getBinaryType(name);
        switch (bt) {
        case JAR:
        case WAR:
        case EAR:
        case RAR:
        case EBA:
        case CBA:
            processJarFile(thisBinaryFile, bt);
            break;
        case ZIP:
            processZipFile(thisBinaryFile);
            break;
        case CLASS:
            processClassFile(thisBinaryFile);
            break;
        case UNKNOWN:
            break;
        }
    }

    private void processJarFile(File thisBinaryFile, BinaryType jarType) throws IOException {
        // Use the JarFile constructor that takes a boolean to turn off signature verification. Otherwise, jar files
        // that contain invalid manifest signatures cannot be scanned.
        final JarFile fileToProcess = new JarFile(thisBinaryFile.getAbsoluteFile(), false);
        try {
            final Enumeration<JarEntry> entries = fileToProcess.entries();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                processJarEntry(fileToProcess, entry, jarType);
            }
        }
        catch (Exception e) {
            logger.severe(() -> formatMessage("ArchiveReadError", jarType.getExtension(), e.getMessage()));
        } 
        finally {
            try {
                fileToProcess.close();
            } 
            catch (IOException e) {
                logger.severe(() -> formatMessage("ArchiveCloseError", jarType.getExtension(), e.getMessage()));
            }
        }
    }

    private void processJarEntry(JarFile fileToProcess, JarEntry entry, BinaryType jarType) {
        InputStream is = null;
        final String entryName = entry.getName();
        final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
        if (!entry.isDirectory()) {
            try {
                final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                if (bt.isJarEntryType()) {
                    if (!isEmpty(entry)) {
                        logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                        is = fileToProcess.getInputStream(entry);
                        switch (bt) {
                        case CLASS:
                            processClassFile(is);
                            break;
                        case WAR:
                            processWarFile(is);
                            break;
                        case JAR:
                            processJarFile(is);
                            break;
                        case CBA:
                            processCbaFile(is);
                            break;
                        case RAR:
                            processRarFile(is);
                            break;
                        default:
                        }
                    }
                }
            } 
            catch (IOException e) {
                logger.severe(() -> formatMessage("ArchiveReadError", jarType.getExtension(), e.getMessage()));
            }
        }
    }

    private void processJarFile(InputStream is) throws IOException {
        InputStream jeis = null;
        try {
            // Need to use the JarInputStream constructor that takes a boolean to turn off signature verification. Otherwise, jar files that contain invalid
            // manifest signatures cannot be scanned.
            final JarInputStream jis = new JarInputStream(is, false);
            JarEntry je = null;
            while ((je = jis.getNextJarEntry()) != null) {
                final String entryName = je.getName();
                final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
                if (!je.isDirectory()) {
                    try {
                        final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                        if (bt.isJarInJarEntryType()) {
                            jeis = readInputStream(jis);
                            if (!isNull(jeis)) {
                                logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                                switch (bt) {
                                case JAR:
                                    processJarFile(jeis);
                                    break;
                                case CLASS:
                                    processClassFile(jeis);
                                    break;
                                default:
                                }
                            }
                        }
                    } 
                    catch (IOException e) {
                        logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.JAR.getExtension(), e.getMessage()));
                    } 
                    finally {
                        if (jeis != null) {
                            jeis.close();
                            jeis = null;
                        }
                    }
                }
                jis.closeEntry();
            }
        } 
        finally {
            if (jeis != null) {
                jeis.close();
            }
        }
    }

    private void processZipFile(File thisBinaryFile) throws IOException {
        final ZipFile fileToProcess = new ZipFile(thisBinaryFile.getAbsoluteFile());
        try {
            final Enumeration<? extends ZipEntry> entries = fileToProcess.entries();
            while (entries.hasMoreElements()) {
                final ZipEntry entry = entries.nextElement();
                processZipEntry(fileToProcess, entry);
            }
        }
        catch (Exception e) {
            logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.ZIP.getExtension(), e.getMessage()));
        } 
        finally {
            try {
                fileToProcess.close();
            } 
            catch (IOException e) {
                logger.severe(() -> formatMessage("ArchiveCloseError", BinaryType.ZIP.getExtension(), e.getMessage()));
            }
        }
    }

    private void processZipEntry(ZipFile fileToProcess, ZipEntry entry) {
        InputStream is = null;
        final String entryName = entry.getName();
        final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
        if (!entry.isDirectory()) {
            try {
                final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                if (bt.isZipEntryType()) {
                    if (!isEmpty(entry)) {
                        logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                        is = fileToProcess.getInputStream(entry);
                        switch (bt) {
                        case CLASS:
                            processClassFile(is);
                            break;
                        case WAR:
                            processWarFile(is);
                            break;
                        case JAR:
                            processJarFile(is);
                            break;
                        case EAR:
                            processEarFile(is);
                            break;
                        case EBA:
                            processEbaFile(is);
                            break;
                        case RAR:
                            processRarFile(is);
                            break;
                        default:
                        }
                    }
                }
            }
            catch (IOException e) {
                logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.ZIP.getExtension(), e.getMessage()));
            }
        }
    }

    private void processWarFile(InputStream is) throws IOException {
        InputStream jeis = null;
        try {
            // Need to use the JarInputStream constructor that takes a boolean to turn off signature verification. Otherwise, jar files that contain invalid
            // manifest signatures cannot be scanned.
            final JarInputStream jis = new JarInputStream(is, false);
            JarEntry je = null;
            while ((je = jis.getNextJarEntry()) != null) {
                final String entryName = je.getName();
                final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
                if (!je.isDirectory()) {
                    try {
                        final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                        if (bt.isWarEntryType()) {
                            jeis = readInputStream(jis);
                            if (!isNull(jeis)) {
                                logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                                switch (bt) {
                                case CLASS:
                                    processClassFile(jeis);
                                    break;
                                case JAR:
                                    processJarFile(jeis);
                                    break;
                                default:
                                }
                            }
                        }
                    } 
                    catch (IOException e) {
                        logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.WAR.getExtension(), e.getMessage()));
                    } 
                    finally {
                        if (jeis != null) {
                            jeis.close();
                            jeis = null;
                        }
                    }
                }
                jis.closeEntry();
            }
        } 
        finally {
            if (jeis != null) {
                jeis.close();
            }
        }
    }

    private void processEarFile(InputStream is) throws IOException {
        InputStream jeis = null;
        try {
            // Need to use the JarInputStream constructor that takes a boolean to turn off signature verification. Otherwise, jar files that contain invalid
            // manifest signatures cannot be scanned.
            final JarInputStream jis = new JarInputStream(is, false);
            JarEntry je = null;
            while ((je = jis.getNextJarEntry()) != null) {
                final String entryName = je.getName();
                final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
                if (!je.isDirectory()) {
                    try {
                        final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                        if (bt.isEarEntryType()) {
                            jeis = readInputStream(jis);
                            if (!isNull(jeis)) {
                                logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                                switch (bt) {
                                case WAR:
                                    processWarFile(jeis);
                                    break;
                                case JAR:
                                    processJarFile(jeis);
                                    break;
                                case CLASS:
                                    processClassFile(jeis);
                                    break;
                                default:
                                }
                            }
                        }
                    } 
                    catch (IOException e) {
                        logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.EAR.getExtension(), e.getMessage()));
                    } 
                    finally {
                        if (jeis != null) {
                            jeis.close();
                            jeis = null;
                        }
                    }
                }
                jis.closeEntry();
            }
        } 
        finally {
            if (jeis != null) {
                jeis.close();
            }
        }
    }

    private void processCbaFile(InputStream is) throws IOException {
        InputStream jeis = null;
        try {
            // Need to use the JarInputStream constructor that takes a boolean to turn off signature verification. Otherwise, jar files that contain invalid
            // manifest signatures cannot be scanned.
            final JarInputStream jis = new JarInputStream(is, false);
            JarEntry je = null;
            while ((je = jis.getNextJarEntry()) != null) {
                final String entryName = je.getName();
                final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
                if (!je.isDirectory()) {
                    try {
                        final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                        if (bt.isCbaEntryType()) {
                            jeis = readInputStream(jis);
                            if (!isNull(jeis)) {
                                logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                                switch (bt) {
                                case JAR:
                                    processJarFile(jeis);
                                    break;
                                default:
                                }
                            }
                        }
                    } 
                    catch (IOException e) {
                        logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.CBA.getExtension(), e.getMessage()));
                    } 
                    finally {
                        if (jeis != null) {
                            jeis.close();
                            jeis = null;
                        }
                    }
                }
                jis.closeEntry();
            }
        } 
        finally {
            if (jeis != null) {
                jeis.close();
            }
        }
    }

    private void processEbaFile(InputStream is) throws IOException {
        InputStream jeis = null;
        try {
            // Need to use the JarInputStream constructor that takes a boolean to turn off signature verification. Otherwise, jar files that contain invalid
            // manifest signatures cannot be scanned.
            final JarInputStream jis = new JarInputStream(is, false);
            JarEntry je = null;
            while ((je = jis.getNextJarEntry()) != null) {
                final String entryName = je.getName();
                final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
                if (!je.isDirectory()) {
                    try {
                        final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                        if (bt.isEbaEntryType()) {
                            jeis = readInputStream(jis);
                            if (!isNull(jeis)) {
                                logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                                switch (bt) {
                                case WAR:
                                    processWarFile(jeis);
                                    break;
                                case CBA:
                                    processCbaFile(jeis);
                                    break;
                                case JAR:
                                    processJarFile(jeis);
                                    break;
                                default:
                                }
                            }
                        }
                    } 
                    catch (IOException e) {
                        logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.EBA.getExtension(), e.getMessage()));
                    } 
                    finally {
                        if (jeis != null) {
                            jeis.close();
                            jeis = null;
                        }
                    }
                }
                jis.closeEntry();
            }
        } 
        finally {
            if (jeis != null) {
                jeis.close();
            }
        }
    }

    private void processRarFile(InputStream is) throws IOException {
        InputStream jeis = null;
        try {
            // Need to use the JarInputStream constructor that takes a boolean to turn off signature verification. Otherwise, jar files that contain invalid
            // manifest signatures cannot be scanned.
            final JarInputStream jis = new JarInputStream(is, false);
            JarEntry je = null;
            while ((je = jis.getNextJarEntry()) != null) {
                final String entryName = je.getName();
                final String lowerEntryName = entryName.toLowerCase(Locale.ENGLISH);
                if (!je.isDirectory()) {
                    try {
                        final BinaryType bt = BinaryType.getBinaryType(lowerEntryName);
                        if (bt.isRarEntryType()) {
                            jeis = readInputStream(jis);
                            if (!isNull(jeis)) {
                                logger.finest(() -> formatMessage("ArchiveReadFile", entryName));
                                switch (bt) {
                                case CLASS:
                                    processClassFile(jeis);
                                    break;
                                case JAR:
                                    processJarFile(jeis);
                                    break;
                                default:
                                }
                            }
                        }
                    } 
                    catch (IOException e) {
                        logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.RAR.getExtension(), e.getMessage()));
                    } 
                    finally {
                        if (jeis != null) {
                            jeis.close();
                            jeis = null;
                        }
                    }
                }
                jis.closeEntry();
            }
        } 
        finally {
            if (jeis != null) {
                jeis.close();
            }
        }
    }

    private void processClassFile(File fileToProcess) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(fileToProcess);
            processClassFile(fis);
        } 
        catch (IOException e) {
            logger.severe(() -> formatMessage("ArchiveReadError", BinaryType.CLASS.getExtension(), e.getMessage()));
        } 
        finally {
            try {
                if (fis != null) {
                    fis.close();
                }
            } 
            catch (IOException e) {
                logger.severe(() -> formatMessage("ArchiveCloseError", BinaryType.CLASS.getExtension(), e.getMessage()));
            }
        }
    }

    private void processClassFile(InputStream fileToProcessStream) throws IOException {
        final byte[] bytes = toByteArray(fileToProcessStream);
        final CtClass ctClass = toCtClass(bytes);
        final ClassProcessor cp = new ClassProcessor(ctClass);
        ap.process(cp, bytes);
    }

    private boolean isEmpty(ZipEntry entry) {
        return entry.getSize() == 0;
    }

    private boolean isNull(InputStream is) {
        return is == null;
    }

    private InputStream readInputStream(InputStream is) throws IOException {
        final byte[] bytes = toByteArray(is);
        if (bytes != null) {
            return new ByteArrayInputStream(bytes);
        }
        return null;
    }

    private CtClass toCtClass(byte[] bytes) throws IOException {
        Desc.useContextClassLoader = true;
        final ClassPool classPool = new ScopedClassPoolFactoryImpl().create(Analyzer.class.getClassLoader(),
                ClassPool.getDefault(), ScopedClassPoolRepositoryImpl.getInstance());
        return classPool.makeClass(new ByteArrayInputStream(bytes));
    }

    private byte[] toByteArray(InputStream is) throws IOException {
        byte[] contents = null;
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            while (true) {
                final int nextByte = is.read();
                if (nextByte == -1) {
                    break;
                }
                baos.write(nextByte);
            }
            contents = baos.toByteArray();
            if (contents.length != 0) {
                return contents;
            }
        } 
        finally {
            if (baos != null) {
                baos.close();
            }
        }
        return null;
    }
}
