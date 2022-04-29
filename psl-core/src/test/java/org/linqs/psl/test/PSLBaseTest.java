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

import org.linqs.psl.config.Options;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.AbstractRule;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.MathUtils;

import org.apache.log4j.PropertyConfigurator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

/**
 * The base test that all PSL core tests derive from.
 * This base ensures that all PSL resources get properly cleaned up after a test completes.
 */
public abstract class PSLBaseTest {
    public static final String RESOURCES_BASE_FILE = ".resources";
    protected final String RESOURCE_DIR;

    public PSLBaseTest() {
        RESOURCE_DIR = (new File(this.getClass().getClassLoader().getResource(RESOURCES_BASE_FILE).getFile())).getParentFile().getAbsolutePath();
    }

    @Before
    public void pslBaseSetup() {
    }

    @After
    public void pslBaseCleanup() {
        // Remove any defined rules.
        AbstractRule.unregisterAllRulesForTesting();

        // Close any known open models.
        TestModel.ModelInformation.closeAll();

        // Clear all options.
        Options.clearAll();
    }

    // General utils.

    // Init a defualt logger with the given level.
    protected void initLogger(String logLevel) {
        Properties props = new Properties();

        props.setProperty("log4j.rootLogger", String.format("%s, A1", logLevel));
        props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
        props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");

        PropertyConfigurator.configure(props);
    }

    // Init with the default logging level: DEBUG.
    protected void initLogger() {
        initLogger("DEBUG");
    }

    protected void disableLogger() {
        initLogger("OFF");
    }

    protected String sort(String string) {
        char[] chars = string.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }

    // Implement assertions so downstream tests don't have to import or configure.

    // PSL custom assertions.

