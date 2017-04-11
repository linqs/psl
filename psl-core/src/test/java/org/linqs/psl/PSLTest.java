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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Utilities for testing PSL.
 */
public class PSLTest {
	/**
	 * Convenience call for the common functionality of assertRule() (don't alphabetize).
	 */
	public static void assertRule(Rule rule, String expected) {
		assertRule(rule, expected, true);
	}

	/**
	 * Assert that a rule has the given string representation.
	 *
	 * If, for some reason, the exact format of the output is not known (like with summations which
	 * may order the summation terms in different ways), then you can use |alphabetize| to sort all
	 * characters in both strings (actual and expected) before comparing.
	 * Only alphabetize if it is really necessary since it makes the output much harder to interpret.
	 */
	public static void assertRule(Rule rule, String expected, boolean alphabetize) {
		if (alphabetize) {
			assertEquals(
				String.format("Rule mismatch. (Before alphabetizing) expected: [%s], found [%s].", expected, rule.toString()),
				sort(expected),
				sort(rule.toString())
			);
		} else {
			assertEquals(
				String.format("Rule mismatch. Expected: [%s], found [%s].", expected, rule.toString()),
				expected,
				rule.toString()
			);
		}
	}

	/**
	 * Convenience call for the common functionality of compareGroundRules() (alphabetize).
	 */
	public static void compareGroundRules(List<String> expected, Rule rule, GroundRuleStore store) {
		compareGroundRules(expected, rule, store, true);
	}

	/**
	 * Ground out a rule and check all the grounding against the expected list.
	 * Both the actual grounding and expected grounding will be sorted before comparing.
	 * Here, sorting will occur within each rule and then between rules.
	 *
	 * If, for some reason, the exact format of the output is not known (like with summations which
	 * may order the summation terms in different ways), then you can use |alphabetize| to sort all
	 * characters in both strings (actual and expected) before comparing.
	 * Only alphabetize if it is really necessary.
	 */
	public static void compareGroundRules(List<String> expected, Rule rule, GroundRuleStore store, boolean alphabetize) {
		List<String> actual = new ArrayList<String>();
		for (GroundRule groundRule : store.getGroundKernels(rule)) {
			if (alphabetize) {
				actual.add(sort(groundRule.toString()));
			} else {
				actual.add(groundRule.toString());
			}
		}

		assertEquals("Size mismatch in comparing rules.", expected.size(), actual.size());

		if (alphabetize) {
			for (int i = 0; i < expected.size(); i++) {
				expected.set(i, sort(expected.get(i)));
			}
		}

		Collections.sort(expected);
		Collections.sort(actual);

		for (int i = 0; i < expected.size(); i++) {
			assertEquals(
					String.format("Rule %d mismatch. Expected: [%s], found [%s].", i, expected.get(i), actual.get(i)),
					expected.get(i),
					actual.get(i));
		}
	}

	/**
	 * Compare two Arrays of strings for equality.
	 *
	 * If, for some reason, the content but not exact format of the output is not known;
	 * then you can use |alphabetize| to sort all
	 * characters in both strings (actual and expected) before comparing.
	 * Only alphabetize if it is really necessary because it can hide errors in order that are expected.
	 */
	public static void compareStrings(String[] expected, String[] actual, boolean alphabetize) {
		assertEquals("Size mismatch.", expected.length, actual.length);

		for (int i = 0; i < expected.length; i++) {
			if (alphabetize) {
				assertEquals(
					String.format("String %d mismatch. (Before alphabetize) expected: [%s], found [%s].", i, expected[i], actual[i]),
					sort(expected[i]),
					sort(actual[i])
				);
			} else {
				assertEquals(
					String.format("String %d mismatch. Expected: [%s], found [%s].", i, expected[i], actual[i]),
					expected[i],
					actual[i]
				);
			}
		}
	}

	private static String sort(String string) {
		char[] chars = string.toCharArray();
		Arrays.sort(chars);
		return new String(chars);
	}
}
