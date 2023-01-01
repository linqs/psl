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
package org.linqs.psl.cli;

import org.linqs.psl.config.Config;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.util.Version;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.util.Comparator;
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

    public static final String OPTION_CONFIG = "c";
    public static final String OPTION_CONFIG_LONG = "config";
    public static final String OPTION_DB_H2_PATH = "h2path";
    public static final String OPTION_DB_POSTGRESQL_NAME = "postgres";
    public static final String OPTION_INT_IDS = "int";
    public static final String OPTION_INT_IDS_LONG = "int-ids";
    public static final String OPTION_LOG_LONG = "log";
    public static final String OPTION_OUTPUT_DIR = "o";
    public static final String OPTION_OUTPUT_DIR_LONG = "output";
    public static final String OPTION_OUTPUT_GROUND_RULES_LONG = "groundrules";
    public static final String OPTION_PROPERTIES = "D";
    public static final String OPTION_SKIP_ATOM_COMMIT_LONG = "skipAtomCommit";
    public static final String OPTION_VERSION = "v";
    public static final String OPTION_VERSION_LONG = "version";

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
     * Loads configuration.
     */
    private void initConfig() {
        // Load any options specified directly on the command line (override standing options).
        for (Map.Entry<Object, Object> entry : parsedOptions.getOptionProperties("D").entrySet()) {
            String key = entry.getKey().toString();
            Config.setProperty(key, entry.getValue(), true);
        }
    }

    private static Options setupOptions() {
        Options newOptions = new Options();

        newOptions.addOption(Option.builder(OPERATION_INFER)
                .longOpt(OPERATION_INFER_LONG)
                .desc("Run MAP inference." +
                        " You can optionally supply a name for an inference application" +
                        " (defaults to " + RuntimeOptions.INFERENCE_METHOD.defaultValue() + ").")
                .hasArg()
                .argName("inferenceMethod")
                .optionalArg(true)
                .build());

        newOptions.addOption(Option.builder(OPERATION_LEARN)
                .longOpt(OPERATION_LEARN_LONG)
                .desc("Run weight learning." +
                        " You can optionally supply a name for a weight learner" +
                        " (defaults to " + RuntimeOptions.LEARN_METHOD.defaultValue() + ").")
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

        newOptions.addOption(Option.builder(OPTION_CONFIG)
                .longOpt(OPTION_CONFIG_LONG)
                .desc("Path to PSL config file (JSON or YAML)")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_DB_H2_PATH)
                .desc("Path for H2 database file (defaults to 'cli_<user name>@<host name>' ('" + RuntimeOptions.DB_H2_PATH.defaultValue() + "'))." +
                        " Not compatible with the '--" + OPTION_DB_POSTGRESQL_NAME + "' option.")
                .hasArg()
                .argName("path")
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_DB_POSTGRESQL_NAME)
                .desc("Name for the PostgreSQL database to use (defaults to " + RuntimeOptions.DB_PG_NAME.defaultValue() + ")." +
                        " Not compatible with the '--" + OPTION_DB_H2_PATH + "' option." +
                        " Currently only local databases without credentials are supported.")
                .hasArg()
                .argName("name")
                .optionalArg(true)
                .build());

        newOptions.addOption(Option.builder(OPTION_INT_IDS)
                .longOpt(OPTION_INT_IDS_LONG)
                .desc("Use integer identifiers (UniqueIntID) instead of string identifiers (UniqueStringID).")
                .build());

        newOptions.addOption(Option.builder()
                .longOpt(OPTION_LOG_LONG)
                .desc("Set the logging level to one of (TRACE, DEBUG, INFO (default), WARN, ERROR, FATAL).")
                .hasArg()
                .argName("level")
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

        newOptions.addOption(Option.builder(OPTION_PROPERTIES)
                .argName("name=value")
                .desc("Directly specify PSL properties." +
                        " See https://github.com/linqs/psl/wiki/Configuration-Options for a list of available options.")
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
            getHelpFormatter().printHelp("psl", options, true);
            return null;
        }

        if (commandLineOptions.hasOption(OPTION_VERSION)) {
            System.out.println("PSL Version " + Version.getFull());
            return null;
        }

        // Can't have both an H2 and Postgres database.
        if (commandLineOptions.hasOption(OPTION_DB_H2_PATH) && commandLineOptions.hasOption(OPTION_DB_POSTGRESQL_NAME)) {
            System.err.println("Command line error: Options '--" + OPTION_DB_H2_PATH + "' and '--" + OPTION_DB_POSTGRESQL_NAME + "' are not compatible.");
            getHelpFormatter().printHelp("psl", options, true);
        }

        return commandLineOptions;
    }
}
