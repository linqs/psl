package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.config.Config;

import org.junit.Assert;
import org.junit.Test;

public class GaussianProcessKernelsTest {
    public static final double EPSILON = 1e-5;

    @Test
    public void testSquaredExpKernel() {
        Config.setProperty(GaussianProcessKernels.REL_DEP_KEY, 1.0f);

        float[] x = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] y = {3.0f, 4.0f, 5.0f, 6.0f};

        GaussianProcessKernels.Kernel kernel = null;

        // Note that config values are fetched on construction.
        // Therefore, a new kernel should be constructed each time the config changes.

        Config.setProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.OS);
        kernel = GaussianProcessKernels.makeKernel(GaussianProcessKernels.KernelType.SQUARED_EXP, null);
        Assert.assertEquals(0.1353352832366127, kernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.SS);
        kernel = GaussianProcessKernels.makeKernel(GaussianProcessKernels.KernelType.SQUARED_EXP, null);
        Assert.assertEquals(0.60799952264954815, kernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.LS);
        kernel = GaussianProcessKernels.makeKernel(GaussianProcessKernels.KernelType.SQUARED_EXP, null);
        Assert.assertEquals(0.48347071575068623, kernel.kernel(x, y), EPSILON);

        float[] weights = {1.0f, 0.5f, 1.0f, 0.1f};
        GaussianProcessKernels.Kernel weightedKernel = null;

        Config.setProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.OS);
        weightedKernel = new GaussianProcessKernels.SquaredExpKernel(weights);
        Assert.assertEquals(0.22238845301786575, weightedKernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.SS);
        weightedKernel = new GaussianProcessKernels.SquaredExpKernel(weights);
        Assert.assertEquals(0.60799952264954815, weightedKernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.LS);
        weightedKernel = new GaussianProcessKernels.SquaredExpKernel(weights);
        Assert.assertEquals(0.48347071575068623, weightedKernel.kernel(x, y), EPSILON);
    }
}
