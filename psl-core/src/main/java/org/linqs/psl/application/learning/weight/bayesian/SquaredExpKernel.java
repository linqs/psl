package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.config.Config;
import org.linqs.psl.util.FloatMatrix;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class SquaredExpKernel extends GaussianProcessKernel {
    public SquaredExpKernel() {
        super();
    }

    public SquaredExpKernel(float[] scalingWeights) {
        super(scalingWeights);
    }

    // scale * np.exp( -0.5 * relDep * ||wx - wy||_2)
    @Override
    public float kernel(FloatMatrix pt1, FloatMatrix pt2) {
        assert(pt1.size() == pt2.size());

        if (this.weighted) {
            pt1.elementMul(scalingWeights, true);
            pt2.elementMul(scalingWeights, true);
        }

        FloatMatrix diff;

        switch (space) {
            case OS:
                // pt1 overwritten.
                diff = pt1.elementSub(pt2, true);
                break;

            case LS:
                // pt1 overwritten.
                diff = pt1.elementLog(true).elementSub(pt2.elementLog(true), true);
                break;

            case SS:
                // [log(x[i]) - log(x[0]) for i in range(len(point))]

                float pt1LogBaseline = (float)Math.log(pt1.get(0, 0));
                float pt2LogBaseline = (float)Math.log(pt2.get(0, 0));

                pt1.elementLog(true).sub(pt1LogBaseline, true);
                pt2.elementLog(true).sub(pt2LogBaseline, true);

                // pt1 overwritten.
                diff = pt1.elementSub(pt2, true);

                break;

            default:
                throw new IllegalStateException("Unknown Space: " + space);
        }

        return scale * (float)Math.exp(-0.5 * relDep * diff.norm2());
    }
}
