package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.util.FloatMatrix;

import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GaussianProcessPriorTest extends WeightLearningTest {
    @Override
    protected WeightLearningApplication getWLA() {
        // Do less steps for tests.
        Config.setProperty(GaussianProcessPrior.MAX_ITERATIONS_KEY, 2);

        return new GaussianProcessPrior(this.info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }

    protected WeightLearningApplication getWLALocal() {
        // Do less steps for tests.
        Config.setProperty(GaussianProcessPrior.MAX_ITERATIONS_KEY, 2);

        return new GPPTest(this.info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }

    @Test
    public void testGetNext() {
        GaussianProcessPrior wl = (GaussianProcessPrior) getWLA();
        List<Float> yPred = Arrays.asList(0.5f, 0.4f, 0.6f, 0.7f);
        List<Float> yStd = Arrays.asList(0.2f, 0.7f, 0.3f, 0.1f);

        List<GaussianProcessPrior.WeightConfig> weightConfigs = new ArrayList<GaussianProcessPrior.WeightConfig>();
        for (int i = 0; i < yPred.size(); i++) {
            weightConfigs.add(new GaussianProcessPrior.WeightConfig(null, yPred.get(i), yStd.get(i)));
        }

        Assert.assertEquals(1, wl.getNextPoint(weightConfigs, 1));
    }

    @Test
    public void testGetConfigs() {
        Config.setProperty(GaussianProcessPrior.MAX_CONFIGS_KEY, 5);
        Config.setProperty(GaussianProcessPrior.RANDOM_CONFIGS_ONLY_KEY, false);

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
                Assert.assertEquals(expected.get(i)[j], configs.get(i).config[j], 1e-5);
            }
        }
    }

    @Test
    public void testPredictFnValAndStd() {
        Config.setProperty(GaussianProcessKernel.REL_DEP_KEY, 100);
        Config.setProperty(GaussianProcessKernel.SCALE_KEY, 1.0);
        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.OS);

        GaussianProcessPrior wl = (GaussianProcessPrior) getWLA();

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

        float[] x = new float[]{0.4f, 0.2f, 0.1f};
        List<GaussianProcessPrior.WeightConfig> xKnown = new ArrayList<GaussianProcessPrior.WeightConfig>();
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{0.1f, 0.2f, 0.3f}));
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{0.2f, 0.2f, 0.1f}));
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{0.4f, 0.3f, 0.2f}));
        float[] yKnown = new float[]{0.5f, 0.6f, 0.7f};
        FloatMatrix blasYKnown = FloatMatrix.columnVector(yKnown);

        wl.setKernelForTest(GaussianProcessKernel.makeKernel(GaussianProcessKernel.KernelType.SQUARED_EXP, wl));
        wl.setBlasYKnownForTest(blasYKnown);

        GaussianProcessPrior.ValueAndStd fnAndStd = wl.predictFnValAndStd(x, xKnown);
        Assert.assertEquals(0.84939, fnAndStd.value, 1e-4);
        Assert.assertEquals(0.99656, fnAndStd.std, 1e-4);
    }

    @Test
    public void testDoLearn() {
        Config.setProperty(GaussianProcessKernel.REL_DEP_KEY, 1);
        Config.setProperty(GaussianProcessKernel.SPACE_KEY, GaussianProcessKernel.Space.OS);
        Config.setProperty(GaussianProcessPrior.MAX_CONFIGS_KEY, 5);
        Config.setProperty(GaussianProcessPrior.MAX_ITERATIONS_KEY, 3);
        Config.setProperty(GaussianProcessPrior.KERNEL_KEY, GaussianProcessKernel.KernelType.SQUARED_EXP);
        Config.setProperty(GaussianProcessPrior.RANDOM_CONFIGS_ONLY_KEY, false);

        GaussianProcessPrior wl = (GaussianProcessPrior) getWLALocal();
        wl.doLearn();

        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(1.0f, ((WeightedRule)this.info.model.getRules().get(i)).getWeight(), 1e-30);
        }
    }

    private static class GPPTest extends GaussianProcessPrior {
        public GPPTest(List<Rule> rules, Database rvDB, Database observedDB) {
            super(rules, rvDB, observedDB);
        }

        @Override
        public float getFunctionValue(WeightConfig config) {
            if (config.config[0] == 1.0 && config.config[1] == 1.0 && config.config[2] == 1.0) {
                return 0.5f;
            }

            if (config.config[0] == 0.0 && config.config[1] == 1.0 && config.config[2] == 1.0) {
                return 0.6f;
            }

            return 0.3f;
        }
    }
}
