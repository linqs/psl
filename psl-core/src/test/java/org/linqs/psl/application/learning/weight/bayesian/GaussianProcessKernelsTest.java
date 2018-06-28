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
        GaussianProcessKernels.Kernel kernel = GaussianProcessKernels.KERNELS.get("squaredExp");
        Config.addProperty("gppker.reldep", 1f);
        float[] x = {1,2,3,4};
        float[] y = {3,4,5,6};
       Assert.assertEquals(0.1353352832366127, kernel.kernel(x,y), 1e-5);
    }
}
