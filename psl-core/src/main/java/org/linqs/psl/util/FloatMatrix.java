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

import org.linqs.psl.util.HashCode;

import com.github.fommil.netlib.BLAS;
import com.github.fommil.netlib.LAPACK;
import org.netlib.util.intW;

import java.util.Arrays;

/*
 * TODO(eriq): Places with extra allocations:
 *  - lapack_sgesv() - pivots (pass in?)
 *  - inverse() - Data copy and identity (keep some preallocated buffers for work?)
 */

/**
 * A dense float matrix representation and utilities.
 * Internally, BLAS functions are called.
 * Therefore, the representation of the array matches the BLAS api (a column-major 1-D array).
 * Note that this class is written for performance and usability (not feature-richness).

 * Reference:
 *  <a href='http://www.netlib.org/blas/'>BLAS</a>
 *  <a href='http://www.netlib.org/lapack/'>LAPACK</a>
 */
public final class FloatMatrix {
    private float[] data;
    private int numRows;
    private int numCols;

    /**
     * Make an empty matrix.
     * Calling methods on an empty matrix may result in errors.
     */
    public FloatMatrix() {
        data = null;
        numRows = 0;
        numCols = 0;
    }

    /**
     * Make a matrix by assuming the given data.
     * This is not for column/row vectors, use columnVector()/rowVector() for that.
     * The caller keeps ownership of the data.
     *
     * @param data A column-major representation of the data.
     */
    public FloatMatrix(float[] data, int numRows, int numCols) {
        this(data, numRows, numCols, true);
    }

    /**
     * Make a matrix using existing data.
     * This is not for column/row vectors, use columnVector()/rowVector() for that.
     *
     * @param data A column-major representation of the data.
     * @param copy If true, the passed in array will be deep copied.
     *  Otherwise, the caller forfeits control of the data.
     */
    public FloatMatrix(float[] data, int numRows, int numCols, boolean copy) {
        if (data.length != (numRows * numCols)) {
            throw new IllegalArgumentException(String.format(
                    "Length of data (%d) and size of matrix (%d x %d = %d) does not match.",
                    data.length, numRows, numCols, (numRows * numCols)));
        }

        this.numRows = numRows;
        this.numCols = numCols;

        if (copy) {
            this.data = Arrays.copyOf(data, data.length);
        } else {
            this.data = data;
        }
    }

    /**
     * Make a matrix by assuming the given data.
     * The caller forfeits ownership of the data.
     * Note that this constructor will make a copy of the data.
     * Use the 1-D array constructors for copy-less construction.
     */
    public FloatMatrix(float[][] gridData) {
        if (gridData == null || gridData.length == 0) {
            data = null;
            numRows = 0;
            numCols = 0;
            return;
        }

        numRows = gridData.length;
        numCols = gridData[0].length;

        data = new float[numRows * numCols];
        for (int row = 0; row < numRows; row++) {
            if (gridData[row].length != numCols) {
                throw new IllegalArgumentException(String.format(
                        "Matrix does not have consistent number of columns. Expecting %d, found %d (row %d).",
                        numCols, gridData[row].length, row));
            }

            for (int col = 0; col < numCols; col++) {
                data[col * numRows + row] = gridData[row][col];
            }
        }
    }

    /**
     * Take on all the given information.
     * This is just like calling the constructor, except no allocation will occur.
     */
    public void assume(float[] data, int numRows, int numCols) {
        this.data = data;
        this.numRows = numRows;
        this.numCols = numCols;
    }

    /**
     * Make a zero matrix.
     */
    public static FloatMatrix zeroes(int numRows, int numCols) {
        return new FloatMatrix(new float[numRows * numCols], numRows, numCols);
    }

    /**
     * Make a ones matrix.
     */
    public static FloatMatrix ones(int numRows, int numCols) {
        float[] data = new float[numRows * numCols];
        for (int i = 0; i < (numRows * numCols); i++) {
            data[i] = 1.0f;
        }

        return new FloatMatrix(data, numRows, numCols, false);
    }

