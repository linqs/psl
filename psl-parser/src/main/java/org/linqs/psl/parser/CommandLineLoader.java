/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.parser;

import org.linqs.psl.application.inference.mpe.ADMMInference;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.Config;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.SystemUtils;
import org.linqs.psl.util.Version;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileReader;
import java.io.IOException;
import java.net.UnknownHostException;
import java.net.InetAddress;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.Properties;
import java.util.Map;

/**
 * Load the command line options into PSL Config's configuration values
 * and log4j configuration appropriately.
 */
public class CommandLineLoader {
    // Command line options.
    public static final String OPTION_HELP = "h";
    public static final String OPTION_HELP_LONG = "help";
    public static final String OPERATION_INFER = "i";
    public static final String OPERATION_INFER_LONG = "infer";
    public static final String OPERATION_LEARN = "l";
    public static final String OPERATION_LEARN_LONG = "learn";

    public static final String OPTION_DATA = "d";
    public static final String OPTION_DATA_LONG = "data";
    public static final String OPTION_DB_H2_PATH = "h2path";
    public static final String OPTION_DB_POSTGRESQL_NAME = "postgres";
    public static final String OPTION_EVAL = "e";
    public static final String OPTION_EVAL_LONG = "eval";
    public static final String OPTION_INT_IDS = "int";
    public static final String OPTION_INT_IDS_LONG = "int-ids";
    public static final String OPTION_LOG4J = "4j";
    public static final String OPTION_LOG4J_LONG = "log4j";
    public static final String OPTION_MODEL = "m";
    public static final String OPTION_MODEL_LONG = "model";
    public static final String OPTION_OUTPUT_DIR = "o";
    public static final String OPTION_OUTPUT_DIR_LONG = "output";
    public static final String OPTION_OUTPUT_GROUND_RULES_LONG = "groundrules";
    public static final String OPTION_OUTPUT_SATISFACTION_LONG = "satisfaction";
    public static final String OPTION_PROPERTIES = "D";
    public static final String OPTION_PROPERTIES_FILE = "p";
    public static final String OPTION_PROPERTIES_FILE_LONG = "properties";
    public static final String OPTION_SKIP_ATOM_COMMIT_LONG = "skipAtomCommit";
    public static final String OPTION_VERSION = "v";
    public static final String OPTION_VERSION_LONG = "version";

    public static final String DEFAULT_H2_DB_PATH = SystemUtils.getTempDir("cli");
    public static final String DEFAULT_POSTGRES_DB_NAME = "psl_cli";
    public static final String DEFAULT_IA = ADMMInference.class.getName();
    public static final String DEFAULT_WLA = MaxLikelihoodMPE.class.getName();

    private static Options options = setupOptions();

    private CommandLine parsedOptions;

    public CommandLineLoader(String[] args) {
        try {
            parsedOptions = parseOptions(args);
            if (parsedOptions == null) {
                return;
            }
        } catch (Exception ex) {
            System.err.println("Unexpected exception!");
            ex.printStackTrace(System.err);
        }

        initLogger();
        initConfig();
    }

    /**
     * Returns the supported Options object.
     */
    public static Options getOptions() {
        return options;
    }

    /**
     * Returns the parsedOptions object.
     */
    public CommandLine getParsedOptions() {
        return parsedOptions;
    }

    /**
     * Initializes logging.
     */
    private void initLogger() {
        Properties props = new Properties();

        if (parsedOptions.hasOption(OPTION_LOG4J)) {
            try {
                props.load(FileUtils.getInputStreamReader(parsedOptions.getOptionValue(OPTION_LOG4J)));
            } catch (IOException ex) {
                throw new RuntimeException("Failed to read logger configuration from a file.", ex);
            }
        } else {
            // Setup a default logger.
            props.setProperty("log4j.rootLogger", "INFO, A1");
            props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
            props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
            props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");
        }

        // Load any options specified directly on the command line (override standing options).
        for (Map.Entry<Object, Object> entry : parsedOptions.getOptionProperties("D").entrySet()) {
            String key = entry.getKey().toString();

            if (!key.startsWith("log4j.")) {
                continue;
            }

            props.setProperty(key, entry.getValue().toString());
        }

        // Log4j is pretty picky about it's thresholds, so we will specially set one option.
        if (props.containsKey("log4j.threshold")) {
            props.setProperty("log4j.rootLogger", props.getProperty("log4j.threshold") + ", A1");
        }

        // Some deps use java.util.logging, so we will silence their loggers.
        java.util.logging.Logger rootJavaLogger = java.util.logging.LogManager.getLogManager().getLogger("");
        rootJavaLogger.setLevel(java.util.logging.Level.SEVERE);
        for (java.util.logging.Handler handler : rootJavaLogger.getHandlers()) {
            handler.setLevel(java.util.logging.Level.SEVERE);
        }

        PropertyConfigurator.configure(props);
    }

