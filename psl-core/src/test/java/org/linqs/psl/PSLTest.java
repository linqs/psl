/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;

import org.apache.log4j.PropertyConfigurator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * Utilities for testing PSL.
 */
public class PSLTest {
    /**
     * Convenience call for the common functionality of assertRule() (alphabetize).
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
        assertStringEquals(expected, rule.toString(), alphabetize, "Rule mismatch");
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
        for (GroundRule groundRule : store.getGroundRules(rule)) {
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

    private static String sort(String string) {
        char[] chars = string.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    // Init a defualt logger with the given level.
    public static void initLogger(String logLevel) {
        Properties props = new Properties();

        props.setProperty("log4j.rootLogger", String.format("%s, A1", logLevel));
        props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
        props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");

        PropertyConfigurator.configure(props);
    }

    // Init with the default logging level: DEBUG.
    public static void initLogger() {
        initLogger("DEBUG");
    }

    public static void disableLogger() {
        initLogger("OFF");
    }
}
