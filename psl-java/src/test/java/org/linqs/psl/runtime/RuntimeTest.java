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

    protected final String outDir;
    protected final String resourceDir;

    public RuntimeTest() {
        outDir = Paths.get(System.getProperty("java.io.tmpdir"), PREFIX + "_" + this.getClass().getName()).toString();
        resourceDir = (new File(this.getClass().getClassLoader().getResource(RESOURCES_BASE_FILE).getFile())).getParentFile().getAbsolutePath();
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

    public void run(String path, String loggingLevel) {
        RuntimeOptions.LOG_LEVEL.set(loggingLevel);
        run(path);
    }

    public void run(RuntimeConfig config) {
        Runtime runtime = new Runtime();
        runtime.run(config);

        validate();
    }

    public void run(String path) {
        Runtime runtime = new Runtime();
        runtime.run(path);

        validate();
    }

    /**
     * Children should override this if they want to do specific validations.
     */
    public void validate() {
        // The base implementation does nothing.
    }
}
