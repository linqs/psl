/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.search.WeightSampler;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.util.FloatMatrix;
import org.linqs.psl.util.ListUtils;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GaussianProcessPrior extends WeightLearningApplication {
    private static final Logger log = LoggerFactory.getLogger(GaussianProcessPrior.class);

    public static final int MAX_RAND_INT_VAL = 100000000;
    public static final float SMALL_VALUE = 0.4f;

    private int maxIterations;
    private int maxConfigs;
    private float exploration;
    private boolean randomConfigsOnly;
    private boolean earlyStopping;

    private FloatMatrix knownDataStdInv;
    private GaussianProcessKernel kernel;
    private GaussianProcessKernel.Space space;
    private List<WeightConfig> configs;
    private List<WeightConfig> exploredConfigs;
    private FloatMatrix blasYKnown;

    private float initialMetricValue;
    private float initialMetricStd;

    private WeightSampler weightSampler;

    /**
     * This variable represents whether or not the provided weight
     * configuration is used as the first point for exploration.
     */
    private boolean useProvidedWeight;

    public GaussianProcessPrior(Model model, Database rvDB, Database observedDB) {
        this(model.getRules(), rvDB, observedDB);
    }

    public GaussianProcessPrior(List<Rule> rules, Database rvDB, Database observedDB) {
        super(rules, rvDB, observedDB);

        maxIterations = Options.WLA_GPP_MAX_ITERATIONS.getInt();
        maxConfigs = Options.WLA_GPP_MAX_CONFIGS.getInt();
        exploration = Options.WLA_GPP_EXPLORATION.getFloat();
        randomConfigsOnly = Options.WLA_GPP_RANDOM_CONFIGS_ONLY.getBoolean();
        earlyStopping = Options.WLA_GPP_EARLY_STOPPING.getBoolean();
        useProvidedWeight = Options.WLA_GPP_USE_PROVIDED_WEIGHT.getBoolean();

        initialMetricValue = Float.NEGATIVE_INFINITY;
        initialMetricStd = Float.POSITIVE_INFINITY;

        space = GaussianProcessKernel.Space.valueOf(Options.WLA_GPP_KERNEL_SPACE.getString().toUpperCase());

        weightSampler = new WeightSampler(mutableRules.size());
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

    private void setInitialConfigValAndStd(WeightConfig initialConfig) {
        float initialStd = (float)evaluator.getNormalizedMaxRepMetric() - initialConfig.valueAndStd.value;

        for (int i = 0; i < configs.size(); i++) {
            WeightConfig config = configs.get(i);
            config.valueAndStd.value = initialConfig.valueAndStd.value;
            config.valueAndStd.std = initialStd;
        }
    }

    @Override
    protected void doLearn() {
        String currentLocation = null;

        // Very important to define a good kernel.
        kernel = new SquaredExpKernel();

        reset();

        List<Float> exploredFnVal = new ArrayList<Float>();

        WeightConfig bestConfig = null;
        float bestVal = Float.NEGATIVE_INFINITY;
        boolean allStdSmall = false;

        int iteration = 0;
        while (iteration < maxIterations && configs.size() > 0 && !(earlyStopping && allStdSmall)) {
            int nextPoint = (iteration == 0) ? 0 : getNextPoint(configs);
            WeightConfig config = configs.get(nextPoint);

            exploredConfigs.add(config);
            configs.remove(nextPoint);

            // Set the current location.
            currentLocation = StringUtils.join(DELIM, config.config);

            log.trace("Weights: {}", config.config);

            float fnVal = (float)getFunctionValue(config);
            exploredFnVal.add(fnVal);
            config.valueAndStd.value = fnVal;
            config.valueAndStd.std = 0.0f;

            log.debug("Weights: {} -- objective: {}", currentLocation, fnVal);

            if (iteration == 0) {
                setInitialConfigValAndStd(config);
            }

            if (bestConfig == null || fnVal > bestVal) {
                bestVal = fnVal;
                bestConfig = config;
            }

            log.info(String.format("Iteration %d -- Config Picked: %s, Current Best Config: %s.", (iteration + 1), exploredConfigs.get(iteration), bestConfig));

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
        public void work(long index, WeightConfig item) {
            ValueAndStd valAndStd = predictFnValAndStd(configs.get((int)index).config, exploredConfigs, xyStdData,
                    kernelBuffer1, kernelBuffer2, kernelMatrixShell1, kernelMatrixShell2, xyStdMatrixShell, mulBuffer);
            configs.get((int)index).valueAndStd = valAndStd;
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

        // Create configuration with weights in the user provided model file.
        WeightConfig userProvidedConfig = new WeightConfig(new float[numMutableRules]);
        for (int i = 0; i < numMutableRules; i++) {
            userProvidedConfig.config[i] = (float)mutableRules.get(i).getWeight();
        }

        // If systematic generation of points will lead to not a reasonable exploration of space,
        // then just pick random points in space and hope it is better than being systematic.
        if (randomConfigsOnly) {
            log.debug("Generating random configs.");
            configs = getRandomConfigs();
        } else {
            if (numPerSplit < 5) {
                log.warn("Note that not picking random points for a model with a large number of rules will result in poor exploration of the weight space.");
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
        }

        // Add the user provided weight configuration to the head of the list.
        if (useProvidedWeight) {
            configs.add(0, userProvidedConfig);
        }

        return configs;
    }

    private List<WeightConfig> getRandomConfigs() {
        int numMutableRules = this.mutableRules.size();
        List<WeightConfig> configs = new ArrayList<WeightConfig>();

        for (int i = 0; i < maxConfigs; i++) {
            WeightConfig curConfig = new WeightConfig(new float[numMutableRules]);
            weightSampler.getRandomWeights(curConfig.config);
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
    protected double getFunctionValue(WeightConfig config) {
        setWeights(config);
        computeMPEState();
        evaluator.compute(trainingMap);
        return evaluator.getNormalizedRepMetric();
    }

    // Exploration strategy
    protected int getNextPoint(List<WeightConfig> configs) {
        int bestConfig = -1;
        float curBestVal = Float.NEGATIVE_INFINITY;

        for (int i = 0; i < configs.size(); i++) {
            float curVal = (configs.get(i).valueAndStd.value / exploration) + configs.get(i).valueAndStd.std;
            if (bestConfig == -1 || curVal > curBestVal) {
                curBestVal = curVal;
                bestConfig = i;
            }
        }

        return bestConfig;
    }

    protected class ValueAndStd {
        float value;
        float std;

        public ValueAndStd() {
            this(initialMetricValue, initialMetricStd);
        }

        public ValueAndStd(float value, float std) {
            this.value = value;
            this.std = std;
        }
    }

    protected class WeightConfig {
        public float[] config;
        public ValueAndStd valueAndStd;

        public WeightConfig(float[] config) {
            this(config, initialMetricValue, initialMetricStd);
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
