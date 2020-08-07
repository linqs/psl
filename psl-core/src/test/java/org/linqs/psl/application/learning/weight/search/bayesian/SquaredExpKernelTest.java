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
