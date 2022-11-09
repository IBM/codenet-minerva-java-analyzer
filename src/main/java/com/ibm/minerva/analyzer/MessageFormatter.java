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

import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

public final class MessageFormatter {

    public static String formatMessage(String key, Object... args) {
        final ResourceBundle resourceBundle = ResourceBundle.getBundle("com.ibm.minerva.analyzer.messages");
        try {
            return formatMessage(resourceBundle, key, args);
        }
        catch (MissingResourceException mre) {
            return formatMessage(resourceBundle, "BadMessageKey", new Object[] {key});
        }
    }

    private static String formatMessage(ResourceBundle resourceBundle, String key, Object[] args) {
        String msg = resourceBundle.getString(key);
        if (args != null && args.length > 0) {
            try {
                msg = MessageFormat.format(msg, args);
            }
            catch (Exception e) {
                msg = resourceBundle.getString("FormatFailed") + " " + msg;
            }
        }
        return msg;
    }
}
