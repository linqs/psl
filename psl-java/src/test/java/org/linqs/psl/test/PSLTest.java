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
package org.linqs.psl.test;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.java.PSLModel;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;

import java.util.Arrays;

public class PSLTest {
    /**
     * Convenience call for the common functionality of assertModel() (alphabetize).
     */
    public static void assertModel(Model model, String[] expectedRules) {
        assertModel(model, expectedRules, true);
    }

    /**
     * Assert that the current model has the given rules.
     *
     * If, for some reason, the exact format of the output is not known (like with summations which
     * may order the summation terms in different ways), then you can use |alphabetize| to sort all
     * characters in both strings (actual and expected) before comparing.
     * Only alphabetize if it is really necessary since it makes the output much harder to interpret.
     */
    public static void assertModel(Model model, String[] expectedRules, boolean alphabetize) {
        int ruleCount = 0;

        if (alphabetize) {
            for (Rule rule : model.getRules()) {
                String alphaRule = sort(rule.toString());
                String alphaExpected = sort(expectedRules[ruleCount]);

                assertEquals(
                        String.format("Rule %d mismatch. Expected (before alphabetizing): [%s], found [%s].", ruleCount, expectedRules[ruleCount], rule.toString()),
                        alphaExpected,
                        alphaRule
                );
                ruleCount++;
            }
        } else {
            for (Rule rule : model.getRules()) {
                assertEquals(
                        String.format("Rule %d mismatch. Expected: [%s], found [%s].", ruleCount, expectedRules[ruleCount], rule.toString()),
                        expectedRules[ruleCount],
                        rule.toString()
                );
                ruleCount++;
            }
        }

        assertEquals("Mismatch in expected rule count.", expectedRules.length, ruleCount);
    }

    /**
     * Compare two Arrays of strings for equality.
     *
     * If, for some reason, the content but not exact format of the output is not known;
     * then you can use |alphabetize| to sort all
     * characters in both strings (actual and expected) before comparing.
     * Only alphabetize if it is really necessary because it can hide errors in order that are expected.
     */
    private static void compareStrings(String[] expected, String[] actual, boolean alphabetize) {
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
