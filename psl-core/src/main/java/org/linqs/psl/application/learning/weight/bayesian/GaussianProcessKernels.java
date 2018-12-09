package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.config.Config;
import org.linqs.psl.util.FloatMatrix;

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
    public static float REL_DEP_DEFAULT = 1.0f;

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
        /**
         * Compute the kernel function.
         * The passed in points will be untouched and caller keeps ownership.
         */
        public float kernel(float[] point1, float[] point2);
    }

    public static class SquaredExpKernel implements Kernel {
        private FloatMatrix scalingWeights;
        private boolean weighted;
        private float scale;
        private float relDep;
        private Space space;

        /**
         * Weights are the number of groundings for a rule divided by total number of groundings.
         * The idea is that rules with lesser grounding will need bigger jumps to make a difference.
         * Caller loses ownership of the weights.
         */
        public SquaredExpKernel(float[] scalingWeights) {
            this(FloatMatrix.columnVector(scalingWeights), true);
        }

        public SquaredExpKernel() {
            this(null, false);
        }

        private SquaredExpKernel(FloatMatrix scalingWeights, boolean weighted) {
            this.scalingWeights = scalingWeights;
            this.weighted = weighted;

            scale = Config.getFloat(SCALE_KEY, SCALE_DEFAULT);
            relDep = Config.getFloat(REL_DEP_KEY, REL_DEP_DEFAULT);
            space = Space.valueOf(Config.getString(SPACE_KEY, SPACE_DEFAULT).toUpperCase());
        }

        // scale * np.exp( -0.5 * relDep * ||wx - wy||_2)
        @Override
        public float kernel(float[] point1, float[] point2) {
            assert(point1.length == point2.length);

            // TEST(eriq): Can we do this without allocation (make the points matrices?)?
            //  Would mean possible allocations later in method (eg space = LS).

            FloatMatrix pt1 = FloatMatrix.columnVector(point1);
            FloatMatrix pt2 = FloatMatrix.columnVector(point2);

            if (this.weighted) {
                pt1.elementMul(scalingWeights, true);
                pt2.elementMul(scalingWeights, true);
            }

            FloatMatrix diff;

            switch (space) {
                case OS:
                    diff = pt1.elementSub(pt2, false);
                    break;

                case LS:
                    diff = pt1.elementLog(true).elementSub(pt2.elementLog(true));
                    break;

                case SS:
                    // [log(x[i]) - log(x[0]) for i in range(len(point))]

                    float pt1LogBaseline = (float)Math.log(pt1.get(0, 0));
                    float pt2LogBaseline = (float)Math.log(pt2.get(0, 0));

                    pt1.elementLog(true).sub(pt1LogBaseline);
                    pt2.elementLog(true).sub(pt2LogBaseline);

                    diff = pt1.elementSub(pt2);

                    break;

                default:
                    throw new IllegalStateException("Unknown Space: " + space);
            }

            return scale * (float)Math.exp(-0.5 * relDep * diff.norm2());
        }
    }
}
