package org.linqs.psl.application.learning.weight.bayesian;

import com.google.common.collect.Maps;

import java.util.Collections;
import java.util.Map;

/**
 * Created by sriramsrinivasan on 6/27/18.
 */
public class GaussianProcessKernels {

    public static Map<String, Kernel> KERNELS;
    {
        Map<String, Kernel> temp = Maps.newHashMap();
        temp.put("gaussian", new GaussianKernel());
        KERNELS = Collections.unmodifiableMap(temp);
    }

    interface Kernel{
        float kernel(float[] point1, float[] point2);
    }
    class GaussianKernel implements Kernel{

        @Override
        public float kernel(float[] point1, float[] point2) {
            return 0;
        }
    }
}
