package org.linqs.psl.application.learning.weight.bayesian.AcquisitionFunctions;

import org.linqs.psl.application.learning.weight.bayesian.GaussianProcessPrior;
import org.linqs.psl.config.Config;

import java.util.List;

/**
 * Upper confidence bound used as AcquisitionFunction.,
 */
public class UCB implements AcquisitionFunction {
    private float exploration;
    private static final String CONFIG_PREFIX = "gpp";
    private static final String EXPLORATION = ".explore";
    private static final float EXPLORATION_VAL = 2.0f;

    public UCB(){
        exploration = Config.getFloat(CONFIG_PREFIX+EXPLORATION, EXPLORATION_VAL);
    }
    //Exploration strategy
    public int getNextPoint(List<GaussianProcessPrior.WeightConfig> configs, int iter){
        int bestConfig = -1;
        float curBestVal = -Float.MAX_VALUE;
        for (int i = 0; i < configs.size(); i++) {
            float curVal = (configs.get(i).valueAndStd.value/exploration) +
                    configs.get(i).valueAndStd.std;
            if(curBestVal < curVal){
                curBestVal = curVal;
                bestConfig = i;
            }
        }
        return bestConfig;
    }
}
