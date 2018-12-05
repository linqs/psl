package org.linqs.psl.application.learning.weight.bayesian;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.WeightLearningTest;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;

import org.jblas.FloatMatrix;
import org.jblas.Solve;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class GaussianProcessPriorTest extends WeightLearningTest {
    @Override
    protected WeightLearningApplication getWLA() {
        return new GaussianProcessPrior(this.info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }

    protected WeightLearningApplication getWLALocal(){
        return new GPPTest(this.info.model.getRules(), weightLearningTrainDB, weightLearningTruthDB);
    }

    @Test
    public void testGetNext(){
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
        Config.addProperty(GaussianProcessPrior.MAX_CONFIGS_KEY, 5);
        Config.addProperty(GaussianProcessPrior.RANDOM_CONFIGS_ONLY_KEY, false);

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
        Config.addProperty(GaussianProcessKernels.REL_DEP_KEY, 100);
        Config.addProperty(GaussianProcessKernels.SCALE_KEY, 1.0);
        Config.addProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.OS);

        GaussianProcessPrior wl = (GaussianProcessPrior) getWLA();

        FloatMatrix inverseMat = new FloatMatrix(3, 3);
        inverseMat.put(0, 0, 1);
        inverseMat.put(0, 1, 1f);
        inverseMat.put(0, 2, 0.9999f);
        inverseMat.put(1, 0, 0.9999f);
        inverseMat.put(1, 1, 1);
        inverseMat.put(1, 2, 0.9999f);
        inverseMat.put(2, 0, 0.9999f);
        inverseMat.put(2, 1, 0.9999f);
        inverseMat.put(2, 2, 1);

        inverseMat = Solve.solve(inverseMat, FloatMatrix.eye(3));
        wl.setKnownDataStdInvForTest(inverseMat);

        float[] x = new float[]{0.4f, 0.2f, 0.1f};
        List<GaussianProcessPrior.WeightConfig> xKnown = new ArrayList<GaussianProcessPrior.WeightConfig>();
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{0.1f, 0.2f, 0.3f}));
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{0.2f, 0.2f, 0.1f}));
        xKnown.add(new GaussianProcessPrior.WeightConfig(new float[]{0.4f, 0.3f, 0.2f}));
        float[] yKnown = new float[]{0.5f, 0.6f, 0.7f};
        FloatMatrix blasYKnown = new FloatMatrix(yKnown);

        wl.setKernelForTest(GaussianProcessKernels.makeKernel(GaussianProcessKernels.KernelType.SQUARED_EXP, wl));

        GaussianProcessPrior.ValueAndStd fnAndStd = wl.predictFnValAndStd(x, xKnown, blasYKnown);
        Assert.assertEquals(0.84939, fnAndStd.value, 1e-5);
        Assert.assertEquals(0.99656, fnAndStd.std, 1e-5);
    }

    @Test
    public void testDoLearn(){
        Config.addProperty(GaussianProcessKernels.REL_DEP_KEY, 1);
        Config.addProperty(GaussianProcessKernels.SPACE_KEY, GaussianProcessKernels.Space.OS);
        Config.addProperty(GaussianProcessPrior.MAX_CONFIGS_KEY, 5);
        Config.addProperty(GaussianProcessPrior.MAX_ITERATIONS_KEY, 3);
        Config.addProperty(GaussianProcessPrior.KERNEL_KEY, GaussianProcessKernels.KernelType.SQUARED_EXP);
        Config.addProperty(GaussianProcessPrior.RANDOM_CONFIGS_ONLY_KEY, false);

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
        public float getFunctionValue(WeightConfig config){
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
