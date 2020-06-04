package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPrior;
import org.linqs.psl.config.Config;

import java.util.List;

/**
 * Probability of improvement is used as AcquisitionFunction.
 */
public class PI implements AcquisitionFunction {
    private float exploration;
    private static final String CONFIG_PREFIX = "gpp";
    private static final String EXPLORATION = ".explore";
    private static final float EXPLORATION_VAL = 0.0f;

    public PI(){
        exploration = Config.getFloat(CONFIG_PREFIX+EXPLORATION, EXPLORATION_VAL);
    }
    //Exploration strategy
    public int getNextPoint(List<GaussianProcessPrior.WeightConfig> configs, int iter){
        int bestConfig = -1;
        float curBestVal = -Float.MAX_VALUE;
        float max_so_far = 0;
        for (int i = 0; i < configs.size(); i++) {
            float curVal = DistUtils.CNDF((configs.get(i).valueAndStd.value - exploration) /
                    configs.get(i).valueAndStd.std);
            if (max_so_far < configs.get(i).valueAndStd.value){
                max_so_far = configs.get(i).valueAndStd.value;
            }
            if(curBestVal < curVal){
                curBestVal = curVal;
                bestConfig = i;
            }
        }
        exploration = max_so_far;
        return bestConfig;
    }
}
