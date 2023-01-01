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

import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.runtime.RuntimeConfig;
import org.linqs.psl.runtime.Runtime;
import org.linqs.psl.util.Logger;

import org.apache.commons.cli.CommandLine;

import java.util.Properties;

/**
 * Launches PSL from the command line.
 * Supports inference and supervised parameter learning.
 */
public class Launcher {
    private static final Logger log = Logger.getLogger(Launcher.class);
    private CommandLine parsedOptions;

    protected Launcher(CommandLine givenOptions) {
        this.parsedOptions = givenOptions;
    }

    /**
     * Convert all compatible options to the PSL runtime.
     */
    private RuntimeConfig convertRuntimeOptions() {
        RuntimeConfig config = null;

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_CONFIG)) {
            config = RuntimeConfig.fromFile(parsedOptions.getOptionValue(CommandLineLoader.OPTION_CONFIG));
        } else {
            config = new RuntimeConfig();
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_HELP)) {
            config.options.put(RuntimeOptions.HELP.name(), "" + true);
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_VERSION)) {
            config.options.put(RuntimeOptions.VERSION.name(), "" + true);
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPERATION_INFER)) {
            config.options.put(RuntimeOptions.INFERENCE.name(), "" + true);

            String method = parsedOptions.getOptionValue(CommandLineLoader.OPERATION_INFER);
            if (method != null) {
                config.options.put(RuntimeOptions.INFERENCE_METHOD.name(), method);
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPERATION_LEARN)) {
            config.options.put(RuntimeOptions.LEARN.name(), "" + true);

            String method = parsedOptions.getOptionValue(CommandLineLoader.OPERATION_LEARN);
            if (method != null) {
                config.options.put(RuntimeOptions.LEARN_METHOD.name(), method);
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_DB_H2_PATH)) {
            config.options.put(RuntimeOptions.DB_TYPE.name(), Runtime.DatabaseType.H2.toString());
            config.options.put(RuntimeOptions.DB_H2_PATH.name(), parsedOptions.getOptionValue(CommandLineLoader.OPTION_DB_H2_PATH));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_DB_POSTGRESQL_NAME)) {
            config.options.put(RuntimeOptions.DB_TYPE.name(), Runtime.DatabaseType.Postgres.toString());
            config.options.put(RuntimeOptions.DB_PG_NAME.name(), parsedOptions.getOptionValue(CommandLineLoader.OPTION_DB_POSTGRESQL_NAME));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_INT_IDS)) {
            config.options.put(RuntimeOptions.DB_INT_IDS.name(), "" + true);
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_PROPERTIES)) {
            Properties props = parsedOptions.getOptionProperties(CommandLineLoader.OPTION_PROPERTIES);
            for (String key : props.stringPropertyNames()) {
                config.options.put(key, props.getProperty(key));
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_OUTPUT_DIR)) {
            config.options.put(RuntimeOptions.INFERENCE_OUTPUT_RESULTS_DIR.name(), parsedOptions.getOptionValue(CommandLineLoader.OPTION_OUTPUT_DIR));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_OUTPUT_GROUND_RULES_LONG)) {
            config.options.put(RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES.name(), "" + true);

            String path = parsedOptions.getOptionValue(CommandLineLoader.OPTION_OUTPUT_GROUND_RULES_LONG);
            if (path != null) {
                config.options.put(RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES_PATH.name(), path);
            }
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_SKIP_ATOM_COMMIT_LONG)) {
            config.options.put(RuntimeOptions.INFERENCE_COMMIT.name(), "" + !parsedOptions.hasOption(CommandLineLoader.OPTION_SKIP_ATOM_COMMIT_LONG));
        }

        if (parsedOptions.hasOption(CommandLineLoader.OPTION_LOG_LONG)) {
            config.options.put(RuntimeOptions.LOG_LEVEL.name(), parsedOptions.getOptionValue(CommandLineLoader.OPTION_LOG_LONG));
        }

        return config;
    }

    private void run() {
        Runtime runtime = new Runtime();
        runtime.run(convertRuntimeOptions());
    }

    public static void main(String[] args) {
        main(args, false);
    }

    public static void main(String[] args, boolean rethrow) {
        try {
            CommandLineLoader commandLineLoader = new CommandLineLoader(args);
            CommandLine givenOptions = commandLineLoader.getParsedOptions();
            // Return for command line parse errors or PSL errors.
            if (givenOptions == null) {
                return;
            }
            Launcher pslLauncher = new Launcher(givenOptions);
            pslLauncher.run();
        } catch (Exception ex) {
            if (rethrow) {
                throw new RuntimeException("Failed to run CLI: " + ex.getMessage(), ex);
            } else {
                System.err.println("Unexpected exception!");
                ex.printStackTrace(System.err);
                System.exit(1);
            }
        }
    }
}
