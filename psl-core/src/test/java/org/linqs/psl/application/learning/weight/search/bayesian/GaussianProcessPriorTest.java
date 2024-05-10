/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.FloatMatrix;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GaussianProcessPriorTest extends WeightLearningTest {
    @Override
    protected WeightLearningApplication getBaseWLA() {
        // Do less steps for tests.
        Options.WLA_GPP_MAX_ITERATIONS.set(2);

        return new GaussianProcessPrior(this.info.model.getRules(), trainTargetDatabase, trainTruthDatabase,
                validationTargetDatabase, validationTruthDatabase, false);
    }

    protected WeightLearningApplication getWLALocal() {
        // Do less steps for tests.
        Options.WLA_GPP_MAX_ITERATIONS.set(2);

        WeightLearningApplication wla = new GPPTest(info.model.getRules(), trainTargetDatabase, trainTruthDatabase,
                validationTargetDatabase, validationTruthDatabase);
        wla.setEvaluation(new EvaluationInstance(info.predicates.get("Friends"), new ContinuousEvaluator(), true));

        return wla;
    }

    @Test
    public void testGetNext() {
        GaussianProcessPrior wl = (GaussianProcessPrior)getWLA();
        List<Float> yPred = Arrays.asList(0.5f, 0.4f, 0.6f, 0.7f);
        List<Float> yStd = Arrays.asList(0.2f, 0.7f, 0.3f, 0.1f);

        List<GaussianProcessPrior.WeightConfig> weightConfigs = new ArrayList<GaussianProcessPrior.WeightConfig>();
        for (int i = 0; i < yPred.size(); i++) {
            weightConfigs.add(wl.new WeightConfig(null, yPred.get(i), yStd.get(i)));
        }

        Assert.assertEquals(1, wl.getNextPoint(weightConfigs));
    }

    @Test
    public void testGetConfigs() {
        Options.WLA_GPP_MAX_CONFIGS.set(5);
        Options.WLA_GPP_RANDOM_CONFIGS_ONLY.set(false);
        Options.WLA_GPP_USE_PROVIDED_WEIGHT.set(false);

        GaussianProcessPrior wl = (GaussianProcessPrior)getWLA();
        List<GaussianProcessPrior.WeightConfig> configs = wl.getConfigs();

        List<float[]> expected = new ArrayList<float[]>();
        expected.add(new float[]{0.0f, 0.0f, 0.0f});
        expected.add(new float[]{1.0f, 0.0f, 0.0f});
        expected.add(new float[]{0.0f, 1.0f, 0.0f});
        expected.add(new float[]{1.0f, 1.0f, 0.0f});
        expected.add(new float[]{0.0f, 0.0f, 1.0f});
        expected.add(new float[]{1.0f, 0.0f, 1.0f});
        expected.add(new float[]{0.0f, 1.0f, 1.0f});
        expected.add(new float[]{1.0f, 1.0f, 1.0f});

        for (int i = 0; i < configs.size(); i++) {
            for (int j = 0; j < configs.get(i).config.length; j++) {
                Assert.assertEquals(expected.get(i)[j], configs.get(i).config[j].getValue(), 1e-5);
            }
        }
    }

    @Test
    public void testPredictFnValAndStd() {
        Options.WLA_GPP_KERNEL_REL_DEP.set(100.0f);
        Options.WLA_GPP_KERNEL_SCALE.set(1.0f);
        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.OS.toString());

        GaussianProcessPrior wl = (GaussianProcessPrior)getWLA();

        FloatMatrix inverseMat = FloatMatrix.zeroes(3, 3);
        inverseMat.set(0, 0, 1);
        inverseMat.set(0, 1, 1f);
        inverseMat.set(0, 2, 0.9999f);
        inverseMat.set(1, 0, 0.9999f);
        inverseMat.set(1, 1, 1);
        inverseMat.set(1, 2, 0.9999f);
        inverseMat.set(2, 0, 0.9999f);
        inverseMat.set(2, 1, 0.9999f);
        inverseMat.set(2, 2, 1);

        inverseMat = inverseMat.inverse();
        wl.setKnownDataStdInvForTest(inverseMat);

        Weight[] x = new Weight[]{new Weight(0.4f), new Weight(0.2f), new Weight(0.1f)};
        List<GaussianProcessPrior.WeightConfig> xKnown = new ArrayList<GaussianProcessPrior.WeightConfig>();
        xKnown.add(wl.new WeightConfig(new Weight[]{new Weight(0.1f), new Weight(0.2f), new Weight(0.3f)}));
        xKnown.add(wl.new WeightConfig(new Weight[]{new Weight(0.2f), new Weight(0.2f), new Weight(0.1f)}));
        xKnown.add(wl.new WeightConfig(new Weight[]{new Weight(0.4f), new Weight(0.3f), new Weight(0.2f)}));
        float[] yKnown = new float[]{0.5f, 0.6f, 0.7f};
        FloatMatrix blasYKnown = FloatMatrix.columnVector(yKnown);

        wl.setKernelForTest(new SquaredExpKernel());
        wl.setBlasYKnownForTest(blasYKnown);

        GaussianProcessPrior.ValueAndStd fnAndStd = wl.predictFnValAndStd(x, xKnown);
        Assert.assertEquals(0.84939, fnAndStd.value, 1e-4);
        Assert.assertEquals(0.99656, fnAndStd.std, 1e-4);
    }

    @Test
    public void testDoLearn() {
        Options.WLA_GPP_KERNEL_REL_DEP.set(1.0f);
        Options.WLA_GPP_KERNEL_SPACE.set(GaussianProcessKernel.Space.OS.toString());
        Options.WLA_GPP_MAX_CONFIGS.set(5);
        Options.WLA_GPP_MAX_ITERATIONS.set(3);
        Options.WLA_GPP_RANDOM_CONFIGS_ONLY.set(false);

        GaussianProcessPrior wl = (GaussianProcessPrior)getWLALocal();
        wl.doLearn();

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(1.0f, ((WeightedRule)this.info.model.getRules().get(i)).getWeight().getValue(), 1e-30);
        }
    }

    private static class GPPTest extends GaussianProcessPrior {
        public GPPTest(List<Rule> rules, Database trainTargetDatabase, Database trainTruthDatabase, Database validationTargetDatabase, Database validationTruthDatabase) {
            super(rules, trainTargetDatabase, trainTruthDatabase, validationTargetDatabase, validationTruthDatabase, false);
        }

        @Override
        public double getFunctionValue(WeightConfig config) {
            if (config.config[0].getValue() == 1.0f && config.config[1].getValue() == 1.0f && config.config[2].getValue() == 1.0f) {
                return 0.5;
            }

            if (config.config[0].getValue() == 0.0f && config.config[1].getValue() == 1.0f && config.config[2].getValue() == 1.0f) {
                return 0.6;
            }

            return 0.3;
        }
    }
}