    /**
     * Initialize log4j with a default logger.
     * Only to be used with short CLI runs: --version or --help.
     */
    private static void initDefaultLogger() {
        Properties props = new Properties();
        props.setProperty("log4j.rootLogger", "INFO, A1");
        props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
        props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");
        PropertyConfigurator.configure(props);
    }

    /**
     * Loads configuration.
     */
    private void initConfig() {
        // Load a properties file that was specified on the command line.
        if (parsedOptions.hasOption(OPTION_PROPERTIES_FILE)) {
            String propertiesPath = parsedOptions.getOptionValue(OPTION_PROPERTIES_FILE);
            Config.loadResource(propertiesPath);
        }

        // Load any options specified directly on the command line (override standing options).
        for (Map.Entry<Object, Object> entry : parsedOptions.getOptionProperties("D").entrySet()) {
            String key = entry.getKey().toString();
            Config.setProperty(key, entry.getValue());
        }
    }

    private static Options setupOptions() {
        Options newOptions = new Options();

        newOptions.addOption(Option.builder(OPERATION_INFER)
                .longOpt(OPERATION_INFER_LONG)
                .desc("Run MAP inference." +
                        " You can optionally supply a name for an inference application" +
                        " (defaults to " + DEFAULT_IA + ").")
                .hasArg()
                .argName("inferenceMethod")
                .optionalArg(true)
                .build());

        newOptions.addOption(Option.builder(OPERATION_LEARN)
                .longOpt(OPERATION_LEARN_LONG)
                .desc("Run weight learning." +
                        " You can optionally supply a name for a weight learner" +
                        " (defaults to " + DEFAULT_WLA + ").")
                .hasArg()
                .argName("learner")
                .optionalArg(true)
                .build());

        // Make sure that help and version are in the main group so a successful run can use them.
        newOptions.addOption(Option.builder(OPTION_HELP)
                .longOpt(OPTION_HELP_LONG)
                .desc("Print this help message and exit")
                .build());

        newOptions.addOption(Option.builder(OPTION_VERSION)
                .longOpt(OPTION_VERSION_LONG)
                .desc("Print the PSL version and exit")
                .build());

        newOptions.addOption(Option.builder(OPTION_DATA)
                .longOpt(OPTION_DATA_LONG)
                .desc("Path to PSL data file")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_DB_H2_PATH)
                .desc("Path for H2 database file (defaults to 'cli_<user name>@<host name>' ('" + DEFAULT_H2_DB_PATH + "'))." +
                        " Not compatible with the '--" + OPTION_DB_POSTGRESQL_NAME + "' option.")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_DB_POSTGRESQL_NAME)
                .desc("Name for the PostgreSQL database to use (defaults to " + DEFAULT_POSTGRES_DB_NAME + ")." +
                        " Not compatible with the '--" + OPTION_DB_H2_PATH + "' option." +
                        " Currently only local databases without credentials are supported.")
                .hasArg()
                .argName("name")
                .optionalArg(true)
                .build());

        newOptions.addOption(Option.builder(OPTION_EVAL)
                .longOpt(OPTION_EVAL_LONG)
                .desc("Run the named evaluator (" + Evaluator.class.getName() + ") on any open predicate with a 'truth' partition." +
                        " If multiple evaluators are specific, they will each be run.")
                .hasArgs()
                .argName("evaluator ...")
                .build());

        newOptions.addOption(Option.builder(OPTION_INT_IDS)
                .longOpt(OPTION_INT_IDS_LONG)
                .desc("Use integer identifiers (UniqueIntID) instead of string identifiers (UniqueStringID).")
                .build());

        newOptions.addOption(Option.builder(OPTION_LOG4J)
                .longOpt(OPTION_LOG4J_LONG)
                .desc("Optional log4j properties file path")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder(OPTION_MODEL)
                .longOpt(OPTION_MODEL_LONG)
                .desc("Path to PSL model file")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder(OPTION_OUTPUT_DIR)
                .longOpt(OPTION_OUTPUT_DIR_LONG)
                .desc("Optional path for writing results to filesystem (default is STDOUT)")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_OUTPUT_GROUND_RULES_LONG)
                .desc("Output the program's ground rules." +
                        " If a path is specified, the ground rules will be output there." +
                        " Otherwise, they will be output to stdout (not the logger).")
                .hasArg()
                .argName("path")
                .optionalArg(true)
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_OUTPUT_SATISFACTION_LONG)
                .desc("Output the program's ground rules along with their satisfaction values after inference." +
                        " If a path is specified, the ground rules will be output there." +
                        " Otherwise, they will be output to stdout (not the logger).")
                .hasArg()
                .argName("path")
                .optionalArg(true)
                .build());

        newOptions.addOption(Option.builder(OPTION_PROPERTIES_FILE)
                .longOpt(OPTION_PROPERTIES_FILE_LONG)
                .desc("Optional PSL properties file path")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder(OPTION_PROPERTIES)
                .argName("name=value")
                .desc("Directly specify PSL properties (overrides options set via --" + OPTION_PROPERTIES_FILE_LONG + ")." +
                        " See https://github.com/linqs/psl/wiki/Configuration-Options for a list of available options." +
                        " Log4j properties (properties starting with 'log4j') will be passed to the logger." +
                        " 'log4j.threshold=DEBUG', for example, will be passed to log4j and set the global logging threshold.")
                .hasArg()
                .numberOfArgs(2)
                .valueSeparator('=')
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_SKIP_ATOM_COMMIT_LONG)
                .desc("Skip persisting atoms to database after inference.")
                .optionalArg(true)
                .build());

        return newOptions;
    }

    private static HelpFormatter getHelpFormatter() {
        HelpFormatter helpFormatter = new HelpFormatter();

        // Hack the option ordering to put argumentions without options first and then required options first.
        // infer and learn go first, then required, then just normal.
        helpFormatter.setOptionComparator(new Comparator<Option>() {
            @Override
            public int compare(Option o1, Option o2) {
                String name1 = o1.getOpt();
                if (name1 == null) {
                    name1 = o1.getLongOpt();
                }

                String name2 = o2.getOpt();
                if (name2 == null) {
                    name2 = o2.getLongOpt();
                }

                if (name1.equals(OPERATION_INFER)) {
                    return -1;
                }

                if (name2.equals(OPERATION_INFER)) {
                    return 1;
                }

                if (name1.equals(OPERATION_LEARN)) {
                    return -1;
                }

                if (name2.equals(OPERATION_LEARN)) {
                    return 1;
                }

                if (o1.isRequired() && !o2.isRequired()) {
                    return -1;
                }

                if (!o1.isRequired() && o2.isRequired()) {
                    return 1;
                }

                return name1.compareTo(name2);
            }
        });

        helpFormatter.setWidth(100);

        return helpFormatter;
    }

    /**
     * Parse the options on the command line.
     * Will return null for errors or if the CLI should not be run
     * (like if we are doing a help/version run).
     */
    private static CommandLine parseOptions(String[] args) {
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLineOptions = null;

        try {
            commandLineOptions = parser.parse(options, args);
        } catch (ParseException ex) {
            System.err.println("Command line error: " + ex.getMessage());
            getHelpFormatter().printHelp("psl", options, true);
            return null;
        }

        if (commandLineOptions.hasOption(OPTION_HELP)) {
            initDefaultLogger();
            getHelpFormatter().printHelp("psl", options, true);
            return commandLineOptions;
        }

        if (commandLineOptions.hasOption(OPTION_VERSION)) {
            initDefaultLogger();
            System.out.println("PSL Version " + Version.getFull());
            return commandLineOptions;
        }

        // Can't have both an H2 and Postgres database.
        if (commandLineOptions.hasOption(OPTION_DB_H2_PATH) && commandLineOptions.hasOption(OPTION_DB_POSTGRESQL_NAME)) {
            System.err.println("Command line error: Options '--" + OPTION_DB_H2_PATH + "' and '--" + OPTION_DB_POSTGRESQL_NAME + "' are not compatible.");
            getHelpFormatter().printHelp("psl", options, true);
        }

        return commandLineOptions;
    }
}
