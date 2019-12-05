package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.config.Config;

import org.junit.Assert;
import org.junit.Test;

public class SquaredExpKernelTest {
    public static final double EPSILON = 1e-5;

    @Test
    public void testSquaredExpKernel() {
        Config.setProperty(GaussianProcessKernel.REL_DEP_KEY, 1.0f);

        float[] x = {1.0f, 2.0f, 3.0f, 4.0f};
        float[] y = {3.0f, 4.0f, 5.0f, 6.0f};

        GaussianProcessKernel kernel = null;

        // Note that config values are fetched on construction.
        // Therefore, a new kernel should be constructed each time the config changes.

        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.OS);
        kernel = GaussianProcessKernel.makeKernel(GaussianProcessKernel.KernelType.SQUARED_EXP, null);
        Assert.assertEquals(0.1353352832366127, kernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.SS);
        kernel = GaussianProcessKernel.makeKernel(GaussianProcessKernel.KernelType.SQUARED_EXP, null);
        Assert.assertEquals(0.60799952264954815, kernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.LS);
        kernel = GaussianProcessKernel.makeKernel(GaussianProcessKernel.KernelType.SQUARED_EXP, null);
        Assert.assertEquals(0.48347071575068623, kernel.kernel(x, y), EPSILON);

        float[] weights = {1.0f, 0.5f, 1.0f, 0.1f};
        GaussianProcessKernel weightedKernel = null;

        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.OS);
        weightedKernel = new SquaredExpKernel(weights);
        Assert.assertEquals(0.22238845301786575, weightedKernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.SS);
        weightedKernel = new SquaredExpKernel(weights);
        Assert.assertEquals(0.60799952264954815, weightedKernel.kernel(x, y), EPSILON);

        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.LS);
        weightedKernel = new SquaredExpKernel(weights);
        Assert.assertEquals(0.48347071575068623, weightedKernel.kernel(x, y), EPSILON);
    }
}
