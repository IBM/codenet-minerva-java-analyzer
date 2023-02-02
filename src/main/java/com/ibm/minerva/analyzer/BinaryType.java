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

import java.util.EnumSet;

public enum BinaryType {
    CBA(".cba"),
    CLASS(".class"),
    EAR(".ear"),
    EBA(".eba"),
    JAR(".jar"),
    RAR(".rar"),
    WAR(".war"),
    ZIP(".zip"),
    UNKNOWN(null);

    private static final EnumSet<BinaryType> cbaEntries = EnumSet.of(JAR);
    private static final EnumSet<BinaryType> earEntries = EnumSet.of(CLASS, JAR, WAR);
    private static final EnumSet<BinaryType> ebaEntries = EnumSet.of(CBA, JAR, WAR);
    private static final EnumSet<BinaryType> jarEntries = EnumSet.of(CBA, CLASS, JAR, RAR, WAR);
    private static final EnumSet<BinaryType> jarInJarEntries = EnumSet.of(CLASS, JAR);
    private static final EnumSet<BinaryType> zipEntries = EnumSet.of(CLASS, EBA, EAR, JAR, RAR, WAR);

    private final String extension;

    private BinaryType(String extension) {
        this.extension = extension;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isCbaEntryType() {
        return cbaEntries.contains(this);
    }

    public boolean isEarEntryType() {
        return earEntries.contains(this);
    }

    public boolean isEbaEntryType() {
        return ebaEntries.contains(this);
    }

    public boolean isJarEntryType() {
        return jarEntries.contains(this);
    }

    public boolean isJarInJarEntryType() {
        return jarInJarEntries.contains(this);
    }

    public boolean isRarEntryType() {
        return jarEntries.contains(this);
    }

    public boolean isWarEntryType() {
        return jarEntries.contains(this);
    }

    public boolean isZipEntryType() {
        return zipEntries.contains(this);
    }

    public static BinaryType getBinaryType(String name) {
        if (name.endsWith(JAR.extension)) {
            return JAR;
        } 
        else if (name.endsWith(WAR.extension)) {
            return WAR;
        } 
        else if (name.endsWith(EAR.extension)) {
            return EAR;
        } 
        else if (name.endsWith(RAR.extension)) {
            return RAR;
        } 
        else if (name.endsWith(ZIP.extension)) {
            return ZIP;
        } 
        else if (name.endsWith(EBA.extension)) {
            return EBA;
        } 
        else if (name.endsWith(CBA.extension)) {
            return CBA;
        } 
        else if (name.endsWith(CLASS.extension)) {
            return CLASS;
        }
        return UNKNOWN;
    }
}
