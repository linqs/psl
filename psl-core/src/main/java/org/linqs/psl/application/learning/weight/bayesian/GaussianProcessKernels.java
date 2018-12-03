package org.linqs.psl.application.learning.weight.bayesian;

import org.jblas.FloatMatrix;
import org.jblas.MatrixFunctions;
import org.linqs.psl.config.Config;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class GaussianProcessKernels {
    public static final String CONFIG_PREFIX = "gppker";

    public static final String SCALE_KEY = CONFIG_PREFIX + ".scale";
    public static float SCALE_DEFAULT = 1.0f;

    // Smaller means longer dependence. Smaller better when number of rules large
    // TODO(sriram): learn this from data.
    public static final String REL_DEP_KEY = CONFIG_PREFIX + ".reldep";
    public static float REL_DEP_DEFAULT = 0.01f;

    public static enum Space {
        SS, OS, LS
    }

    public static final String SPACE_KEY = CONFIG_PREFIX + ".space";
    public static final String SPACE_DEFAULT = Space.SS.toString();

    public static enum KernelType {
        SQUARED_EXP, WEIGHTED_SQUARED_EXP
    }

    public static Kernel makeKernel(KernelType type, GaussianProcessPrior method) {
        switch (type) {
            case SQUARED_EXP:
                return new SquaredExpKernel();

            case WEIGHTED_SQUARED_EXP:
                int[] counts = method.computeScalingFactor();

                float max = 0.0f;
                for (int i = 0; i < counts.length; i++) {
                    if (counts[i] > max) {
                        max = counts[i];
                    }
                }

                float[] scale = new float[counts.length];
                for (int i = 0; i < counts.length; i++) {
                    scale[i] = counts[i] / max;
                }

                return new SquaredExpKernel(scale);

            default:
                throw new IllegalStateException("Unknown KernelType: " + type);
        }
    }

    public interface Kernel {
        public float kernel(float[] point1, float[] point2);
    }

    public static class SquaredExpKernel implements Kernel {
        private FloatMatrix weights;
        private boolean weighted;
        private float scale;
        private float relDep;
        private Space space;

        // Weights are the number of groundings for a rule divided by total number of groundings.
        // The idea is that rules with lesser grounding will need bigger jumps to make a difference.
        public SquaredExpKernel(float[] weights) {
            this(new FloatMatrix(weights), true);
        }

        public SquaredExpKernel() {
            this(null, false);
        }

        public SquaredExpKernel(FloatMatrix weights, boolean weighted) {
            this.weights = weights;
            this.weighted = weighted;

            scale = Config.getFloat(SCALE_KEY, SCALE_DEFAULT);
            relDep = Config.getFloat(REL_DEP_KEY, REL_DEP_DEFAULT);
            space = Space.valueOf(Config.getString(SPACE_KEY, SPACE_DEFAULT).toUpperCase());
        }

        // scale * np.exp( -0.5 * relDep * ||wx - wy||_2)
        @Override
        public float kernel(float[] point1, float[] point2) {
            FloatMatrix pt1 = new FloatMatrix(point1);
            FloatMatrix pt2 = new FloatMatrix(point2);

            if (this.weighted) {
                pt1 = pt1.mul(this.weights);
                pt2 = pt2.mul(this.weights);
            }

            FloatMatrix diff = null;

            switch (space) {
                case OS:
                    diff = pt1.sub(pt2);
                    break;

                case LS:
                    diff = MatrixFunctions.log(pt1).sub(MatrixFunctions.log(pt2));
                    break;

                case SS:
                    pt1 = MatrixFunctions.log(pt1).sub((float)Math.log(pt1.get(0)));
                    pt2 = MatrixFunctions.log(pt2).sub((float)Math.log(pt2.get(0)));
                    diff = pt1.sub(pt2);
                    break;

                default:
                    throw new IllegalStateException("Unknown Space: " + space);
            }

            return scale * (float)Math.exp(-0.5 * relDep * diff.norm2());
        }
    }
}
