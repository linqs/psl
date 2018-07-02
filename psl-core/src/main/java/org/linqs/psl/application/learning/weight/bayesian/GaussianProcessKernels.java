package org.linqs.psl.application.learning.weight.bayesian;

import com.google.common.collect.Maps;
import org.jblas.FloatMatrix;
import org.linqs.psl.config.Config;

import java.util.Collections;
import java.util.Map;

/**
 * Created by sriramsrinivasan on 6/27/18.
 */
public class GaussianProcessKernels {

    public static Map<String, Kernel> KERNELS;
    private static final String CONFIG_PREFIX = "gppker";
    private static final String SCALE_PARAM = ".scale";
    private static final String REL_DEP_PARAM = ".reldep";
    private static float SCALE = 1.0f;
    private static float REL_DEP = 100.0f;

    static {
        Map<String, Kernel> temp = Maps.newHashMap();
        temp.put("squaredExp", new SquaredExpKernel());
        KERNELS = Collections.unmodifiableMap(temp);
    }

    interface Kernel{
        float kernel(float[] point1, float[] point2);
    }
    static class SquaredExpKernel implements Kernel{

        //scale * np.exp( -0.5 * relDep * ||x - y||_2)
        @Override
        public float kernel(float[] point1, float[] point2) {
            float scale = Config.getFloat(CONFIG_PREFIX+SCALE_PARAM, SCALE);
            float relDep = Config.getFloat(CONFIG_PREFIX+REL_DEP_PARAM, REL_DEP);
            FloatMatrix pt1 = new FloatMatrix(point1);
            FloatMatrix pt2 = new FloatMatrix(point2);
            final FloatMatrix diff = pt1.sub(pt2);
            return scale * (float)Math.exp(-0.5 * relDep * diff.norm2());
        }
    }
}
