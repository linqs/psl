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

import java.math.BigInteger;

/**
 * Various static math utilities.
 */
public final class MathUtils {
    public static final double EPSILON = 1e-6;
    public static final double RELAXED_EPSILON = 5e-3;
    public static final double STRICT_EPSILON = 1e-8;

    public static final float EPSILON_FLOAT = 1e-4f;
    public static final float RELAXED_EPSILON_FLOAT = 5e-3f;
    public static final float STRICT_EPSILON_FLOAT = 1e-6f;

    // Static only.
    private MathUtils() {}

    public static boolean signsMatch(int a, int b) {
        return (a > 0 && b > 0) || (a < 0 && b < 0) || (a == 0 && b == 0);
    }

    public static boolean signsMatch(float a, float b) {
        return (a > 0 && b > 0) || (a < 0 && b < 0) || (isZero(a) && isZero(b));
    }

    public static boolean signsMatch(double a, double b) {
        return (a > 0 && b > 0) || (a < 0 && b < 0) || (isZero(a) && isZero(b));
    }

    public static boolean equals(double a, double b) {
        return equals(a, b, EPSILON);
    }

    public static boolean equalsRelaxed(double a, double b) {
        return equals(a, b, RELAXED_EPSILON);
    }

    public static boolean equalsStrict(double a, double b) {
        return equals(a, b, STRICT_EPSILON);
    }

    public static boolean equals(double a, double b, double epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

    public static int compare(double a, double b) {
        return compare(a, b, EPSILON);
    }

    /**
     * A comparison method with the Comparator integer return semantics.
     */
    public static int compare(double a, double b, double epsilon) {
        if (equals(a, b, epsilon)) {
            return 0;
        }

        if (a < b) {
            return -1;
        }

        return 1;
    }

    public static boolean isZero(double a) {
        return equals(a, 0.0);
    }

    public static boolean isZero(double a, double epsilon) {
        return equals(a, 0.0, epsilon);
    }

    public static boolean equals(float a, float b) {
        return equals(a, b, EPSILON_FLOAT);
    }

    public static boolean equalsRelaxed(float a, float b) {
        return equals(a, b, RELAXED_EPSILON_FLOAT);
    }

    public static boolean equalsStrict(float a, float b) {
        return equals(a, b, STRICT_EPSILON_FLOAT);
    }

    public static boolean equals(float a, float b, float epsilon) {
        return Math.abs(a - b) <= epsilon;
    }

    public static int compare(float a, float b) {
        return compare(a, b, EPSILON_FLOAT);
    }

    /**
     * A comparison method with the Comparator integer return semantics.
     */
    public static int compare(float a, float b, float epsilon) {
        if (equals(a, b, epsilon)) {
            return 0;
        }

        if (a < b) {
            return -1;
        }

        return 1;
    }

    public static boolean isZero(float a) {
        return equals(a, 0.0f);
    }

    public static boolean isZero(float a, float epsilon) {
        return equals(a, 0.0f, epsilon);
    }

    public static int smallFactorial(int number) {
        if (number >= 16) {
            throw new IllegalArgumentException("Too large a number for smallFactorial: " + number);
        }

        int result = 1;

        for (int factor = 2; factor <= number; factor++) {
            result *= factor;
        }

        return result;
    }

    public static BigInteger factorial(BigInteger number) {
        BigInteger result = BigInteger.valueOf(1);

        for (long factor = 2; factor <= number.longValue(); factor++) {
            result = result.multiply(BigInteger.valueOf(factor));
        }

        return result;
    }

    /**
     * Scale n-dimensional double array to unit vector.
     */
    public static void toUnit(double[] vector) {
        toMagnitude(vector, 1.0);
    }

    /**
     * Scale n-dimensional float array to unit vector.
     */
    public static void toUnit(float[] vector) {
        toMagnitude(vector, 1.0);
    }

    /**
     * Scale n-dimensional double array to vector with the specified magnitude.
     */
    public static void toMagnitude(double[] vector, double magnitude) {
        if (magnitude <= 0.0) {
            throw new ArithmeticException("Cannot scale a vector to a non-positive magnitude.");
        }

        double norm = pNorm(vector, 2.0f);
        if (!((norm != 0.0) || (vector.length == 0))) {
            throw new ArithmeticException("Cannot scale a zero vector to a non-zero magnitude.");
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] = (magnitude * (vector[i] / norm));
        }
    }

    /**
     * Scale n-dimensional float array to vector with the specified magnitude.
     */
    public static void toMagnitude(float[] vector, double magnitude) {
        if (magnitude <= 0.0) {
            throw new ArithmeticException("Cannot scale a vector to a non-positive magnitude.");
        }

        float norm = pNorm(vector, 2.0f);
        if (!((norm != 0.0) || (vector.length == 0))) {
            throw new ArithmeticException("Cannot scale a zero vector to a non-zero magnitude.");
        }

        for (int i = 0; i < vector.length; i++) {
            vector[i] = (float)(magnitude * (vector[i] / norm));
        }
    }

    /**
     * Compute the p-norm of the provided vector.
     */
    public static float pNorm(float[] vector, float p) {
        float norm = 0.0f;

        if (p <= 0.0f) {
            throw new ArithmeticException("The p-norm for p <= 0.0 is not defined.");
        }

        if (p == Float.POSITIVE_INFINITY) {
            for (float v : vector) {
                if (norm < Math.abs(v)) {
                    norm = Math.abs(v);
                }
            }
            return norm;
        }

        for (float v : vector) {
            norm += Math.pow(v, p);
        }
        norm = (float)Math.pow(norm, 1.0f / p);

        return norm;
    }

    /**
     * Compute the p-norm of the provided vector.
     */
    public static double pNorm(double[] vector, double p) {
        double norm = 0.0;

        if (p <= 0.0) {
            throw new ArithmeticException("The p-norm for p <= 0.0 is not defined.");
        }

        if (p == Double.POSITIVE_INFINITY) {
            for (double v : vector) {
                if (norm < Math.abs(v)) {
                    norm = Math.abs(v);
                }
            }
            return norm;
        }

        for (double v : vector) {
            norm += Math.pow(v, p);
        }
        norm = Math.pow(norm, 1.0f / p);

        return norm;
    }
}
