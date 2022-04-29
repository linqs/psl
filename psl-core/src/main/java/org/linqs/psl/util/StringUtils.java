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

import java.util.Arrays;
import java.util.List;

/**
 * Various static String utilities.
 */
public final class StringUtils {
    // Static only.
    private StringUtils() {}

    public static int[] splitInt(String text, char delim) {
        return splitInt(text, "" + delim);
    }

    public static int[] splitInt(String text, String delim) {
        String[] parts = text.split(delim);

        int[] ints = new int[parts.length];
        for (int i = 0; i < parts.length; i++) {
            ints[i] = Integer.parseInt(parts[i]);
        }

        return ints;
    }

    public static long[] splitLong(String text, char delim) {
        return splitLong(text, "" + delim);
    }

    public static long[] splitLong(String text, String delim) {
        String[] parts = text.split(delim);

        long[] longs = new long[parts.length];
        for (int i = 0; i < parts.length; i++) {
            longs[i] = Long.parseLong(parts[i]);
        }

        return longs;
    }

    public static float[] splitFloat(String text, char delim) {
        return splitFloat(text, "" + delim);
    }

    public static float[] splitFloat(String text, String delim) {
        String[] parts = text.split(delim);

        float[] floats = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            floats[i] = Float.parseFloat(parts[i]);
        }

        return floats;
    }

    public static double[] splitDouble(String text, char delim) {
        return splitDouble(text, "" + delim);
    }

    public static double[] splitDouble(String text, String delim) {
        String[] parts = text.split(delim);

        double[] doubles = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            doubles[i] = Double.parseDouble(parts[i]);
        }

        return doubles;
    }

    public static String join(char delim, Object... parts) {
        return join("" + delim, parts);
    }

    public static String join(String delim, Object... parts) {
        StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

        for (int i = 0; i < parts.length; i++) {
            builder.append(parts[i]);

            if (i != parts.length - 1) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }

    public static String join(char delim, int... parts) {
        return join("" + delim, parts);
    }

    public static String join(String delim, int... parts) {
        StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

        for (int i = 0; i < parts.length; i++) {
            builder.append(parts[i]);

            if (i != parts.length - 1) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }

    public static String join(char delim, long... parts) {
        return join("" + delim, parts);
    }

    public static String join(String delim, long... parts) {
        StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

        for (int i = 0; i < parts.length; i++) {
            builder.append(parts[i]);

            if (i != parts.length - 1) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }

    public static String join(char delim, float... parts) {
        return join("" + delim, parts);
    }

    public static String join(String delim, float... parts) {
        StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

        for (int i = 0; i < parts.length; i++) {
            builder.append(parts[i]);

            if (i != parts.length - 1) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }

    public static String join(char delim, double... parts) {
        return join("" + delim, parts);
    }

    public static String join(String delim, double... parts) {
        StringBuilder builder = new StringBuilder(parts.length * 2 - 1);

        for (int i = 0; i < parts.length; i++) {
            builder.append(parts[i]);

            if (i != parts.length - 1) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }

    public static String repeat(String text, int times) {
        return repeat(text, "", times);
    }

    public static String repeat(String text, String delim, int times) {
        if (times < 0) {
            throw new IllegalArgumentException("Cannot repeat a string negative times.");
        } else if (times == 0) {
            return "";
        }

        StringBuilder builder = new StringBuilder(times * 2);
        for (int i = 0; i < times; i++) {
            builder.append(text);

            if (i != (times - 1)) {
                builder.append(delim);
            }
        }

        return builder.toString();
    }

    public static String sort(String string) {
        char[] chars = string.toCharArray();
        Arrays.sort(chars);
        return new String(chars);
    }
}
