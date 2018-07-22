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

import org.junit.Test;

import java.nio.file.Paths;

public class SimpleAcquaintancesTest extends CLITest {
	@Test
	public void testBase() {
		String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
		String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base.data").toString();

		run(modelPath, dataPath);
	}

	@Test
	public void testTypes() {
		String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
		String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base_types.data").toString();

		run(modelPath, dataPath);
	}

	@Test
	public void testMixedTypes() {
		String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
		String dataPath = Paths.get(baseDataDir, "simple-acquaintances-mixed", "base.data").toString();

		run(modelPath, dataPath);
	}

	@Test
	public void testBlock() {
		String modelPath = Paths.get(baseModelsDir, "simple-acquaintances.psl").toString();
		String dataPath = Paths.get(baseDataDir, "simple-acquaintances", "base_block.data").toString();

		run(modelPath, dataPath);
	}
}
