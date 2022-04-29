/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.linqs.psl.application.learning.weight.search.bayesian;

import org.linqs.psl.util.FloatMatrix;

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
