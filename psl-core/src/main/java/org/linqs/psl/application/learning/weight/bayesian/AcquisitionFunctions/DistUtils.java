package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.apache.commons.math3.distribution.NormalDistribution;

/**
 * Distribution utitilties. Currently for normal distribution.
 */
public class DistUtils {
    private  static NormalDistribution NORM_DIST = new NormalDistribution();

    // returns the cumulative normal distribution function (CNDF)
    // for a standard normal: N(0,1)
    public static float CNDF(double x)
    {
        return (float)NORM_DIST.cumulativeProbability(x);
    }

    public static float NDF(double x){
        return (float)NORM_DIST.density(x);
    }
}
