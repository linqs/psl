/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.runtime;

import org.linqs.psl.config.Options;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.util.Logger;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.nio.file.Paths;

/**
 * The base class for tests that run a PSL model via the CLI.
 */
public abstract class RuntimeTest {
    public static final String PREFIX = "runtime_test";
    public static final String RESOURCES_BASE_FILE = ".resources";
    public static final String BASE_DATA_DIR_NAME = "data";
    public static final String BASE_MODELS_DIR_NAME = "models";

    protected final String outDir;
    protected final String resourceDir;
    protected final String baseDataDir;
    protected final String baseModelsDir;

    public RuntimeTest() {
        outDir = Paths.get(System.getProperty("java.io.tmpdir"), PREFIX + "_" + this.getClass().getName()).toString();

        resourceDir = (new File(this.getClass().getClassLoader().getResource(RESOURCES_BASE_FILE).getFile())).getParentFile().getAbsolutePath();
        baseDataDir = Paths.get(resourceDir, BASE_DATA_DIR_NAME).toString();
        baseModelsDir = Paths.get(resourceDir, BASE_MODELS_DIR_NAME).toString();
    }

    @Before
    public void setUp() {
        (new File(outDir)).mkdirs();
        Options.clearAll();
    }

    @After
    public void tearDown() {
        (new File(outDir)).delete();
        Logger.setLevel("OFF");
        Predicate.clearForTesting();
        AbstractRule.unregisterAllRulesForTesting();
        Options.clearAll();
    }

    public void runInference(String modelPath, String dataPath) {
        runInference(modelPath, dataPath, "OFF");
    }

    public void runInference(String modelPath, String dataPath, String loggingLevel) {
        RuntimeOptions.INFERENCE.set(true);
        RuntimeOptions.INFERENCE_DATA_PATH.set(dataPath);
        RuntimeOptions.INFERENCE_MODEL_PATH.set(modelPath);
        RuntimeOptions.INFERENCE_OUTPUT_RESULTS_DIR.set(outDir);

        run(loggingLevel);
    }

    public void run(String loggingLevel) {
        RuntimeOptions.LOG_LEVEL.set(loggingLevel);
        run();
    }

    /**
     * Run assuming that all options have already been set in config.
     */
    public void run() {
        Runtime runtime = new Runtime();
        runtime.run();

        validate();
    }

    /**
     * Children should override this if they want to do specific validations.
     */
    public void validate() {
        // The base implementation does nothing.
    }
}
