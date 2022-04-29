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
package org.linqs.psl.util;

import org.linqs.psl.test.PSLBaseTest;

import org.junit.Test;

import java.util.Arrays;

public class IteratorUtilsTest extends PSLBaseTest {
    @Test
    public void testPowersetOne() {
        boolean[][] expected = new boolean[][]{
            new boolean[]{false},
            new boolean[]{true},
        };

        int count = 0;
        for (boolean[] subset : IteratorUtils.powerset(1)) {
            assertEquals(Arrays.toString(expected[count]), Arrays.toString(subset));
            count++;
        }

        assertEquals(expected.length, count);
    }

    @Test
    public void testPowersetTwo() {
        boolean[][] expected = new boolean[][]{
            new boolean[]{false, false},
            new boolean[]{true, false},
            new boolean[]{false, true},
            new boolean[]{true, true},
        };

        int count = 0;
        for (boolean[] subset : IteratorUtils.powerset(2)) {
            assertEquals(Arrays.toString(expected[count]), Arrays.toString(subset));
            count++;
        }

        assertEquals(expected.length, count);
    }

    @Test
    public void testPowersetThree() {
        boolean[][] expected = new boolean[][]{
            new boolean[]{false, false, false},
            new boolean[]{true, false, false},
            new boolean[]{false, true, false},
            new boolean[]{true, true, false},
            new boolean[]{false, false, true},
            new boolean[]{true, false, true},
            new boolean[]{false, true, true},
            new boolean[]{true, true, true},
        };

        int count = 0;
        for (boolean[] subset : IteratorUtils.powerset(3)) {
            assertEquals(Arrays.toString(expected[count]), Arrays.toString(subset));
            count++;
        }

        assertEquals(expected.length, count);
    }
}
