/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl;

import org.apache.log4j.PropertyConfigurator;

import java.util.Properties;

/**
 * Utilities for testing PSL.
 */
public class PSLTest {
    // Init a defualt logger with the given level.
    public static void initLogger(String logLevel) {
        Properties props = new Properties();

        props.setProperty("log4j.rootLogger", String.format("%s, A1", logLevel));
        props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
        props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");

        PropertyConfigurator.configure(props);
    }

    // Init with the default logging level: DEBUG.
    public static void initLogger() {
        initLogger("DEBUG");
    }

    public static void disableLogger() {
        initLogger("OFF");
    }
}