    /**
     * Make an identity matrix.
     */
    public static FloatMatrix eye(int size) {
        float[] data = new float[size * size];
        for (int i = 0; i < size; i++) {
            data[i * (size + 1)] = 1.0f;
        }

        return new FloatMatrix(data, size, size, false);
    }

    public static FloatMatrix columnVector(float[] data) {
        return columnVector(data, true);
    }

    public static FloatMatrix columnVector(float[] data, boolean copy) {
        return new FloatMatrix(data, data.length, 1, copy);
    }

    public static FloatMatrix rowVector(float[] data) {
        return rowVector(data, true);
    }

    public static FloatMatrix rowVector(float[] data, boolean copy) {
        return new FloatMatrix(data, 1, data.length, copy);
    }

    /**
     * Get a copy of this matrix.
     * Will cause an allocation.
     */
    public FloatMatrix copy() {
        return new FloatMatrix(Arrays.copyOf(data, data.length), numRows, numCols, false);
    }

    /**
     * Get a copy of this matrix's data as a 2D-array.
     * Will cause an allocation.
     */
    public float[][] asGrid() {
        float[][] rtn = new float[numRows][numCols];

        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                rtn[row][col] = data[col * numRows + row];
            }
        }

        return rtn;
    }

    /**
     * Get a copy of this matrix's data as a 1D column-major array.
     * Will cause an allocation.
     */
    public float[] asColumnArray() {
        return Arrays.copyOf(data, data.length);
    }

    /**
     * Get a copy of this matrix's data as a 1D row-major array.
     * Will cause an allocation.
     */
    public float[] asRowArray() {
        float[] rtn = new float[size()];

        for (int row = 0; row < numRows; row++) {
            for (int col = 0; col < numCols; col++) {
                rtn[row * numCols + col] = data[col * numRows + row];
            }
        }

        return rtn;
    }

    public float get(int row, int col) {
        return data[col * numRows + row];
    }

    public void set(int row, int col, float value) {
        data[col * numRows + row] = value;
    }

    public int size() {
        return numRows * numCols;
    }

    public int numRows() {
        return numRows;
    }

    public int numCols() {
        return numCols;
    }

    @Override
    public int hashCode() {
        return HashCode.build(HashCode.build(numRows), numCols);
    }

    @Override
    public boolean equals(Object otherObj) {
        if (this == otherObj) {
            return true;
        }

        if (otherObj == null || !(otherObj instanceof FloatMatrix)) {
            return false;
        }

        FloatMatrix other = (FloatMatrix)otherObj;

        if (numRows != other.numRows || numCols != other.numCols) {
            return false;
        }

        for (int i = 0; i < size(); i++) {
            if (!MathUtils.equals(data[i], other.data[i])) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        if (data == null || numRows == 0) {
            return "[]";
        }

        StringBuilder builder = new StringBuilder();

        builder.append("[");
        for (int i = 0; i < numRows; i++) {
            builder.append("[");

            for (int j = 0; j < numCols; j++) {
                builder.append(get(i, j));

                if (j != (numCols - 1)) {
                    builder.append(", ");
                }
            }

            builder.append("]");

            if (i != (numRows - 1)) {
                builder.append(", ");
            }
        }
        builder.append("]");

        return builder.toString();
    }

    /**
     * Element-wise difference (not in-place).
     */
    public FloatMatrix elementSub(FloatMatrix other) {
        return elementSub(other, false);
    }

    /**
     * Element-wise difference.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix elementSub(FloatMatrix other, boolean inPlace) {
        assert(numRows == other.numRows);
        assert(numCols == other.numCols);

        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] - other.data[i];
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Element-wise addition (not in-place).
     */
    public FloatMatrix elementAdd(FloatMatrix other) {
        return elementAdd(other, false);
    }

    /**
     * Element-wise addition.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix elementAdd(FloatMatrix other, boolean inPlace) {
        assert(numRows == other.numRows);
        assert(numCols == other.numCols);

        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] + other.data[i];
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Element-wise multiplication (not in-place).
     */
    public FloatMatrix elementMul(FloatMatrix other) {
        return elementMul(other, false);
    }

    /**
     * Element-wise multiplication.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix elementMul(FloatMatrix other, boolean inPlace) {
        assert(numRows == other.numRows);
        assert(numCols == other.numCols);

        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] * other.data[i];
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Element-wise division (not in-place).
     */
    public FloatMatrix elementDiv(FloatMatrix other) {
        return elementDiv(other, false);
    }

    /**
     * Element-wise division.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix elementDiv(FloatMatrix other, boolean inPlace) {
        assert(numRows == other.numRows);
        assert(numCols == other.numCols);

        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] / other.data[i];
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Element-wise natural log (not in-place).
     */
    public FloatMatrix elementLog() {
        return elementLog(false);
    }

    /**
     * Element-wise natural log.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix elementLog(boolean inPlace) {
        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = (float)Math.log(data[i]);
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Scalar difference (not in-place).
     */
    public FloatMatrix sub(float val) {
        return sub(val, false);
    }

    /**
     * Scalar difference.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix sub(float val, boolean inPlace) {
        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] - val;
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Scalar addition (not in-place).
     */
    public FloatMatrix add(float val) {
        return add(val, false);
    }

    /**
     * Scalar addition.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix add(float val, boolean inPlace) {
        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] + val;
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Scalar multiplication (not in-place).
     */
    public FloatMatrix mul(float val) {
        return mul(val, false);
    }

    /**
     * Scalar multiplication.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix mul(float val, boolean inPlace) {
        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] * val;
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Scalar division (not in-place).
     */
    public FloatMatrix div(float val) {
        return div(val, false);
    }

    /**
     * Scalar division.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix div(float val, boolean inPlace) {
        float[] result;
        if (inPlace) {
            result = data;
        } else {
            result = new float[size()];
        }

        for (int i = 0; i < size(); i++) {
            result[i] = data[i] / val;
        }

        if (inPlace) {
            return this;
        } else {
            return new FloatMatrix(result, numRows, numCols, false);
        }
    }

    /**
     * Get the 1-norm (sum of absolute values).
     */
    public float norm1() {
        float sum = 0;

        for (int i = 0; i < size(); i++) {
            sum += Math.abs(data[i]);
        }

        return sum;
    }

    /**
     * Get the 2-norm (Euclidean norm) (square root of sum of squared values).
     */
    public float norm2() {
        float sum = 0;

        for (int i = 0; i < size(); i++) {
            sum += Math.pow(data[i], 2);
        }

        return (float)Math.sqrt(sum);
    }

    /**
     * Transpose.
     * Note that transposition can often be done as part of a BLAS/LAPACK solve.
     * If you are trying to do a greater solve, then use the transpose options there rather than
     * this method.
     */
    public FloatMatrix transpose() {
        FloatMatrix result = FloatMatrix.zeroes(numCols, numRows);

        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                result.set(j, i, get(i, j));
            }
        }

        return result;
    }

    /**
     * Matrix multiplication without transposition or scaling.
     */
    public FloatMatrix mul(FloatMatrix b) {
        return mul(b, false, false, 1.0f);
    }

    /**
     * Matrix multiplication.
     * Performs: alpha * AB.
     * Both A and B can be transposed.
     */
    public FloatMatrix mul(FloatMatrix b, boolean transposeA, boolean transposeB, float alpha) {
        return mul(b, null, transposeA, transposeB, alpha, 0.0f);
    }

    /**
     * Matrix multiplication.
     * Performs: alpha * AB + beta * C.
     * Both A and B can be transposed.
     * The result will be written INTO C (and returned).
     *
     * @param c This matrix is used to store the result from BLAS, and it will be returned.
     *  If null, a new matrix will be allocated for the result.
     */
    public FloatMatrix mul(FloatMatrix b, FloatMatrix c, boolean transposeA, boolean transposeB, float alpha, float beta) {
        return blas_sgemm(b, c, transposeA, transposeB, alpha, beta);
    }

    /**
     * Dot product.
     */
    public float dot(FloatMatrix other) {
        return blas_sdot(other);
    }

    /**
     * Compute the inversion of this matrix.
     * There is no in-place variant of this.
     * Note that you typically don't want to compute a direct inversion:
     * https://www.johndcook.com/blog/2010/01/19/dont-invert-that-matrix/
     */
    public FloatMatrix inverse() {
        if (numRows != numCols) {
            throw new IllegalArgumentException(String.format(
                    "Cannot invert a non-square matrix (%d x %d).",
                    numRows, numCols));
        }

        // LAPACK overwrites the data.
        FloatMatrix dataCopy = this.copy();
        FloatMatrix identity = FloatMatrix.eye(numRows);

        try {
            // The result (x) is put into the param (identity).
            dataCopy.lapack_sgesv(identity);
        } catch (ArithmeticException ex) {
            throw new ArithmeticException("Non-invertible matrix: " + toString());
        }

        return identity;
    }

    /**
     * Cholesky factorization (not in-place).
     */
    public FloatMatrix choleskyDecomposition() {
        return choleskyDecomposition(false);
    }

    /**
     * Compute the Cholesky factorization of this real symmetric positive definite matrix.
     * A = L * L**T
     * The lower triangular result will be returned.
     * Will throw if the matrix is not symmetric positive definite.
     * The unused portion of the matrix is to be ignored and no guarantees are given on it values.
     *
     * @param inPlace If true, then this matrix will be used and no copies made.
     * @return A matrix (new or this) expressing the result.
     */
    public FloatMatrix choleskyDecomposition(boolean inPlace) {
        FloatMatrix result = this;
        if (!inPlace) {
            result = copy();
        }

        result.lapack_spotrf(false);

        return result;
    }

    /**
     * Call sgesv from LAPACK.
     * http://www.netlib.org/lapack/explore-html/d7/de8/sgesv_8f.html
     *
     * It is strongly discouraged to call BLAS/LAPACK methods directly.
     * Instead, use other higher level methods.
     *
     * Solves for X in A * X = B.
     * Also factors A using a LU decomposition.
     * The factored A takes the form: A = P * L * U.
     * Where P is a permutation matrix, L is unit lower triangular, and U is upper triangular.
     * This (the context matrix) is A and B becomes X on successful completion.
     * Note that A may also be out of order as specified by the standard LAPACK IPIV (returned).
     *
     * @param b The matrix on the LHS in this equation.
     *  X will be put into b on successful completion of this method.
     * @return The LAPACK standard IPIV for A.
     */
    public int[] lapack_sgesv(FloatMatrix b) {
        if (numRows != numCols) {
            throw new IllegalArgumentException(String.format(
                    "sgesv requires a square A matrix, got (%d x %d).",
                    numRows, numCols));
        }

        int[] pivots = new int[numRows];
        intW result = new intW(0);

        LAPACK.getInstance().sgesv(
                numRows,  // N - Dimension of A.
                b.numCols,  // NRHS - Cols of B.
                data,  // A
                numRows,  // LDA
                pivots,  // IPIV - Pivot indices.
                b.data,  // B
                b.numRows,  // LDB
                result  // INFO - Result
        );

        if (result.val < 0) {
            throw new IllegalArgumentException(String.format(
                    "Error in the %d argument to sgesv.",
                    result.val * -1));
        }

        if (result.val > 0) {
            throw new ArithmeticException(String.format(
                    "Error in sgesv. U(%d, %d) is singular, so the solution could not be computed.",
                    result.val, result.val));
        }

        return pivots;
    }

    /**
     * Call spotrf from LAPACK.
     * http://www.netlib.org/lapack/explore-html/dd/d7e/spotrf2_8f.html
     *
     * It is strongly discouraged to call BLAS/LAPACK methods directly.
     * Instead, use other higher level methods.
     *
     * Computes the Cholesky factorization of a real symmetric positive definite matrix A.
     *
     * The factorization has the form:
     *  A = U**T * U, if |upper| = true, or
     *  A = L  * L**T, if |upper| = false,
     * where U is an upper triangular matrix and L is lower triangular.
     *
     * This (the context matrix) is A.
     * Upon successful completion, this matrix will have U/L in it (depending on |upper|).
     *
     * @param upper True if the upper triangle is to be used, false for the lower.
     */
    public void lapack_spotrf(boolean upper) {
        if (numRows != numCols) {
            throw new IllegalArgumentException(String.format(
                    "spotrf requires a square A matrix, got (%d x %d).",
                    numRows, numCols));
        }

        String uplo = "U";
        if (!upper) {
            uplo = "L";
        }

        intW result = new intW(0);

        LAPACK.getInstance().spotrf(
                uplo,  // UPLO - Upper or lower?
                numRows,  // N - Dimension of A.
                data,  // A
                numRows,  // LDA
                result  // INFO - Result
        );

        if (result.val < 0) {
            throw new IllegalArgumentException(String.format(
                    "Error in the %d argument to spotrf.",
                    result.val * -1));
        }

        if (result.val > 0) {
            throw new ArithmeticException(String.format(
                    "Error in spotrf (%d). Matrix is not positive definite.",
                    result.val));
        }
    }

    /**
     * Call sgemm from BLAS.
     * http://www.netlib.org/lapack/explore-html/d4/de2/sgemm_8f.html
     *
     * It is strongly discouraged to call BLAS/LAPACK methods directly.
     * Instead, use other higher level methods.
     *
     * Performs: alpha * A * B + beta * C.
     * Both A and B can be transposed.
     * The result will be put into C (and returned).
     *
     * This (the context matrix) is A.
     *
     * @param b The matrix used as B.
     * @param c The matrix used as C.
     *  May be null, and a new (zero) matrix will get created and used.
     * @param transposeA True if A should be transposed.
     * @param transposeB True if B should be transposed.
     * @param alpha The multiplier on AB.
     * @param beta The multiplier on C.
     * @return C (which will also be updated in-place).
     */
    public FloatMatrix blas_sgemm(
            FloatMatrix b, FloatMatrix c,
            boolean transposeA, boolean transposeB,
            float alpha, float beta) {
        String transA = "N";
        int aNumRows = numRows;
        int aNumCols = numCols;

        String transB = "N";
        int bNumRows = b.numRows;
        int bNumCols = b.numCols;

        if (transposeA) {
            transA = "T";
            aNumRows = numCols;
            aNumCols = numRows;
        }

        if (transposeB) {
            transB = "T";
            bNumRows = b.numCols;
            bNumCols = b.numRows;
        }

        if (aNumCols != bNumRows) {
            throw new IllegalArgumentException(String.format(
                    "Cannot multiply matrices of (post transposed) dimensions (%d x %d) and (%d x %d).",
                    numRows, numCols, b.numRows, b.numCols));
        }

        if (c == null) {
            c = FloatMatrix.zeroes(aNumRows, bNumCols);
            beta = 0.0f;
        }

        BLAS.getInstance().sgemm(
                transA,  // TRANSA
                transB,  // TRANSB
                aNumRows,  // M - rows of op(A)
                bNumCols,  // N - cols of op(B)
                aNumCols,  // K - cols of op(A) and rows of op(B)
                alpha,  // ALPHA
                data,  // A
                aNumRows,  // LDA
                b.data,  // B
                bNumRows,  // LDB
                beta,  // BETA
                c.data,  // C
                c.numRows  // LDC
        );

        return c;
    }

    /**
     * Call sdot from BLAS.
     * http://www.netlib.org/lapack/explore-html/d0/d16/sdot_8f.html
     *
     * It is strongly discouraged to call BLAS/LAPACK methods directly.
     * Instead, use other higher level methods.
     *
     * Get the dot product of X and Y.
     * This (the context matrix) is X.
     *
     * @param y The other matrix in this dot product.
     * @return The dot product.
     */
    public float blas_sdot(FloatMatrix y) {
        if ((numRows != 1 && numCols != 1) || (y.numRows != 1 && y.numCols != 1)) {
            throw new IllegalArgumentException(String.format(
                    "sdot only works with vectors. Got (%d x %d) and (%d x %d).",
                    numRows, numCols,
                    y.numRows, y.numCols));
        }

        if (size() != y.size()) {
            throw new IllegalArgumentException(String.format(
                    "sdot only works with same sized vectors. Got %d and %d.",
                    size(), y.size()));
        }

        float dot = BLAS.getInstance().sdot(
                size(),  // N - Size of both vectors.
                data,  // X
                1,  // INCX
                y.data,  // Y
                1  // INCY
        );

        return dot;
    }
}
