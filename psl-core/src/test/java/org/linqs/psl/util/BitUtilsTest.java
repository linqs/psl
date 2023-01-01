/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class BitUtilsTest {
    // We will limit the size to three for these tests.

    private static final long[] BIT_SETS = new long[]{
        0l,
        1l,
        2l,
        3l,
        4l,
        5l,
        6l,
        7l,
    };

    private static final boolean[][] BIT_ARRAYS = new boolean[][]{
        new boolean[]{false, false, false},
        new boolean[]{true, false, false},
        new boolean[]{false, true, false},
        new boolean[]{true, true, false},
        new boolean[]{false, false, true},
        new boolean[]{true, false, true},
        new boolean[]{false, true, true},
        new boolean[]{true, true, true},
    };

    @Test
    public void testToBits() {
        boolean[] bits = new boolean[3];
        for (int i = 0; i < BIT_SETS.length; i++) {
            BitUtils.toBits(BIT_SETS[i], bits);
            String note = String.format("Index: %d, Expected: %s, Actual: %s", i, Arrays.toString(BIT_ARRAYS[i]), Arrays.toString(bits));
            assertArrayEquals(note, BIT_ARRAYS[i], bits);
        }
    }

    @Test
    public void testToBitSet() {
        for (int i = 0; i < BIT_SETS.length; i++) {
            long actual = BitUtils.toBitSet(BIT_ARRAYS[i]);
            String note = String.format("Index: %d, Expected: %d, Actual: %d", i, BIT_SETS[i], actual);
            assertEquals(note, BIT_SETS[i], actual);
        }
    }

    @Test
    public void testGetBit() {
        for (int caseIndex = 0; caseIndex < BIT_SETS.length; caseIndex++) {
            long bitSet = BIT_SETS[caseIndex];
            boolean[] bits = BIT_ARRAYS[caseIndex];

            for (int bitIndex = 0; bitIndex < bits.length; bitIndex++) {
                String note = String.format("Case Index: %d, Bit Index: %d", caseIndex, bitIndex);
                assertEquals(note, bits[bitIndex], BitUtils.getBit(bitSet, bitIndex));
            }
        }
    }

    @Test
    public void testSetBit() {
        long bits = 0l;

        // Flip from false to true.
        for (int i = 0; i < BIT_ARRAYS[0].length; i++) {
            bits = BitUtils.setBit(bits, i, true);
            assertTrue(BitUtils.getBit(bits, i));
        }

        // Stay true.
        for (int i = 0; i < BIT_ARRAYS[0].length; i++) {
            bits = BitUtils.setBit(bits, i, true);
            assertTrue(BitUtils.getBit(bits, i));
        }

        // Flip from true to false.
        for (int i = 0; i < BIT_ARRAYS[0].length; i++) {
            bits = BitUtils.setBit(bits, i, false);
            assertFalse(BitUtils.getBit(bits, i));
        }

        // Stay false.
        for (int i = 0; i < BIT_ARRAYS[0].length; i++) {
            bits = BitUtils.setBit(bits, i, false);
            assertFalse(BitUtils.getBit(bits, i));
        }
    }
}
