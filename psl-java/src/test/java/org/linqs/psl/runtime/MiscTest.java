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

import static org.junit.Assert.fail;

import org.linqs.psl.config.RuntimeOptions;

import org.junit.Test;

public class MiscTest extends RuntimeTest {
    @Test
    public void testHelp() {
        RuntimeOptions.HELP.set(true);
        run();
    }

    @Test
    public void testVersion() {
        RuntimeOptions.VERSION.set(true);
        run();
    }

    @Test
    public void testInferAndLearn() {
        RuntimeOptions.INFERENCE.set(true);
        RuntimeOptions.LEARN.set(true);

        try {
            run();
            fail("Error not thrown when both inference and learning is enabled.");
        } catch (IllegalStateException ex) {
            // Expected.
        }
    }
}
