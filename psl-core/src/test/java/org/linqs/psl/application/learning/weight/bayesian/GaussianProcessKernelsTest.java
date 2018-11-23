package org.linqs.psl.application.learning.weight.bayesian;

import org.junit.Assert;
import org.junit.Test;
import org.linqs.psl.config.Config;

/**
 * Created by sriramsrinivasan on 6/27/18.
 */
public class GaussianProcessKernelsTest {
    @Test
    public void testSquaredExpKernel(){
        GaussianProcessKernels.Kernel kernel = GaussianProcessKernels.kernelProvider("squaredExp", null);
        Config.addProperty("gppker.reldep", 1f);
        Config.addProperty("gppker.space", "OS");
        float[] x = {1,2,3,4};
        float[] y = {3,4,5,6};
        Assert.assertEquals(0.1353352832366127, kernel.kernel(x,y), 1e-5);
        Config.clearProperty("gppker.space");
        Config.addProperty("gppker.space", "SS");
        Assert.assertEquals(0.60799952264954815, kernel.kernel(x,y), 1e-5);
        Config.clearProperty("gppker.space");
        Config.addProperty("gppker.space", "LS");
        Assert.assertEquals(0.48347071575068623, kernel.kernel(x,y), 1e-5);
        float[] weights = {1.0f, 0.5f, 1.0f, 0.1f};
        GaussianProcessKernels.Kernel weightedKernel = new GaussianProcessKernels.SquaredExpKernel(weights);
        Config.clearProperty("gppker.space");
        Config.addProperty("gppker.space", "OS");
        Assert.assertEquals(0.22238845301786575, weightedKernel.kernel(x,y), 1e-5);
        Config.clearProperty("gppker.space");
        Config.addProperty("gppker.space", "SS");
        Assert.assertEquals(0.60799952264954815, weightedKernel.kernel(x,y), 1e-5);
        Config.clearProperty("gppker.space");
        Config.addProperty("gppker.space", "LS");
        Assert.assertEquals(0.48347071575068623, weightedKernel.kernel(x,y), 1e-5);
    }
}
