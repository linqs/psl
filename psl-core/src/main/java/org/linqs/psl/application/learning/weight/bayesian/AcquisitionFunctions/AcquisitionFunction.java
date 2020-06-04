package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPrior;

import java.util.List;

/**
 * An interface to accommodate different AcquisitionFunction.
 */
public interface AcquisitionFunction {
    //Exploration strategy
    public int getNextPoint(List<GaussianProcessPrior.WeightConfig> configs, int iter);
}
