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
package org.linqs.psl.test;

import org.linqs.psl.model.rule.AbstractRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * The base test that all PSL core tests derive from.
 * This base ensures that all PSL resources get properly cleaned up after a test completes.
 */
public abstract class PSLBaseTest {
    @Before
    public void pslBaseSetup() {
    }

    @After
    public void pslBaseCleanup() {
        // Remove any defined rules.
        AbstractRule.unregisterAllRulesForTesting();
    }
}
