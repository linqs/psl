package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.FloatMatrix;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.RandUtils;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GaussianProcessPrior extends WeightLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(GaussianProcessPrior.class);

    public static final String CONFIG_PREFIX = "gpp";

    public static final String KERNEL_KEY = CONFIG_PREFIX + ".kernel";
    public static final String KERNEL_DEFAULT = GaussianProcessKernel.KernelType.SQUARED_EXP.toString();

    public static final String MAX_ITERATIONS_KEY = CONFIG_PREFIX + ".maxiterations";
    public static final int MAX_ITERATIONS_DEFAULT = 25;

    public static final String MAX_CONFIGS_KEY = CONFIG_PREFIX + ".maxconfigs";
    public static final int MAX_CONFIGS_DEFAULT = 1000000;

    public static final String EXPLORATION_KEY = CONFIG_PREFIX + ".explore";
    public static final float EXPLORATION_DEFAULT = 2.0f;

    public static final String RANDOM_CONFIGS_ONLY_KEY = CONFIG_PREFIX + ".randomConfigsOnly";
    public static final boolean RANDOM_CONFIGS_ONLY_DEFAULT = true;

    public static final String EARLY_STOPPING_KEY = CONFIG_PREFIX + ".earlyStopping";
    public static final boolean EARLY_STOPPING_DEFAULT = true;

    public static final int MAX_RAND_INT_VAL = 100000000;
    public static final float SMALL_VALUE = 0.4f;

    private GaussianProcessKernel.KernelType kernelType;
    private int maxIterations;
    private int maxConfigs;
    private float exploration;
    private boolean randomConfigsOnly;
    private boolean earlyStopping;

    private float minConfigVal;
    private FloatMatrix knownDataStdInv;
    private GaussianProcessKernel kernel;
    private GaussianProcessKernel.Space space;
    private List<WeightConfig> configs;
    private List<WeightConfig> exploredConfigs;
    private FloatMatrix blasYKnown;

    public GaussianProcessPrior(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        kernelType = GaussianProcessKernel.KernelType.valueOf(
                Config.getString(KERNEL_KEY, KERNEL_DEFAULT).toUpperCase());

        maxIterations = Config.getInt(MAX_ITERATIONS_KEY, MAX_ITERATIONS_DEFAULT);
        maxConfigs = Config.getInt(MAX_CONFIGS_KEY, MAX_CONFIGS_DEFAULT);
        exploration = Config.getFloat(EXPLORATION_KEY, EXPLORATION_DEFAULT);
        randomConfigsOnly = Config.getBoolean(RANDOM_CONFIGS_ONLY_KEY, RANDOM_CONFIGS_ONLY_DEFAULT);
        earlyStopping = Config.getBoolean(EARLY_STOPPING_KEY, EARLY_STOPPING_DEFAULT);

        space = GaussianProcessKernel.Space.valueOf(
                Config.getString(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.SPACE_DEFAULT));

        minConfigVal = 1.0f / MAX_RAND_INT_VAL;
    }

    public GaussianProcessPrior(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    private void reset() {
        configs = getConfigs();
        exploredConfigs = new ArrayList<>();
    }

    /**
     * Only for testing.
     */
    protected void setKnownDataStdInvForTest(FloatMatrix data) {
        knownDataStdInv = data;
    }

    /**
     * Only for testing.
     */
    protected void setKernelForTest(GaussianProcessKernel kernel) {
        this.kernel = kernel;
    }

    /**
     * Only for testing.
     */
    protected void setBlasYKnownForTest(FloatMatrix blasYKnown) {
        this.blasYKnown = blasYKnown;
    }

    @Override
    protected void doLearn() {
        // Very important to define a good kernel.
        kernel = GaussianProcessKernel.makeKernel(kernelType, this);

        reset();

        List<Float> exploredFnVal = new ArrayList<Float>();

        WeightConfig bestConfig = null;
        float bestVal = 0.0f;
        boolean allStdSmall = false;

        int iteration = 0;
        while (iteration < maxIterations && configs.size() > 0 && !(earlyStopping && allStdSmall)) {
            int nextPoint = getNextPoint(configs, iteration);
            WeightConfig config = configs.get(nextPoint);

            exploredConfigs.add(config);
            configs.remove(nextPoint);

            float fnVal = getFunctionValue(config);
            exploredFnVal.add(fnVal);
            config.valueAndStd.value = fnVal;
            config.valueAndStd.std = 0.0f;

            if (bestConfig == null || fnVal > bestVal) {
                bestVal = fnVal;
                bestConfig = config;
            }

            log.info(String.format("Iteration %d -- Config Picked: %s, Curent Best Config: %s.", (iteration + 1), exploredConfigs.get(iteration), bestConfig));

            int numKnown = exploredFnVal.size();
            knownDataStdInv = FloatMatrix.zeroes(numKnown, numKnown);
            for (int i = 0; i < numKnown; i++) {
                for (int j = 0; j < numKnown; j++) {
                    knownDataStdInv.set(i, j, kernel.kernel(exploredConfigs.get(i).config, exploredConfigs.get(j).config));
                }
            }

            knownDataStdInv = knownDataStdInv.inverse();

            blasYKnown = FloatMatrix.columnVector(ListUtils.toPrimitiveFloatArray(exploredFnVal), false);

            // Re-construct the worker each iteration so the data buffer is sized correctly.
            ComputePredictionFunctionValueWorker fnValWorker = new ComputePredictionFunctionValueWorker();

            // TODO(eriq): Because most of the time is taken by BLAS methods (multiply and dot),
            // parallelism will not help here.
            // Parallel.foreach(configs, fnValWorker);
            int index = 0;
            for (WeightConfig weightConfig : configs) {
                fnValWorker.work(index, weightConfig);
                index++;
            }

            // Early stopping check.
            allStdSmall = true;
            for (int i = 0; i < configs.size(); i++) {
                if (configs.get(i).valueAndStd.std > SMALL_VALUE) {
                    allStdSmall = false;
                    break;
                }
            }

            iteration++;
        }

        setWeights(bestConfig);
        log.info(String.format("Total number of iterations completed: %d. Stopped early: %s.",
                iteration, (earlyStopping && allStdSmall)));
        log.info("Best config: " + bestConfig);
    }

    private class ComputePredictionFunctionValueWorker extends Parallel.Worker<WeightConfig> {
        private float[] xyStdData;
        private FloatMatrix xyStdMatrixShell;
        private float[] kernelBuffer1;
        private float[] kernelBuffer2;
        private FloatMatrix kernelMatrixShell1;
        private FloatMatrix kernelMatrixShell2;
        private FloatMatrix mulBuffer;

        public ComputePredictionFunctionValueWorker() {
            xyStdData = new float[blasYKnown.size()];
            xyStdMatrixShell = new FloatMatrix();
            kernelBuffer1 = new float[mutableRules.size()];
            kernelBuffer2 = new float[mutableRules.size()];
            kernelMatrixShell1 = new FloatMatrix();
            kernelMatrixShell2 = new FloatMatrix();
            mulBuffer = FloatMatrix.zeroes(1, blasYKnown.size());
        }

        @Override
        public Object clone() {
            return new ComputePredictionFunctionValueWorker();
        }

        @Override
        public void work(int index, WeightConfig item) {
            ValueAndStd valAndStd = predictFnValAndStd(configs.get(index).config, exploredConfigs, xyStdData,
                    kernelBuffer1, kernelBuffer2, kernelMatrixShell1, kernelMatrixShell2, xyStdMatrixShell, mulBuffer);
            configs.get(index).valueAndStd = valAndStd;
        }
    }

    private void setWeights(WeightConfig config) {
        for (int i = 0; i < mutableRules.size(); i++) {
            mutableRules.get(i).setWeight(config.config[i]);
        }

        inMPEState = false;
    }

    protected List<WeightConfig> getConfigs() {
        int numMutableRules = this.mutableRules.size();
        List<WeightConfig> configs = new ArrayList<WeightConfig>();

        float max = 1.0f;
        float min = 1.0f / MAX_RAND_INT_VAL;

        if (space == GaussianProcessKernel.Space.OS) {
            min = 0.0f;
        }

        int numPerSplit = (int)Math.exp(Math.log(maxConfigs) / numMutableRules);

        // If systematic generation of points will lead to not a reasonable exploration of space,
        // then just pick random points in space and hope it is better than being systematic.

        if (randomConfigsOnly) {
            log.debug("Generating random configs.");
            return getRandomConfigs();
        }

        if (numPerSplit < 5) {
            log.warn("Note not picking random points and large number of rules will yield bad exploration.");
        }

        float inc = max / numPerSplit;
        float[] configArray = new float[numMutableRules];
        Arrays.fill(configArray, min);
        WeightConfig config = new WeightConfig(configArray);

        boolean done = false;
        while (!done) {
            int i = 0;
            configs.add(new WeightConfig(config));
            for (int j = 0; j < numMutableRules; j++) {
                if (config.config[i] < max) {
                    config.config[i] += inc;
                    break;
                }

                if (i == numMutableRules - 1) {
                    done = true;
                    break;
                }

                config.config[i] = min;
                i++;
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
            factor[i] = Math.max(1, groundRuleStore.count(mutableRules.get(i)));
        }

        return factor;
    }

    private List<WeightConfig> getRandomConfigs() {
        int numMutableRules = this.mutableRules.size();
        List<WeightConfig> configs = new ArrayList<WeightConfig>();
        for (int i = 0; i < maxConfigs; i++) {
            WeightConfig curConfig = new WeightConfig(new float[numMutableRules]);
            for (int j = 0; j < numMutableRules; j++) {
                curConfig.config[j] = (RandUtils.nextInt(MAX_RAND_INT_VAL) + 1) / (float)(MAX_RAND_INT_VAL + 1);
            }
            configs.add(curConfig);
        }
        return configs;
    }

    /**
     * predictFnValAndStd, but no memory sharing.
     */
    protected ValueAndStd predictFnValAndStd(float[] x, List<WeightConfig> xKnown) {
        return predictFnValAndStd(x, xKnown, new float[blasYKnown.size()],
                new float[x.length], new float[x.length], new FloatMatrix(), new FloatMatrix(),
                new FloatMatrix(), FloatMatrix.zeroes(1, x.length));
    }

    /**
     * Do the prediction.
     *
     * @param xyStdData A correctly-sized buffer to perform computations with.
     *  Will get modified.
     */
    protected ValueAndStd predictFnValAndStd(float[] x, List<WeightConfig> xKnown, float[] xyStdData,
            float[] kernelBuffer1, float[] kernelBuffer2, FloatMatrix kernelMatrixShell1, FloatMatrix kernelMatrixShell2,
            FloatMatrix xyStdMatrixShell, FloatMatrix mulBuffer) {
        ValueAndStd fnAndStd = new ValueAndStd();

        for (int i = 0; i < xyStdData.length; i++) {
            xyStdData[i] = kernel.kernel(x, xKnown.get(i).config, kernelBuffer1, kernelBuffer2, kernelMatrixShell1, kernelMatrixShell2);
        }
        xyStdMatrixShell.assume(xyStdData, 1, xyStdData.length);

        FloatMatrix xyStd = xyStdMatrixShell;

        FloatMatrix product = xyStd.mul(knownDataStdInv, mulBuffer, false, false, 1.0f, 0.0f);

        fnAndStd.value = product.dot(blasYKnown);
        fnAndStd.std = kernel.kernel(x, x, kernelBuffer1, kernelBuffer2, kernelMatrixShell1, kernelMatrixShell2) - product.dot(xyStd);

        return fnAndStd;
    }

    // Get metric value like accuracy.
    protected float getFunctionValue(WeightConfig config) {
        setWeights(config);
        computeMPEState();
        evaluator.compute(trainingMap);
        double score = evaluator.getRepresentativeMetric();
        score = (evaluator.isHigherRepresentativeBetter()) ? score : -1.0 * score;
        return (float)score;
    }

    // Exploration strategy
    protected int getNextPoint(List<WeightConfig> configs, int iteration) {
        int bestConfig = -1;
        float curBestVal = -Float.MAX_VALUE;
        for (int i = 0; i < configs.size(); i++) {
            float curVal = (configs.get(i).valueAndStd.value/exploration) + configs.get(i).valueAndStd.std;
            if (bestConfig == -1 || curVal > curBestVal) {
                curBestVal = curVal;
                bestConfig = i;
            }
        }

        return bestConfig;
    }

    protected static class ValueAndStd {
        float value;
        float std;

        ValueAndStd() {
            this(0,1);
        }

        ValueAndStd(float value, float std) {
            this.value = value;
            this.std = std;
        }
    }

    protected static class WeightConfig {
        public float[] config;
        public ValueAndStd valueAndStd;

        public WeightConfig(float[] config) {
            this(config, 0, 1);
        }

        public WeightConfig(WeightConfig config) {
            this(Arrays.copyOf(config.config, config.config.length), config.valueAndStd.value, config.valueAndStd.std);
        }

        public WeightConfig(float[] config, float val, float std) {
            this.config = config;
            this.valueAndStd = new ValueAndStd(val, std);
        }

        @Override
        public String toString() {
            return String.format("(weights: [%s], val: %f, std: %f)", StringUtils.join(", ", config), valueAndStd.value, valueAndStd.std);
        }
    }
}
