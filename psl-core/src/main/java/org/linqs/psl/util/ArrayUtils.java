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
package org.linqs.psl.util;

import java.util.Arrays;

/**
 * Various static array utilities.
 */
public final class ArrayUtils {
    // Static only.
    private ArrayUtils() {}

    public static double[] ensureCapacity(double[] array, int capacity) {
        assert(capacity >= 0);

        if (array.length  <= capacity) {
            array = Arrays.copyOf(array, (capacity + 1) * 2);
        }
        return array;
    }

    public static float[] ensureCapacity(float[] array, int capacity) {
        assert(capacity >= 0);

        if (array.length  <= capacity) {
            array = Arrays.copyOf(array, (capacity + 1) * 2);
        }
        return array;
    }

    public static int[] ensureCapacity(int[] array, int capacity) {
        assert(capacity >= 0);

        if (array.length  <= capacity) {
            array = Arrays.copyOf(array, (capacity + 1) * 2);
        }
        return array;
    }

    public static <T> T[] ensureCapacity(T[] array, int capacity) {
        assert(capacity >= 0);

        if (array.length  <= capacity) {
            array = Arrays.copyOf(array, (capacity + 1) * 2);
        }
        return array;
    }

    public static <T> int indexOf(T[] haystack, T needle) {
        if (haystack == null) {
            return -1;
        }

        return indexOf(haystack, haystack.length, needle);
    }

    /**
     * Same semantics as List.indexOf().
     */
    public static <T> int indexOf(T[] haystack, int size, T needle) {
        if (haystack == null) {
            return -1;
        }

        for (int i = 0; i < size; i++) {
            if (haystack[i] != null && haystack[i].equals(needle)) {
                return i;
            }
        }

        return -1;
    }

    public static <T> int indexOfReference(T[] haystack, T needle) {
        if (haystack == null) {
            return -1;
        }

        return indexOfReference(haystack, haystack.length, needle);
    }

    /**
     * Like indexOf(), but only checks for referential equality.
     */
    public static <T> int indexOfReference(T[] haystack, int size, T needle) {
        if (haystack == null) {
            return -1;
        }

        for (int i = 0; i < size; i++) {
            if (haystack[i] != null && haystack[i] == needle) {
                return i;
            }
        }

        return -1;
    }
}
