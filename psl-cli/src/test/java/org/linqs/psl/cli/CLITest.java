/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.model.predicate.Predicate;

import org.junit.After;
import org.junit.Before;

import java.io.File;
import java.nio.file.Paths;

/**
 * The base class for tests that run a PSL model via the CLI.
 */
public abstract class CLITest {
	public static final String PREFIX = "cli_test";
	public static final String RESOURCES_BASE_FILE = ".resources";
	public static final String BASE_DATA_DIR_NAME = "data";
	public static final String BASE_MODELS_DIR_NAME = "models";

	protected final String outDir;
	protected final String resourceDir;
	protected final String baseDataDir;
	protected final String baseModelsDir;

	public CLITest() {
		outDir = Paths.get(System.getProperty("java.io.tmpdir"), PREFIX + "_" + this.getClass().getName()).toString();

		resourceDir = (new File(this.getClass().getClassLoader().getResource(RESOURCES_BASE_FILE).getFile())).getParentFile().getAbsolutePath();
		baseDataDir = Paths.get(resourceDir, BASE_DATA_DIR_NAME).toString();
		baseModelsDir = Paths.get(resourceDir, BASE_MODELS_DIR_NAME).toString();
	}

	@Before
	public void setUp() {
		(new File(outDir)).mkdirs();
	}

	@After
	public void tearDown() {
		(new File(outDir)).delete();
		Predicate.clearForTesting();
	}

	public void run(String modelPath, String dataPath) {
		run(modelPath, dataPath, "OFF");
	}

	public void run(String modelPath, String dataPath, String loggingLevel) {
		String[] args = {
			"--" + Launcher.OPERATION_INFER_LONG,
			"--" + Launcher.OPTION_MODEL_LONG, modelPath,
			"--" + Launcher.OPTION_DATA_LONG, dataPath,
			"--" + Launcher.OPTION_OUTPUT_DIR_LONG, outDir,

			// Set the logging level.
			"-" + Launcher.OPTION_PROPERTIES, "log4j.threshold=" + loggingLevel
		};

		Launcher.main(args, true);

		validate();
	}

	/**
	 * Children should override this if they want to do specific validations.
	 */
	public void validate() {
		// The base implementation does nothing.
	}
}
