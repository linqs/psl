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

public class FloatMatrixTest extends PSLBaseTest {
    public static final float EPSILON = 1e-6f;
    @Test
    public void testConstruction() {
        FloatMatrix matrix;
        float[] data;
        float[][] gridData;

        matrix = new FloatMatrix();
        assertEquals(0, matrix.size());

        data = new float[6];
        matrix = new FloatMatrix(data, 2, 3);
        assertEquals(6, matrix.size());
        assertEquals(2, matrix.numRows());
        assertEquals(3, matrix.numCols());

        // Owner keeps control.
        matrix.set(0, 0, -1.0f);
        assertEquals(0.0f, data[0], EPSILON);

        data[1] = 1.0f;
        assertEquals(0.0f, matrix.get(1, 0), EPSILON);

        // Owner gives up control.
        data = new float[6];
        matrix = new FloatMatrix(data, 2, 3, false);

        matrix.set(0, 0, -1.0f);
        assertEquals(-1.0f, data[0], EPSILON);

        data[1] = 1.0f;
        assertEquals(1.0f, matrix.get(1, 0), EPSILON);

        // Incorrect sizes given.
        try {
            data = new float[7];
            matrix = new FloatMatrix(data, 2, 3);
            fail("Constructor did not throw an exception when given incorrect sizes.");
        } catch (IllegalArgumentException ex) {
            // Expected.
        }

        gridData = new float[][]{
            {0.0f, 1.0f, 2.0f},
            {3.0f, 4.0f, 5.0f}
        };
        matrix = new FloatMatrix(gridData);
        for (int i = 0; i < 2; i++) {
            for (int j = 0; j < 3; j++) {
                assertEquals(matrix.get(i, j), (float)(i * 3 + j), EPSILON);
            }
        }

        try {
            gridData = new float[][]{
                {0.0f, 1.0f, 2.0f},
                {3.0f, 4.0f}
            };
        matrix = new FloatMatrix(gridData);
        } catch (IllegalArgumentException ex) {
            // Expected.
        }
    }

