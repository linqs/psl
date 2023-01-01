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

import org.linqs.psl.config.Options;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.util.Logger;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * The base class for tests that run a PSL model via the CLI.
 */
public abstract class CLITest {
    public static final String PREFIX = "cli_test";
    public static final String RESOURCES_BASE_FILE = ".resources";
    public static final String RUNTIME_DIR_NAME = "runtime";

    protected final String outDir;
    protected final String resourceDir;
    protected final String runtimeDir;

    public CLITest() {
        outDir = Paths.get(System.getProperty("java.io.tmpdir"), PREFIX + "_" + this.getClass().getName()).toString();
        resourceDir = (new File(this.getClass().getClassLoader().getResource(RESOURCES_BASE_FILE).getFile())).getParentFile().getAbsolutePath();
        runtimeDir = Paths.get(resourceDir, RUNTIME_DIR_NAME).toString();
    }

    @Before
    public void setUp() {
        (new File(outDir)).mkdirs();
        Options.clearAll();

        Logger.setLevel("OFF");
        RuntimeOptions.INFERENCE_OUTPUT_RESULTS_DIR.set(outDir);
    }

    @After
    public void tearDown() {
        (new File(outDir)).delete();
        Logger.setLevel("OFF");
        Predicate.clearForTesting();
        AbstractRule.unregisterAllRulesForTesting();
        Options.clearAll();
    }

    public void run(String configPath) {
        run(configPath, null);
    }

    public void run(String configPath, List<String> additionalArgs) {
        run(configPath, "OFF", additionalArgs);
    }

    public void run(String configPath, String loggingLevel, List<String> additionalArgs) {
        List<String> args = new ArrayList<String>();
        args.add("--" + CommandLineLoader.OPERATION_INFER_LONG);

        args.add("--" + CommandLineLoader.OPTION_CONFIG_LONG);
        args.add(configPath);

        args.add("--" + CommandLineLoader.OPTION_OUTPUT_DIR_LONG);
        args.add(outDir);

        args.add("--" + CommandLineLoader.OPTION_LOG_LONG);
        args.add(loggingLevel);

        if (additionalArgs != null) {
            args.addAll(additionalArgs);
        }

        Launcher.main(args.toArray(new String[0]), true);

        validate();
    }

    /**
     * Children should override this if they want to do specific validations.
     */
    public void validate() {
        // The base implementation does nothing.
    }
}
