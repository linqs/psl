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

import static org.junit.Assert.fail;

import org.linqs.psl.config.RuntimeOptions;

import org.junit.Test;

public class MiscTest extends RuntimeTest {
    @Test
    public void testHelpOptions() {
        RuntimeOptions.HELP.set(true);
        run(new RuntimeConfig());
    }

    @Test
    public void testHelpConfig() {
        RuntimeConfig config = new RuntimeConfig();
        config.options.put(RuntimeOptions.HELP.name(), "true");

        run(config);
    }

    @Test
    public void testVersionOptions() {
        RuntimeOptions.VERSION.set(true);
        run(new RuntimeConfig());
    }

    @Test
    public void testVersionConfig() {
        RuntimeConfig config = new RuntimeConfig();
        config.options.put(RuntimeOptions.VERSION.name(), "true");

        run(config);
    }
}
