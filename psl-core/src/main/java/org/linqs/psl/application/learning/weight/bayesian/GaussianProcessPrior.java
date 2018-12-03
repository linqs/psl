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

import java.util.Arrays;
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
    private static final String DEFAULT_KERNEL = GaussianProcessKernels.SQUARED_EXP;
    private static final int MAX_CONFIGS = 1000000;
    private static final int MAX_NUM_ITER = 25;
    private static final float EXPLORATION_VAL = 2.0f;
    private static final String RANDOM_CONFIGS_ONLY = ".randomConfigsOnly";
    private static final int MAX_RAND_INT_VAL = 100000000;
    private static final String EARLY_STOPPING = ".earlyStopping";
    private boolean earlyStopping;
    private String kernel_name;
    private FloatMatrix knownDataStdInv;
    private GaussianProcessKernels.Kernel kernel;
    private int maxIterNum;
    private int maxConfigs;
    private float exploration;
    private boolean randomConfigsOnly;
    private List<WeightConfig> configs;
    private List<WeightConfig> exploredConfigs;
    private FloatMatrix blasYKnown;
    private float minConfigVal;

    public GaussianProcessPrior(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB, false);
        kernel_name = Config.getString(CONFIG_PREFIX+KERNEL, DEFAULT_KERNEL);
        if (!GaussianProcessKernels.KERNELS.contains(kernel_name)){
            throw new IllegalArgumentException("No kernel named - " + kernel_name + ", exists.");
        }
        maxIterNum = Config.getInt(CONFIG_PREFIX+NUM_ITER, MAX_NUM_ITER);
        maxConfigs = Config.getInt(CONFIG_PREFIX+MAX_CONFIGS_STR, MAX_CONFIGS);
        exploration = Config.getFloat(CONFIG_PREFIX+EXPLORATION, EXPLORATION_VAL);
        randomConfigsOnly = Config.getBoolean(CONFIG_PREFIX+RANDOM_CONFIGS_ONLY, true);
        earlyStopping = Config.getBoolean(EARLY_STOPPING, true);
        minConfigVal = 1/(float)MAX_RAND_INT_VAL;
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
        //Very important to define a good kernel.
        kernel = GaussianProcessKernels.kernelProvider(kernel_name, this);
        reset();
        List<Float> exploredFnVal = Lists.newArrayList();
        final ComputePredFnValWorker fnValWorker = new ComputePredFnValWorker();
        int iter = 0;
        WeightConfig bestConfig = null;
        float bestVal = -Float.MAX_VALUE;
        boolean allStdSmall = true;
        do{
            int nextPoint = getNextPoint(configs, iter);
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
            //early stopping
            allStdSmall = true;
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).valueAndStd.std > 0.4 ){
                    allStdSmall = false;
                    break;
                }
            }
            iter++;
            if (earlyStopping && allStdSmall){
                break;
            }
        }while((iter < maxIterNum && configs.size() > 0));
        setWeights(bestConfig);
        log.info("Total number of iterations completed: " + iter + ", Early stopping: " + allStdSmall);
        log.info("Best config: " + bestConfig);
//        try{
//            BufferedWriter writer = new BufferedWriter(new FileWriter("/Users/sriramsrinivasan/Documents/gpp_weight_learning_psl/srinivasan-aaai19b/randomRes/expanded_space_10lr.txt"));
//            for (WeightConfig c: exploredConfigs) {
//                writer.write(c.config[0]+","+c.config[1]+","+c.valueAndStd.value+","+c.valueAndStd.std+"\n");
//            }
//            for (WeightConfig c: configs){
//                writer.write(c.config[0]+","+c.config[1]+","+c.valueAndStd.value+","+c.valueAndStd.std+"\n");
//            }
//        } catch (IOException e){
//            System.exit(1);
//        }

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
        float min = 1/((float)MAX_RAND_INT_VAL);
        if (GaussianProcessKernels.Spaces.OS.toString().equals(
                Config.getString(GaussianProcessKernels.CONFIG_PREFIX+GaussianProcessKernels.SPACE,
                        GaussianProcessKernels.Spaces.SS.toString()))) {
            min = 0.0f;
        }
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
        final float[] configArray = new float[numMutableRules];
        Arrays.fill(configArray,min);
        WeightConfig config = new WeightConfig(configArray);
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

    /**
     * Computes the amount to scale gradient for each rule.
     * Scales by the number of groundings of each rule
     * unless the rule is not grounded in the training set, in which case
     * scales by 1.0.
     */
    protected int[] computeScalingFactor() {
        int [] factor = new int[mutableRules.size()];
        for (int i = 0; i < factor.length; i++) {
            factor[i] = (int) Math.max(1.0, groundRuleStore.count(mutableRules.get(i)));
        }

        return factor;
    }

    private List<WeightConfig> getRandomConfigs(){
        int numMutableRules = this.mutableRules.size();
        List<WeightConfig> configs = Lists.newArrayList();
        for (int i = 0; i < maxConfigs; i++) {
            WeightConfig curConfig = new WeightConfig(new float[numMutableRules]);
            for (int j = 0; j < numMutableRules; j++) {
                curConfig.config[j] = (RandUtils.nextInt(MAX_RAND_INT_VAL)+1)/(float)(MAX_RAND_INT_VAL + 1);
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
    protected int getNextPoint(List<WeightConfig> configs, int iter){
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
