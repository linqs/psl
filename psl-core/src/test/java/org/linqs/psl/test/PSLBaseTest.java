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
import org.linqs.psl.util.MathUtils;

import org.junit.After;
import org.junit.Assert;
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

    // Implement assertions so downstream tests don't have to import or configure.

    // fail

    public void fail(String message) {
        Assert.fail(message);
    }

    public void assertNotEquals() {
        Assert.fail();
    }

    // assertNull

    public void assertNull(String message, Object object) {
        Assert.assertNull(message, object);
    }

    public void assertNull(Object object) {
        Assert.assertNull(object);
    }

    // assertNotNull

    public void assertNotNull(String message, Object object) {
        Assert.assertNotNull(message, object);
    }

    public void assertNotNull(Object object) {
        Assert.assertNotNull(object);
    }

    // assertTrue

    public void assertTrue(String message, boolean condition) {
        Assert.assertTrue(message, condition);
    }

    public void assertTrue(boolean condition) {
        Assert.assertTrue(condition);
    }

    // assertFalse

    public void assertFalse(String message, boolean condition) {
        Assert.assertFalse(message, condition);
    }

    public void assertFalse(boolean condition) {
        Assert.assertFalse(condition);
    }

    // assertEquals

    public void assertEquals(String message, float a, float b, float epsilon) {
        Assert.assertEquals(message, a, b, epsilon);
    }

    public void assertEquals(String message, float a, float b) {
        Assert.assertEquals(message, a, b, MathUtils.EPSILON);
    }

    public void assertEquals(float a, float b, float epsilon) {
        Assert.assertEquals(a, b, epsilon);
    }

    public void assertEquals(float a, float b) {
        Assert.assertEquals(a, b, MathUtils.EPSILON);
    }

    public void assertEquals(String message, double a, double b, double epsilon) {
        Assert.assertEquals(message, a, b, epsilon);
    }

    public void assertEquals(String message, double a, double b) {
        Assert.assertEquals(message, a, b, MathUtils.EPSILON);
    }

    public void assertEquals(double a, double b, double epsilon) {
        Assert.assertEquals(a, b, epsilon);
    }

    public void assertEquals(double a, double b) {
        Assert.assertEquals(a, b, MathUtils.EPSILON);
    }

    public void assertEquals(String message, int a, int b) {
        Assert.assertEquals(message, a, b);
    }

    public void assertEquals(int a, int b) {
        Assert.assertEquals(a, b);
    }

    public void assertEquals(String message, long a, long b) {
        Assert.assertEquals(message, a, b);
    }

    public void assertEquals(long a, long b) {
        Assert.assertEquals(a, b);
    }

    public void assertEquals(String message, Object a, Object b) {
        Assert.assertEquals(message, a, b);
    }

    public void assertEquals(Object a, Object b) {
        Assert.assertEquals(a, b);
    }

    // assertNotEquals

    public void assertNotEquals(String message, float a, float b, float epsilon) {
        Assert.assertNotEquals(message, a, b, epsilon);
    }

    public void assertNotEquals(String message, float a, float b) {
        Assert.assertNotEquals(message, a, b, MathUtils.EPSILON);
    }

    public void assertNotEquals(float a, float b, float epsilon) {
        Assert.assertNotEquals(a, b, epsilon);
    }

    public void assertNotEquals(float a, float b) {
        Assert.assertNotEquals(a, b, MathUtils.EPSILON);
    }

    public void assertNotEquals(String message, double a, double b, double epsilon) {
        Assert.assertNotEquals(message, a, b, epsilon);
    }

    public void assertNotEquals(String message, double a, double b) {
        Assert.assertNotEquals(message, a, b, MathUtils.EPSILON);
    }

    public void assertNotEquals(double a, double b, double epsilon) {
        Assert.assertNotEquals(a, b, epsilon);
    }

    public void assertNotEquals(double a, double b) {
        Assert.assertNotEquals(a, b, MathUtils.EPSILON);
    }

    public void assertNotEquals(String message, int a, int b) {
        Assert.assertNotEquals(message, a, b);
    }

    public void assertNotEquals(int a, int b) {
        Assert.assertNotEquals(a, b);
    }

    public void assertNotEquals(String message, long a, long b) {
        Assert.assertNotEquals(message, a, b);
    }

    public void assertNotEquals(long a, long b) {
        Assert.assertNotEquals(a, b);
    }

    public void assertNotEquals(String message, Object a, Object b) {
        Assert.assertNotEquals(message, a, b);
    }

    public void assertNotEquals(Object a, Object b) {
        Assert.assertNotEquals(a, b);
    }
}
