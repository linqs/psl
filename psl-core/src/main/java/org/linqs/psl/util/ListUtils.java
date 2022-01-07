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

import java.util.List;

/**
 * Various static List utilities.
 */
public final class ListUtils {
    // Static only.
    private ListUtils() {}

    public static int[] toPrimitiveIntArray(List<? extends Integer> list) {
        int[] result = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i).intValue();
        }
        return result;
    }

    public static long[] toPrimitiveLongArray(List<? extends Long> list) {
        long[] result = new long[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i).longValue();
        }
        return result;
    }

    public static float[] toPrimitiveFloatArray(List<? extends Float> list) {
        float[] result = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i).floatValue();
        }
        return result;
    }

    public static double[] toPrimitiveDoubleArray(List<? extends Double> list) {
        double[] result = new double[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i).doubleValue();
        }
        return result;
    }

    public static String join(char delim, List<? extends Object> parts) {
        return join("" + delim, parts);
    }

    public static String join(String delim, List<? extends Object> parts) {
        StringBuilder builder = new StringBuilder(parts.size() * 2 - 1);

        for (int i = 0; i < parts.size(); i++) {
            builder.append(parts.get(i));

            if (i != parts.size() - 1) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }
}
