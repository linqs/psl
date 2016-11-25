/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package org.linqs.psl;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for testing PSL.
 */
public class PSLTest {
	public static void compareGroundRules(List<String> expected, Rule rule, GroundRuleStore store) {
		List<String> actual = new ArrayList<String>();
		for (GroundRule groundRule : store.getGroundKernels(rule)) {
			actual.add(groundRule.toString());
		}

		assertEquals("Size mismatch in comparing rules.", expected.size(), actual.size());

		Collections.sort(expected);
		Collections.sort(actual);

		for (int i = 0; i < expected.size(); i++) {
			assertEquals(
					String.format("Rule %d mismatch. Expected: [%s], found [%s].", i, expected.get(i), actual.get(i)),
					expected.get(i),
					actual.get(i));
		}
	}
}
