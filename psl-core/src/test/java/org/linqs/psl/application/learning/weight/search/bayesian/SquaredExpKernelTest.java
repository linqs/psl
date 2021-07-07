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
 package org.linqs.psl.application.learning.weight.search.bayesian;

import org.linqs.psl.config.Options;

import org.junit.Assert;
import org.junit.Test;

public class SquaredExpKernelTest {
    public static final double EPSILON = 1e-5;

    @Test
    public void testSquaredExpKernel() {
        Options.WLA_GPP_KERNEL_REL_DEP.set(1.0f);

        float[] x = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] y = {3.0f, 4.0f, 5.0f, 6.0f};

        GaussianProcessKernel kernel = null;

        // Note that config values are fetched on construction.
        // Therefore, a new kernel should be constructed each time the config changes.

        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.OS.toString());
        kernel = new SquaredExpKernel();
        Assert.assertEquals(0.1353352832366127, kernel.kernel(x, y), EPSILON);

        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.SS.toString());
        kernel = new SquaredExpKernel();
        Assert.assertEquals(0.60799952264954815, kernel.kernel(x, y), EPSILON);

        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.LS.toString());
        kernel = new SquaredExpKernel();
        Assert.assertEquals(0.48347071575068623, kernel.kernel(x, y), EPSILON);

        float[] weights = {1.0f, 0.5f, 1.0f, 0.1f};
        GaussianProcessKernel weightedKernel = null;

        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.OS.toString());
        weightedKernel = new SquaredExpKernel(weights);
        Assert.assertEquals(0.22238845301786575, weightedKernel.kernel(x, y), EPSILON);

        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.SS.toString());
        weightedKernel = new SquaredExpKernel(weights);
        Assert.assertEquals(0.60799952264954815, weightedKernel.kernel(x, y), EPSILON);

        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.LS.toString());
        weightedKernel = new SquaredExpKernel(weights);
        Assert.assertEquals(0.48347071575068623, weightedKernel.kernel(x, y), EPSILON);
    }
}
