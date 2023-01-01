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

import java.util.List;

/**
 * Various static utilities on bits.
 */
public final class BitUtils {
    // Static only.
    private BitUtils() {}

    public static long toBitSet(boolean[] bits) {
        if (bits.length > 64) {
            throw new IllegalArgumentException(String.format(
                    "Number of bits cannot exceed a long (64 bits), got %d.",
                    bits.length));
        }

        long bitSet = 0;
        long mask = 1;

        for (boolean bit : bits) {
            if (bit) {
                bitSet |= mask;
            }

            mask <<= 1;
        }

        return bitSet;
    }

    public static boolean[] toBits(long bitSet) {
        return toBits(bitSet, new boolean[64]);
    }

    /**
     * Convert a bitset into booleans representing each bit.
     * The given array may be smaller than 64.
     * @return The passed in array.
     */
    public static boolean[] toBits(long bitSet, boolean[] bits) {
        if (bits.length > 64) {
            throw new IllegalArgumentException(String.format(
                    "Number of bits cannot exceed a long (64 bits), got %d.",
                    bits.length));
        }

        long mask = 1;

        for (int i = 0; i < bits.length; i++) {
            bits[i] = (bitSet & mask) != 0;
            mask <<= 1;
        }

        return bits;
    }

    /**
     * Fetch the boolean value of a single bit in a bitset.
     */
    public static boolean getBit(long bitSet, int index) {
        if (index >= 64) {
            throw new IndexOutOfBoundsException(String.format(
                    "Max bitset index is 63, got %d.",
                    index));
        }

        long mask = 1 << index;
        return (bitSet & mask) != 0;
    }

    /**
     * Set the value for a single bit in a bitset.
     */
    public static long setBit(long bitSet, int index, boolean value) {
        if (index >= 64) {
            throw new IndexOutOfBoundsException(String.format(
                    "Max bitset index is 63, got %d.",
                    index));
        }

        long mask = 1 << index;

        if (value) {
            return bitSet | mask;
        } else {
            return bitSet & ~mask;
        }
    }
}
