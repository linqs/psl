package org.linqs.psl.application.learning.weight.bayesian;

import com.google.common.collect.Lists;
import org.apache.commons.lang.ArrayUtils;
import org.jblas.FloatMatrix;
import org.jblas.Solve;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.RandUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created by sriramsrinivasan on 6/22/18.
 */
public class GaussianProcessPrior extends WeightLearningApplication {

    private static final Logger log = LoggerFactory.getLogger(GaussianProcessPrior.class);
    private static final String CONFIG_PREFIX = "gpp";
    private static final String KERNEL = ".kernel";
    private static final String NUM_ITER = ".maxiter";
    private static final String MAX_CONFIGS_STR = ".maxconfigs";
    private static final String EXPLORATION = ".explore";
    private static final String DEFAULT_KERNEL = "squaredExp";
    private static final int MAX_CONFIGS = 1000000;
    private static final int MAX_NUM_ITER = 25;
    private static final float EXPLORATION_VAL = 1.0f;
    private static final String RANDOM_CONFIGS_ONLY = ".randomConfigsOnly";
    private FloatMatrix knownDataStdInv;
    private GaussianProcessKernels.Kernel kernel;
    private int maxIterNum;
    private int maxConfigs;
    private float exploration;
    private boolean randomConfigsOnly;
    private List<WeightConfig> configs;
    private List<WeightConfig> exploredConfigs;
    FloatMatrix blasYKnown;

