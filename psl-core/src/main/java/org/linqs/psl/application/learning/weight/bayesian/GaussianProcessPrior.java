package org.linqs.psl.application.learning.weight.bayesian;

import com.google.common.collect.Lists;
import org.jblas.FloatMatrix;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;

import java.util.List;

/**
 * Created by sriramsrinivasan on 6/22/18.
 */
public class GaussianProcessPrior extends WeightLearningApplication {

    private static String CONFIG_PREFIX = "gpp";
    private static String KERNEL = ".kernel";
    private static String DEFAULT_KERNEL = "gaussian";
    private static int MAX_CONFIGS = 100000;
    private static int MAX_NUM_ITER = 25;
    FloatMatrix knownDataStdInv;
    private GaussianProcessKernels.Kernel kernel;

    public GaussianProcessPrior(List<Rule> rules, Database rvDB, Database observedDB, boolean supportsLatentVariables) {
        super(rules, rvDB, observedDB, supportsLatentVariables);
        String kernel_name = Config.getString(CONFIG_PREFIX+KERNEL, DEFAULT_KERNEL);
        if (!GaussianProcessKernels.KERNELS.keySet().contains(kernel_name)){
            throw new IllegalArgumentException("No kernel named - " + kernel_name + ", exists.");
        }
        kernel = GaussianProcessKernels.KERNELS.get(kernel_name);
    }

    @Override
    protected void doLearn() {
        int numMutableRules = this.mutableRules.size();
        List<float[]> configs = getConfigs();

        
    }

    private List<float[]> getConfigs(){
        int numMutableRules = this.mutableRules.size();
        List<float[]> configs = Lists.newArrayList();
        float max = 1.0f;
        float min = 0.0f;
        int numPerSplit = (int)Math.exp(Math.log(MAX_CONFIGS)/numMutableRules);
        float inc = max/numPerSplit;
        float[] config = new float[numMutableRules];
        boolean done = false;
        while (!done) {
            int j = 0;
            configs.add(config.clone());
            for (int l = 0; l < numMutableRules; l++) {
                if(config[j] < max) {
                    config[j]+=inc;
                    break;
                } else if(j == numMutableRules-1){
                    done = true;
                    break;
                } else {
                    config[j] = min;
                    j++;
                }
            }
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
    protected float[] predictFnValAndStd(float[] x, List<float[]> xKnown, float[] yKnown){
        float[] fnAndStd = {0,0};
        FloatMatrix blasYKnown = new FloatMatrix(yKnown);
        FloatMatrix xyStd = FloatMatrix.zeros(yKnown.length);
        for (int i = 0; i < yKnown.length; i++) {
            xyStd.put(i, kernel.kernel(x, xKnown.get(i)));
        }
        fnAndStd[0] = knownDataStdInv.mmul(xyStd).dot(blasYKnown);
        fnAndStd[1] = kernel.kernel(x,x) - knownDataStdInv.mmul(xyStd).dot(xyStd);
        return fnAndStd;
    }

    //Get metric value like accuracy.
    protected double getFunctionValue(){
        evaluator.compute(trainingMap);
        double score = evaluator.getRepresentativeMetric();
        score = (evaluator.isHigherRepresentativeBetter())?score:-1*score;
        return score;
    }

    //Exploration strategy
    protected int getNextPoint(){
        return 0;
    }

    //Very important to define a good kernel.
}
