package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.config.Config;
import org.linqs.psl.util.FloatMatrix;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * All kernel methods MUST be threadsafe.
 */
public abstract class GaussianProcessKernel {
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

    protected final FloatMatrix scalingWeights;
    protected final boolean weighted;
    protected final float scale;
    protected final float relDep;
    protected final Space space;

    public GaussianProcessKernel() {
        this(null, false);
    }

    /**
      * Weights are the number of groundings for a rule divided by total number of groundings.
      * The idea is that rules with lesser grounding will need bigger jumps to make a difference.
      * Caller loses ownership of the weights.
      */
    public GaussianProcessKernel(float[] scalingWeights) {
        this(FloatMatrix.columnVector(scalingWeights), true);
    }

    private GaussianProcessKernel(FloatMatrix scalingWeights, boolean weighted) {
        this.scalingWeights = scalingWeights;
        this.weighted = weighted;

        scale = Config.getFloat(SCALE_KEY, SCALE_DEFAULT);
        relDep = Config.getFloat(REL_DEP_KEY, REL_DEP_DEFAULT);
        space = Space.valueOf(Config.getString(SPACE_KEY, SPACE_DEFAULT).toUpperCase());
    }

    /**
     * The actual kernel computation.
     * The passed in points will be mondified throughout the computation.
     * Must be threadsafe.
     */
    public abstract float kernel(FloatMatrix point1, FloatMatrix point2);

    /**
      * Compute the kernel, but use buffers provided by the caller.
      * The use of the buffers must be theadsafe and there are no requirements or guarentees on the
      * contents of the buffers before/after invocation (except that they are sized
      * the same as the points).
      * The matrices will be modified.
      */
    public float kernel(float[] point1, float[] point2, float[] buffer1, float[] buffer2, FloatMatrix matrixShell1, FloatMatrix matrixShell2) {
        assert(point1.length == point2.length);
        assert(buffer1.length == buffer2.length);
        assert(point1.length == buffer1.length);

        for (int i = 0; i < point1.length; i++) {
            buffer1[i] = point1[i];
            buffer2[i] = point2[i];
        }

        matrixShell1.assume(buffer1, buffer1.length, 1);
        matrixShell2.assume(buffer2, buffer2.length, 1);

        return kernel(matrixShell1, matrixShell2);
    }

    /**
      * Compute the kernels, but allocate new buffer for the computation.
      */
    public float kernel(float[] point1, float[] point2) {
        return kernel(point1, point2, new float[point1.length], new float[point2.length], new FloatMatrix(), new FloatMatrix());
    }

    public static GaussianProcessKernel makeKernel(KernelType type, GaussianProcessPrior method) {
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
}
