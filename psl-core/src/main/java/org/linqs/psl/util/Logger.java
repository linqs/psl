/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.util;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.LoggerContext;

/**
 * Act as both a logger and API for change logging properties.
 */
public class Logger {
    private org.apache.logging.log4j.Logger log;

    private Logger(org.apache.logging.log4j.Logger log) {
        this.log = log;
    }

    public void fatal(String message) {
        log.fatal(message);
    }

    public void fatal(String message, Object... params) {
        log.fatal(message, params);
    }

    public void error(String message) {
        log.error(message);
    }

    public void error(String message, Object... params) {
        log.error(message, params);
    }

    public void warn(String message) {
        log.warn(message);
    }

    public void warn(String message, Object... params) {
        log.warn(message, params);
    }

    public void info(String message) {
        log.info(message);
    }

    public void info(String message, Object... params) {
        log.info(message, params);
    }

    public void debug(String message) {
        log.debug(message);
    }

    public void debug(String message, Object... params) {
        log.debug(message, params);
    }

    public void trace(String message) {
        log.trace(message);
    }

    public void trace(String message, Object... params) {
        log.trace(message, params);
    }

    public boolean isDebugEnabled() {
        return log.isDebugEnabled();
    }

    public boolean isTraceEnabled() {
        return log.isTraceEnabled();
    }

    public org.apache.logging.log4j.Logger getInternalLogger() {
        return log;
    }

    public static Logger getLogger(Class<?> contextClass) {
        return new Logger(LogManager.getLogger(contextClass));
    }

    public static void setLevel(String level) {
        LoggerContext context = (LoggerContext)LogManager.getContext(false);
        Configuration config = context.getConfiguration();
        LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
        loggerConfig.setLevel(Level.getLevel(level.toUpperCase()));
        context.updateLoggers();
    }
}
