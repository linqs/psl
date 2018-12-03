package org.linqs.psl.application.learning.weight.bayesian;

import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;
import org.linqs.psl.config.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by sriramsrinivasan on 6/27/18.
 */
public class GaussianProcessKernels {

    public static Set<String> KERNELS;
    public static final String CONFIG_PREFIX = "gppker";
    private static final String SCALE_PARAM = ".scale";
    private static final String REL_DEP_PARAM = ".reldep";
    public static final String SPACE = ".space";
    public static final String SQUARED_EXP = "squaredExp";
    public static final String WEIGHTED_SQUARED_EXP = "weightedSquaredExp";
    private static float SCALE = 1.0f;
    //Smaller means longer dependence. Smaller better when number of rules large
    // FUTURE WORK: TODO: learn this from data.
    private static float REL_DEP = 0.01f;

    enum Spaces {
        SS, OS, LS
    }

    static {
        Set<String> temp = new HashSet<String>();
        temp.add(SQUARED_EXP);
        temp.add(WEIGHTED_SQUARED_EXP);
        KERNELS = Collections.unmodifiableSet(temp);
    }

    static Kernel kernelProvider(String name, GaussianProcessPrior method){
        if (name.equals(SQUARED_EXP)){
            return new SquaredExpKernel();
        } else if (name.equals(WEIGHTED_SQUARED_EXP)){
            int[] counts = method.computeScalingFactor();
            float max = 0;
            for (int i = 0; i < counts.length; i++) {
                max = max>counts[i]?max:counts[i];
            }
            float[] scale = new float[counts.length];
            for (int i = 0; i < counts.length; i++) {
                scale[i] = counts[i]/max;
            }
            return new SquaredExpKernel(scale);
        } else {
            throw new RuntimeException("Allowed only one of two kernels. Asked for " + name +
                    ". Allowed squaredExp, weightedSquaredExp");
        }
    }

    interface Kernel{
        float kernel(float[] point1, float[] point2);
    }
    static class SquaredExpKernel implements Kernel{
        FloatMatrix weights;
        boolean weighted;
        // Weights are the number of groundings for a rule divided by total number of groundings.
        // Idea is that, rules with lesser grounding will need bigger jumps to make a difference.
        SquaredExpKernel(float[] weights){
            this.weights = new FloatMatrix(weights);
            this.weighted = true;
        }
        SquaredExpKernel(){
            this.weighted = false;
        }

        //scale * np.exp( -0.5 * relDep * ||wx - wy||_2)
        @Override
        public float kernel(float[] point1, float[] point2) {
            float scale = Config.getFloat(CONFIG_PREFIX+SCALE_PARAM, SCALE);
            float relDep = Config.getFloat(CONFIG_PREFIX+REL_DEP_PARAM, REL_DEP);
            String space = Config.getString(CONFIG_PREFIX+SPACE, Spaces.SS.toString());
            FloatMatrix pt1 = new FloatMatrix(point1);
            FloatMatrix pt2 = new FloatMatrix(point2);
            if (this.weighted) {
                pt1 = pt1.mul(this.weights);
                pt2 = pt2.mul(this.weights);
            }
            FloatMatrix diff = null;
            if (space.equals(Spaces.OS.toString())) {
                diff = pt1.sub(pt2);
            } else if (space.equals(Spaces.LS.toString())) {
                diff = MatrixFunctions.log(pt1).sub(MatrixFunctions.log(pt2));
            } else if (space.equals(Spaces.SS.toString())) {
                pt1 = MatrixFunctions.log(pt1).sub((float)Math.log(pt1.get(0)));
                pt2 = MatrixFunctions.log(pt2).sub((float)Math.log(pt2.get(0)));
                diff = pt1.sub(pt2);
            } else {
                throw new RuntimeException("Illegal space specified in GP kernel. Allowed only " + Spaces.values());
            }
            return scale * (float)Math.exp(-0.5 * relDep * diff.norm2());
        }
    }
}
