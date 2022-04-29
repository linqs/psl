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

import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.parser.ModelLoader;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilities for testing PSL.
 */
public class PSLTest {
    /**
     * Convenience call for the common functionality of assertRule() (alphabetize).
     */
    public static void assertRule(String input, String expectedRule) {
        assertRule(input, expectedRule, true);
    }

    /**
     * Load a rule into a model from a string.
     * All rule assertion methods should go through here.
     *
     * If, for some reason, the exact format of the output is not known (like with summations which
     * may order the summation terms in different ways), then you can use |alphabetize| to sort all
     * characters in both strings (actual and expected) before comparing.
     * Only alphabetize if it is really necessary since it makes the output much harder to interpret.
     */
    public static void assertRule(String input, String expectedRule, boolean alphabetize) {
        Rule rule = null;

        rule = ModelLoader.loadRule(input);
        assertRule(rule, expectedRule, alphabetize);

        // Now ensure that we can load the string version of the created rule.
        rule = ModelLoader.loadRule(rule.toString());
        assertRule(rule, expectedRule, alphabetize);
    }

    /**
     * Convenience call for the common functionality of assertRules() (alphabetize).
     */
    public static void assertRules(Rule[] rules, String[] expected) {
        assertRules(rules, expected, true);
    }

     public static void assertRules(Rule[] rules, String[] expected, boolean alphabetize) {
        assertEquals("Size mismatch.", expected.length, rules.length);

        for (int i = 0; i < expected.length; i++) {
            assertStringEquals(expected[i], rules[i].toString(), alphabetize, String.format("Rule %d mismatch", i));
        }
     }

    /**
     * Assert that two strings are equal, possibly forcing alphabetization on the strings first.
     */
    public static void assertStringEquals(String expected, String actual, boolean alphabetize, String message) {
        if (alphabetize) {
            assertEquals(
                String.format("%s. (Before alphabetize) expected: [%s], found [%s].", message, expected, actual),
                sort(expected),
                sort(actual)
            );
        } else {
            assertEquals(
                String.format("%s. Expected: [%s], found [%s].", message, expected, actual),
                expected,
                actual
            );
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
    public static void assertStringsEquals(String[] expected, String[] actual, boolean alphabetize) {
        assertEquals("Size mismatch.", expected.length, actual.length);

        for (int i = 0; i < expected.length; i++) {
            assertStringEquals(expected[i], actual[i], alphabetize, String.format("String %d mismatch", i));
        }
    }

    /**
     * Convenience call for the common functionality of assertModel() (alphabetize).
     */
    public static void assertModel(String input, String[] expectedRules) {
        assertModel(input, expectedRules, true);
    }

    /**
     * Load a rules into a model from a string.
     *
     * If, for some reason, the exact format of the output is not known (like with summations which
     * may order the summation terms in different ways), then you can use |alphabetize| to sort all
     * characters in both strings (actual and expected) before comparing.
     * Only alphabetize if it is really necessary since it makes the output much harder to interpret.
     */
    public static void assertModel(String input, String[] expectedRules, boolean alphabetize) {
        Model model = ModelLoader.load(input);

        List<Rule> rules = new ArrayList<Rule>();
        for (Rule rule : model.getRules()) {
            rules.add(rule);
        }

        assertRules(rules.toArray(new Rule[0]), expectedRules, alphabetize);

        // Try again with each rule, but use the generated text for each rule.
        for (int i = 0; i < rules.size(); i++) {
            try {
                assertRule(rules.get(i).toString(), expectedRules[i], alphabetize);
            } catch (org.antlr.v4.runtime.RecognitionException ex) {
                throw new RuntimeException("toString() rule did not parse: " + rules.get(i).toString(), ex);
            }
        }
    }

    /**
     * A weaker variant of assertModel() that only uses sorted strings for comparison.
     * Use when you can't give guarentees on both the order of rules and format of each rule.
     */
    public static void assertStringModel(String input, String[] expectedRules, boolean alphabetize) {
        Model model = ModelLoader.load(input);

        List<String> rules = new ArrayList<String>();
        for (Rule rule : model.getRules()) {
            rules.add(rule.toString());
        }

        assertEquals("Size mismatch.", expectedRules.length, rules.size());

        String[] stringRules = rules.toArray(new String[0]);

        if (alphabetize) {
            for (int i = 0; i < expectedRules.length; i++) {
                stringRules[i] = sort(stringRules[i]);
                expectedRules[i] = sort(expectedRules[i]);
            }
        }

        Arrays.sort(stringRules);
        Arrays.sort(expectedRules);

        for (int i = 0; i < expectedRules.length; i++) {
            assertStringEquals(expectedRules[i], stringRules[i], false, "Rule mismatch");
        }
    }

    public static List<Rule> getRules(String input) {
        Model model = ModelLoader.load(input);

        List<Rule> rules = new ArrayList<Rule>();
        for (Rule rule : model.getRules()) {
            rules.add(rule);
        }

        return rules;
    }
    /**
     * Convenience call for the common functionality of assertRule() (alphabetize).
     */
    private static void assertRule(Rule rule, String expected) {
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
    private static void assertRule(Rule rule, String expected, boolean alphabetize) {
        assertStringEquals(expected, rule.toString(), alphabetize, "Rule mismatch");
    }

    private static String sort(String string) {
        char[] chars = string.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }
}