    @Test
    public void testStaticConstructors() {
        FloatMatrix matrix;
        float[] data;

        matrix = FloatMatrix.zeroes(2, 3);
        assertEquals(new FloatMatrix(new float[][]{{0, 0, 0}, {0, 0, 0}}), matrix);

        matrix = FloatMatrix.eye(1);
        assertEquals(1, matrix.size());
        assertEquals(1.0f, matrix.get(0, 0), EPSILON);

        matrix = FloatMatrix.eye(3);
        assertEquals(matrix.size(), 9);
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                if (i == j) {
                    assertEquals(1.0f, matrix.get(i, j), EPSILON);
                } else {
                    assertEquals(0.0f, matrix.get(i, j), EPSILON);
                }
            }
        }

        data = new float[]{0.0f, 1.0f, 2.0f};
        matrix = FloatMatrix.columnVector(data);
        assertEquals(new FloatMatrix(new float[][]{{0.0f}, {1.0f}, {2.0f}}), matrix);

        data = new float[]{0.0f, 1.0f, 2.0f};
        matrix = FloatMatrix.rowVector(data);
        assertEquals(new FloatMatrix(new float[][]{{0.0f, 1.0f, 2.0f}}), matrix);
    }

    @Test
    public void testToString() {
       FloatMatrix matrix;

       matrix = new FloatMatrix();
       assertEquals("[]", matrix.toString());

       matrix = FloatMatrix.zeroes(1, 2);
       assertEquals("[[0.0, 0.0]]", matrix.toString());

       matrix = FloatMatrix.eye(3);
       assertEquals("[[1.0, 0.0, 0.0], [0.0, 1.0, 0.0], [0.0, 0.0, 1.0]]", matrix.toString());
    }

    @Test
    public void testCopy() {
        FloatMatrix matrix = FloatMatrix.zeroes(3, 2);
        FloatMatrix copy = matrix.copy();

        assertEquals(matrix, copy);

        copy.set(0, 0, 1.0f);
        assertNotEquals(matrix, copy);
    }

    @Test
    public void testElementWiseOperations() {
        int size = 3;

        FloatMatrix base = FloatMatrix.zeroes(size, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);
                base.set(i, j, baseVal);
            }
        }

        FloatMatrix delta = FloatMatrix.ones(size, size).mul(2.0f);
        FloatMatrix other;

        other = base.elementSub(delta);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);
                assertEquals(baseVal - 2.0f, other.get(i, j), EPSILON);
            }
        }

        other = base.elementAdd(delta);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);
                assertEquals(baseVal + 2.0f, other.get(i, j), EPSILON);
            }
        }

        other = base.elementMul(delta);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);
                assertEquals(baseVal * 2.0f, other.get(i, j), EPSILON);
            }
        }

        other = base.elementDiv(delta);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);
                assertEquals(baseVal / 2.0f, other.get(i, j), EPSILON);
            }
        }

        other = base.elementLog();
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);
                assertEquals(Math.log(baseVal), other.get(i, j), EPSILON);
            }
        }
    }

    @Test
    public void testNorm() {
        int size = 3;

        float norm1 = 0.0f;
        float norm2 = 0.0f;

        FloatMatrix base = FloatMatrix.zeroes(size, size);
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                float baseVal = (float)(i * size + j);

                base.set(i, j, baseVal);

                norm1 += baseVal;
                norm2 += baseVal * baseVal;
            }
        }

        norm2 = (float)Math.sqrt(norm2);

        assertEquals(norm1, base.norm1(), EPSILON);
        assertEquals(36.0f, base.norm1(), EPSILON);

        assertEquals(norm2, base.norm2(), EPSILON);
        assertEquals(14.2828568571f, base.norm2(), EPSILON);
    }

    @Test
    public void testTranspose() {
        FloatMatrix matrix;
        FloatMatrix transpose;
        FloatMatrix solution;

        matrix = FloatMatrix.eye(4);
        transpose = matrix.transpose();
        assertEquals(FloatMatrix.eye(4), transpose);

        matrix = FloatMatrix.zeroes(3, 4);
        solution = FloatMatrix.zeroes(4, 3);

        float count = 0.0f;
        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 4; j++) {
                matrix.set(i, j, count);
                solution.set(j, i, count);

                count += 1.0f;
            }
        }

        transpose = matrix.transpose();
        assertEquals(solution, transpose);
    }

    @Test
    public void testMatrixMultiplication() {
        FloatMatrix a;
        FloatMatrix b;
        FloatMatrix aCopy;
        FloatMatrix bCopy;
        FloatMatrix product;
        FloatMatrix solution;

        // No transpose.
        a = new FloatMatrix(new float[][]{{1.0f, 2.0f, -1.0f}, {2.0f, 0.0f, 1.0f}});
        b = new FloatMatrix(new float[][]{{3.0f, 1.0f}, {0.0f, -1.0f}, {-2.0f, 3.0f}});
        solution = new FloatMatrix(new float[][]{{5.0f, -4.0f}, {4.0f, 5.0f}});

        aCopy = a.copy();
        bCopy = b.copy();

        product = a.mul(b);

        assertEquals(solution, product);
        assertEquals(aCopy, a);
        assertEquals(bCopy, b);

        // Transpose A.
        a = new FloatMatrix(new float[][]{{1.0f, 2.0f, 3.0f}, {4.0f, 5.0f, 6.0f}, {7.0f, 8.0f, 9.0f}});
        b = new FloatMatrix(new float[][]{{9.0f, 8.0f, 7.0f}, {6.0f, 5.0f, 4.0f}, {3.0f, 2.0f, 1.0f}});
        solution = new FloatMatrix(new float[][]{{54.0f, 42.0f, 30.0f}, {72.0f, 57.0f, 42.0f}, {90.0f, 72.0f, 54.0f}});

        aCopy = a.copy();
        bCopy = b.copy();

        product = a.mul(b, true, false, 1.0f);

        assertEquals(solution, product);
        assertEquals(aCopy, a);
        assertEquals(bCopy, b);

        // Transpose B.
        a = new FloatMatrix(new float[][]{{1.0f, 2.0f, 3.0f}, {4.0f, 5.0f, 6.0f}, {7.0f, 8.0f, 9.0f}});
        b = new FloatMatrix(new float[][]{{9.0f, 8.0f, 7.0f}, {6.0f, 5.0f, 4.0f}, {3.0f, 2.0f, 1.0f}});
        solution = new FloatMatrix(new float[][]{{46.0f, 28.0f, 10.0f}, {118.0f, 73.0f, 28.0f}, {190.0f, 118.0f, 46.0f}});

        aCopy = a.copy();
        bCopy = b.copy();

        product = a.mul(b, false, true, 1.0f);

        assertEquals(solution, product);
        assertEquals(aCopy, a);
        assertEquals(bCopy, b);

        // Transpose A and B.
        a = new FloatMatrix(new float[][]{{1.0f, 2.0f, 3.0f}, {4.0f, 5.0f, 6.0f}, {7.0f, 8.0f, 9.0f}});
        b = new FloatMatrix(new float[][]{{9.0f, 8.0f, 7.0f}, {6.0f, 5.0f, 4.0f}, {3.0f, 2.0f, 1.0f}});
        solution = new FloatMatrix(new float[][]{{90.0f, 54.0f, 18.0f}, {114.0f, 69.0f, 24.0f}, {138.0f, 84.0f, 30.0f}});

        aCopy = a.copy();
        bCopy = b.copy();

        product = a.mul(b, true, true, 1.0f);

        assertEquals(solution, product);
        assertEquals(aCopy, a);
        assertEquals(bCopy, b);

        // No transpose, scale.
        a = new FloatMatrix(new float[][]{{1.0f, 2.0f, -1.0f}, {2.0f, 0.0f, 1.0f}});
        b = new FloatMatrix(new float[][]{{3.0f, 1.0f}, {0.0f, -1.0f}, {-2.0f, 3.0f}});
        solution = new FloatMatrix(new float[][]{{10.0f, -8.0f}, {8.0f, 10.0f}});

        aCopy = a.copy();
        bCopy = b.copy();

        product = a.mul(b, false, false, 2.0f);

        assertEquals(solution, product);
        assertEquals(aCopy, a);
        assertEquals(bCopy, b);
    }

    @Test
    public void testDot() {
        FloatMatrix x;
        FloatMatrix y;

        x = FloatMatrix.columnVector(new float[]{1.0f, 2.0f, 3.0f});
        y = FloatMatrix.columnVector(new float[]{4.0f, 5.0f, 6.0f});
        assertEquals(32.0f, x.dot(y), EPSILON);
        assertEquals(32.0f, y.dot(x), EPSILON);

        try {
            x = new FloatMatrix(new float[][]{{1.0f, 2.0f}, {3.0f, 4.0f}});
            y = new FloatMatrix(new float[][]{{1.0f, 2.0f}, {3.0f, 4.0f}});
            x.dot(y);
            fail("Failed to throw exception on non-vector dot.");
        } catch (IllegalArgumentException ex) {
            // Expected.
        }

        try {
            x = FloatMatrix.columnVector(new float[]{1.0f, 2.0f, 3.0f});
            y = FloatMatrix.columnVector(new float[]{4.0f, 5.0f});
            x.dot(y);
            fail("Failed to throw exception on mismatched-size dot.");
        } catch (IllegalArgumentException ex) {
            // Expected.
        }
    }

    @Test
    public void testInversion() {
        FloatMatrix matrix;
        FloatMatrix inverse;
        FloatMatrix solution;

        matrix = FloatMatrix.eye(4);
        inverse = matrix.inverse();
        assertEquals(FloatMatrix.eye(4), inverse);

        matrix = new FloatMatrix(new float[][]{{-1.0f, 1.5f}, {1.0f, -1.0f}});
        inverse = matrix.inverse();
        solution = new FloatMatrix(new float[][]{{2.0f, 3.0f}, {2.0f, 2.0f}});
        assertEquals(solution, inverse);

        try {
            matrix = FloatMatrix.zeroes(3, 2);
            inverse = matrix.inverse();
            fail("Did not throw an exception on trying to invert a non-square matrix.");
        } catch (IllegalArgumentException ex) {
            // Expected.
        }

        try {
            matrix = new FloatMatrix(new float[][]{{-1.0f, 2.0f}, {0.5f, -1.0f}});
            inverse = matrix.inverse();
            fail("Did not throw an exception on trying to invert a non-invertable matrix.");
        } catch (ArithmeticException ex) {
            // Expected.
        }
    }

    @Test
    public void testCholeskyDecomposition() {
        FloatMatrix matrix;
        FloatMatrix solution;

        matrix = new FloatMatrix(new float[][]{{4.0f, 12.0f, -16.0f}, {12.0f, 37.0f, -43.0f}, {-16.0f, -43.0f, 98.0f}});
        solution = new FloatMatrix(new float[][]{{2.0f, 0.0f, 0.0f}, {6.0f, 1.0f, 0.0f}, {-8.0f, 5.0f, 3.0f}});

        FloatMatrix result = matrix.choleskyDecomposition();

        // Zero out the unused portion of the matrix so we can test it.
        result.set(0, 1, 0.0f);
        result.set(0, 2, 0.0f);
        result.set(1, 2, 0.0f);

        assertEquals(solution, result);
    }
}