    /**
     * Convenience call for the common functionality of assertRule() (alphabetize).
     */
    protected void assertRule(Rule rule, String expected) {
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
    protected void assertRule(Rule rule, String expected, boolean alphabetize) {
        assertStringEquals(expected, rule.toString(), alphabetize, "Rule mismatch");
    }

    /**
     * Convenience call for the common functionality of assertRules() (alphabetize).
     */
    protected void assertRules(Rule[] rules, String[] expected) {
        assertRules(rules, expected, true);
    }

    protected void assertRules(Rule[] rules, String[] expected, boolean alphabetize) {
        assertEquals("Size mismatch.", expected.length, rules.length);

        for (int i = 0; i < expected.length; i++) {
            assertStringEquals(expected[i], rules[i].toString(), alphabetize, String.format("Rule %d mismatch", i));
        }
    }

    /**
     * Assert that two strings are equal, possibly forcing alphabetization on the strings first.
     */
    protected void assertStringEquals(String expected, String actual, boolean alphabetize, String message) {
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
    protected void assertStringsEquals(String[] expected, String[] actual, boolean alphabetize) {
        assertEquals("Size mismatch.", expected.length, actual.length);

        for (int i = 0; i < expected.length; i++) {
            assertStringEquals(expected[i], actual[i], alphabetize, String.format("String %d mismatch", i));
        }
    }

    /**
     * Convenience call for the common functionality of compareGroundRules() (alphabetize).
     */
    protected void compareGroundRules(List<String> expected, Rule rule, GroundRuleStore store) {
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
    protected void compareGroundRules(List<String> expected, Rule rule, GroundRuleStore store, boolean alphabetize) {
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

    // Pass throughs for junit assertions.

    // fail

    protected void fail(String message) {
        Assert.fail(message);
    }

    protected void assertNotEquals() {
        Assert.fail();
    }

    // assertNull

    protected void assertNull(String message, Object object) {
        Assert.assertNull(message, object);
    }

    protected void assertNull(Object object) {
        Assert.assertNull(object);
    }

    // assertNotNull

    protected void assertNotNull(String message, Object object) {
        Assert.assertNotNull(message, object);
    }

    protected void assertNotNull(Object object) {
        Assert.assertNotNull(object);
    }

    // assertTrue

    protected void assertTrue(String message, boolean condition) {
        Assert.assertTrue(message, condition);
    }

    protected void assertTrue(boolean condition) {
        Assert.assertTrue(condition);
    }

    // assertFalse

    protected void assertFalse(String message, boolean condition) {
        Assert.assertFalse(message, condition);
    }

    protected void assertFalse(boolean condition) {
        Assert.assertFalse(condition);
    }

    // assertEquals

    protected void assertEquals(String message, float a, float b, float epsilon) {
        Assert.assertEquals(message, a, b, epsilon);
    }

    protected void assertEquals(String message, float a, float b) {
        Assert.assertEquals(message, a, b, MathUtils.EPSILON);
    }

    protected void assertEquals(float a, float b, float epsilon) {
        Assert.assertEquals(a, b, epsilon);
    }

    protected void assertEquals(float a, float b) {
        Assert.assertEquals(a, b, MathUtils.EPSILON);
    }

    protected void assertEquals(String message, double a, double b, double epsilon) {
        Assert.assertEquals(message, a, b, epsilon);
    }

    protected void assertEquals(String message, double a, double b) {
        Assert.assertEquals(message, a, b, MathUtils.EPSILON);
    }

    protected void assertEquals(double a, double b, double epsilon) {
        Assert.assertEquals(a, b, epsilon);
    }

    protected void assertEquals(double a, double b) {
        Assert.assertEquals(a, b, MathUtils.EPSILON);
    }

    protected void assertEquals(String message, int a, int b) {
        Assert.assertEquals(message, a, b);
    }

    protected void assertEquals(int a, int b) {
        Assert.assertEquals(a, b);
    }

    protected void assertEquals(String message, long a, long b) {
        Assert.assertEquals(message, a, b);
    }

    protected void assertEquals(long a, long b) {
        Assert.assertEquals(a, b);
    }

    protected void assertEquals(String message, Object a, Object b) {
        Assert.assertEquals(message, a, b);
    }

    protected void assertEquals(Object a, Object b) {
        Assert.assertEquals(a, b);
    }

    // assertNotEquals

    protected void assertNotEquals(String message, float a, float b, float epsilon) {
        Assert.assertNotEquals(message, a, b, epsilon);
    }

    protected void assertNotEquals(String message, float a, float b) {
        Assert.assertNotEquals(message, a, b, MathUtils.EPSILON);
    }

    protected void assertNotEquals(float a, float b, float epsilon) {
        Assert.assertNotEquals(a, b, epsilon);
    }

    protected void assertNotEquals(float a, float b) {
        Assert.assertNotEquals(a, b, MathUtils.EPSILON);
    }

    protected void assertNotEquals(String message, double a, double b, double epsilon) {
        Assert.assertNotEquals(message, a, b, epsilon);
    }

    protected void assertNotEquals(String message, double a, double b) {
        Assert.assertNotEquals(message, a, b, MathUtils.EPSILON);
    }

    protected void assertNotEquals(double a, double b, double epsilon) {
        Assert.assertNotEquals(a, b, epsilon);
    }

    protected void assertNotEquals(double a, double b) {
        Assert.assertNotEquals(a, b, MathUtils.EPSILON);
    }

    protected void assertNotEquals(String message, int a, int b) {
        Assert.assertNotEquals(message, a, b);
    }

    protected void assertNotEquals(int a, int b) {
        Assert.assertNotEquals(a, b);
    }

    protected void assertNotEquals(String message, long a, long b) {
        Assert.assertNotEquals(message, a, b);
    }

    protected void assertNotEquals(long a, long b) {
        Assert.assertNotEquals(a, b);
    }

    protected void assertNotEquals(String message, Object a, Object b) {
        Assert.assertNotEquals(message, a, b);
    }

    protected void assertNotEquals(Object a, Object b) {
        Assert.assertNotEquals(a, b);
    }
}
