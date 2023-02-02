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

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public final class LoggingUtil {

    private static volatile Level LOG_LEVEL = Level.INFO;
    private static final Set<Logger> LOGGERS = Collections.synchronizedSet(new HashSet<>());
    private static final DateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    private LoggingUtil() {}

    public static Logger getLogger(Class<?> cls) {
        Logger logger = Logger.getLogger(cls.getName());
        Handler handler = new ConsoleHandler();
        handler.setFormatter(new Formatter() {
            @Override
            public String format(LogRecord record) {
                // Format: [yyyy-MM-dd HH:mm:ss] [LEVEL  ] [className methodName] Message
                final StringBuilder sb = new StringBuilder();
                final Level level = record.getLevel();
                if (level != Level.INFO) {
                    sb.append('[');
                    final boolean verbose = isVerboseLevel(level);
                    if (verbose) {
                        sb.append(DATE_FORMAT.format(new Date(record.getMillis())));
                        sb.append("] [");
                    }
                    final String levelName = level.getName();
                    sb.append(levelName);
                    // Adding some padding so that the level name field is always as long as 'WARNING'.
                    final int spaces = Level.WARNING.getName().length() - levelName.length();
                    for (int i = 0; i < spaces; ++i) {
                        sb.append(' ');
                    }
                    if (verbose) {
                        sb.append("] [");
                        sb.append(record.getSourceClassName());
                        sb.append(' ');
                        sb.append(record.getSourceMethodName());
                    }
                    sb.append("] ");
                }
                sb.append(record.getMessage());
                return String.format("%s%n", sb.toString());
            }
        });
        logger.addHandler(handler);
        logger.setLevel(LOG_LEVEL);
        logger.setUseParentHandlers(false);
        LOGGERS.add(logger);
        return logger;
    }

    private static boolean isVerboseLevel(Level level) {
        return level == Level.FINEST || level == Level.FINER || level == Level.FINE;
    }

    static void setLoggingLevel(Level level) {
        LOG_LEVEL = level;
        LOGGERS.forEach(x -> {
            x.setLevel(level);
        });
    }

    static Level getLoggingLevel() {
        return LOG_LEVEL;
    }
}