    public GaussianProcessPrior(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB, false);
        String kernel_name = Config.getString(CONFIG_PREFIX+KERNEL, DEFAULT_KERNEL);
        if (!GaussianProcessKernels.KERNELS.keySet().contains(kernel_name)){
            throw new IllegalArgumentException("No kernel named - " + kernel_name + ", exists.");
        }
        //Very important to define a good kernel.
        kernel = GaussianProcessKernels.KERNELS.get(kernel_name);
        maxIterNum = Config.getInt(CONFIG_PREFIX+NUM_ITER, MAX_NUM_ITER);
        maxConfigs = Config.getInt(CONFIG_PREFIX+MAX_CONFIGS_STR, MAX_CONFIGS);
        exploration = Config.getFloat(CONFIG_PREFIX+EXPLORATION, EXPLORATION_VAL);
        randomConfigsOnly = Config.getBoolean(CONFIG_PREFIX+RANDOM_CONFIGS_ONLY, true);
    }

    public GaussianProcessPrior(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    private void reset(){
        configs = getConfigs();
        exploredConfigs = Lists.newArrayList();
    }
    @Override
    protected void doLearn() {
        reset();
        List<Float> exploredFnVal = Lists.newArrayList();
        final ComputePredFnValWorker fnValWorker = new ComputePredFnValWorker();
        int iter = 0;
        WeightConfig bestConfig = null;
        float bestVal = -Float.MAX_VALUE;
        do{
            int nextPoint = getNextPoint(configs);
            WeightConfig config = configs.get(nextPoint);
            exploredConfigs.add(config);
            configs.remove(nextPoint);
            float fnVal = getFunctionValue(config);
            exploredFnVal.add(fnVal);
            config.valueAndStd.value = fnVal;
            config.valueAndStd.std = 0f;
            if (bestVal < fnVal) {
                bestVal = fnVal;
                bestConfig = config;
            }
            log.info("Round " + (iter+1) +
                    "- config picked - " + exploredConfigs.get(iter));
            log.info("Round " + (iter+1) +
                    "- Best config so far - " + bestConfig);
            final int numKnown = exploredFnVal.size();
            knownDataStdInv = new FloatMatrix(numKnown, numKnown);
            for (int i = 0; i < numKnown; i++) {
                for (int j = 0; j < numKnown; j++) {
                    knownDataStdInv.put(i, j, kernel.kernel(exploredConfigs.get(i).config,
                            exploredConfigs.get(j).config));
                }
            }
            knownDataStdInv = Solve.solve(knownDataStdInv, FloatMatrix.eye(numKnown));
            float[] yKnown = ArrayUtils.toPrimitive(
                    exploredFnVal.toArray(new Float[numKnown]));
            blasYKnown = new FloatMatrix(yKnown);
            Parallel.foreach(configs, fnValWorker);
            iter++;
        }while(iter < maxIterNum && configs.size() > 0);
        setWeights(bestConfig);
        log.info("Best config: " + bestConfig);

    }

    private class ComputePredFnValWorker extends Parallel.Worker<WeightConfig>{

        @Override
        public void work(int index, WeightConfig item) {
            ValueAndStd valAndStd = predictFnValAndStd(configs.get(index).config, exploredConfigs, blasYKnown);
            configs.get(index).valueAndStd = valAndStd;
        }
    }

    private void setWeights(WeightConfig config) {
        for (int i = 0; i < mutableRules.size(); i++) {
            mutableRules.get(i).setWeight(config.config[i]);
        }
        inMPEState = false;
    }

    private List<WeightConfig> getConfigs(){
        int numMutableRules = this.mutableRules.size();
        List<WeightConfig> configs = Lists.newArrayList();
        float max = 1.0f;
        float min = 0.0f;
        int numPerSplit = (int)Math.exp(Math.log(maxConfigs)/numMutableRules);
        //If systematic generation of points will lead to not a reasonable exploration of space.
        //then just pick random points in space and hope it is better than being systematic.

        if (randomConfigsOnly) {
            log.info("Generating random configs.");
            return getRandomConfigs();
        }
        if (numPerSplit < 5){
            log.warn("Note not picking random points and large number " +
                    "of rules will yield bad exploration.");
        }
        float inc = max/numPerSplit;
        boolean done = false;
        WeightConfig config = new WeightConfig(new float[numMutableRules]);
        while (!done) {
            int j = 0;
            configs.add(new WeightConfig(config));
            for (int l = 0; l < numMutableRules; l++) {
                if(config.config[j] < max) {
                    config.config[j]+=inc;
                    break;
                } else if(j == numMutableRules-1){
                    done = true;
                    break;
                } else {
                    config.config[j] = min;
                    j++;
                }
            }
        }
        return configs;
    }

    private List<WeightConfig> getRandomConfigs(){
        int numMutableRules = this.mutableRules.size();
        List<WeightConfig> configs = Lists.newArrayList();
        for (int i = 0; i < maxConfigs; i++) {
            WeightConfig curConfig = new WeightConfig(new float[numMutableRules]);
            for (int j = 0; j < numMutableRules; j++) {
                curConfig.config[j] = RandUtils.nextFloat();
            }
            configs.add(curConfig);
        }
        return configs;
    }
/*
def predict(x, data, kernel, params, sigma, t):
    k = [kernel(x, y, params) for y in data]
    Sinv = np.linalg.inv(sigma)
    y_pred = np.dot(k, Sinv).dot(t)
    sigma_new = kernel(x, x, params) - np.dot(k, Sinv).dot(k)
    return y_pred, sigma_new
 */
    protected ValueAndStd predictFnValAndStd(float[] x, List<WeightConfig> xKnown, FloatMatrix blasYKnown){
        ValueAndStd fnAndStd = new ValueAndStd();
        FloatMatrix xyStd = FloatMatrix.zeros(blasYKnown.length);
        for (int i = 0; i < blasYKnown.length; i++) {
            xyStd.put(i, kernel.kernel(x, xKnown.get(i).config));
        }
        fnAndStd.value = xyStd.transpose().mmul(knownDataStdInv).dot(blasYKnown);
        fnAndStd.std = kernel.kernel(x,x) - xyStd.transpose().mmul(knownDataStdInv).dot(xyStd);
        return fnAndStd;
    }

    //Get metric value like accuracy.
    protected float getFunctionValue(WeightConfig config){
        setWeights(config);
        computeMPEState();
        evaluator.compute(trainingMap);
        double score = evaluator.getRepresentativeMetric();
        score = (evaluator.isHigherRepresentativeBetter())?score:-1*score;
        return (float)score;
    }

    //Exploration strategy
    protected int getNextPoint(List<WeightConfig> configs){
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

    static class ValueAndStd{
        float value;
        float std;
        ValueAndStd(){
            this(0,1);
        }
        ValueAndStd(float value, float std){
            this.value = value;
            this.std = std;
        }
    }

    static class WeightConfig {
        float [] config;

        ValueAndStd valueAndStd;

        WeightConfig(float[] config){
            this(config, 0, 1);
        }

        WeightConfig(WeightConfig config){
            this(ArrayUtils.clone(config.config), config.valueAndStd.value, config.valueAndStd.std);
        }

        WeightConfig(float[] config, float val, float std){
            this.config = config;
            this.valueAndStd = new ValueAndStd(val, std);

        }

        @Override
        public String toString() {
            return "config= " + ArrayUtils.toString(config) + ", val: " + valueAndStd.value +
                    ", std: " + valueAndStd.std;
        }
    }

}
