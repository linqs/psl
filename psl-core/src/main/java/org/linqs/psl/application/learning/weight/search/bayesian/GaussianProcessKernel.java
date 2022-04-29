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
package org.linqs.psl.application.learning.weight.search.bayesian;

import org.linqs.psl.config.Options;
import org.linqs.psl.util.FloatMatrix;

/**
 * All kernel methods MUST be threadsafe.
 */
public abstract class GaussianProcessKernel {
    public static enum Space {
        SS, OS, LS
    }

    protected final FloatMatrix scalingWeights;
    protected final boolean weighted;
    protected final float scale;
    protected final float relDep;
    protected final Space space;

    public GaussianProcessKernel() {
        this(null, false);
    }

    /**
      * Weights are the number of groundings for a rule divided by total number of groundings.
      * The idea is that rules with lesser grounding will need bigger jumps to make a difference.
      * Caller loses ownership of the weights.
      */
    public GaussianProcessKernel(float[] scalingWeights) {
        this(FloatMatrix.columnVector(scalingWeights), true);
    }

    private GaussianProcessKernel(FloatMatrix scalingWeights, boolean weighted) {
        this.scalingWeights = scalingWeights;
        this.weighted = weighted;

        scale = Options.WLA_GPP_KERNEL_SCALE.getFloat();
        relDep = Options.WLA_GPP_KERNEL_REL_DEP.getFloat();
        space = Space.valueOf(Options.WLA_GPP_KERNEL_SPACE.getString().toUpperCase());
    }

    /**
     * The actual kernel computation.
     * The passed in points will be mondified throughout the computation.
     * Must be threadsafe.
     */
    public abstract float kernel(FloatMatrix point1, FloatMatrix point2);

    /**
      * Compute the kernel, but use buffers provided by the caller.
      * The use of the buffers must be theadsafe and there are no requirements or guarantees on the
      * contents of the buffers before/after invocation (except that they are sized
      * the same as the points).
      * The matrices will be modified.
      */
    public float kernel(float[] point1, float[] point2, float[] buffer1, float[] buffer2, FloatMatrix matrixShell1, FloatMatrix matrixShell2) {
        assert(point1.length == point2.length);
        assert(buffer1.length == buffer2.length);
        assert(point1.length == buffer1.length);

        for (int i = 0; i < point1.length; i++) {
            buffer1[i] = (float)point1[i];
            buffer2[i] = (float)point2[i];
        }

        matrixShell1.assume(buffer1, buffer1.length, 1);
        matrixShell2.assume(buffer2, buffer2.length, 1);

        return kernel(matrixShell1, matrixShell2);
    }

    /**
      * Compute the kernels, but allocate new buffer for the computation.
      */
    public float kernel(float[] point1, float[] point2) {
        return kernel(point1, point2, new float[point1.length], new float[point2.length], new FloatMatrix(), new FloatMatrix());
    }
}
