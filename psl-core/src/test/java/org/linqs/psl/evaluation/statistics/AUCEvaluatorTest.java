/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.evaluation.statistics;

import org.junit.Test;

public class AUCEvaluatorTest extends EvaluatorTest<AUCEvaluator> {
    @Override
    protected AUCEvaluator getEvaluator() {
        return new AUCEvaluator();
    }

    @Test
    public void testAUROC() {
        AUCEvaluator evaluator = new AUCEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.75, evaluator.auroc());
    }

    @Test
    public void testPositiveAUPRC() {
        AUCEvaluator evaluator = new AUCEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.791667, evaluator.positiveAUPRC());
    }

    @Test
    public void testNegativeAUPRC() {
        AUCEvaluator evaluator = new AUCEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(1.0f / 3.0f, evaluator.negativeAUPRC());
    }

    /**
     * Run several runs against the output of an external reference implementation.
     */
    @Test
    public void testReference() {
        AUCEvaluator evaluator = new AUCEvaluator();
        float[] truth = null;
        float[] predictions = null;

        truth = new float[]{1f, 1f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 0f, 0f, 0f};
        predictions = new float[]{0.542f, 0.9057f, 0.4502f, 0.2308f, 0.897f, 0.8651f, 0.6619f, 0.4376f, 0.4459f, 0.6135f, 0.9688f, 0.9839f, 0.9729f, 0.688f, 0.9286f, 0.9683f, 0.9183f, 0.5296f, 0.9027f, 0.1719f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.510417, evaluator.auroc());
        assertEquals(0.359771, evaluator.positiveAUPRC());
        assertEquals(0.714468, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f, 0f};
        predictions = new float[]{0.5225f, 0.0797f, 0.1193f, 0.4443f, 0.2775f, 0.6222f, 0.3681f, 0.9106f, 0.5873f, 0.4504f, 0.6098f, 0.0369f, 0.1252f, 0.8882f, 0.5859f, 0.7417f, 0.3804f, 0.7083f, 0.8354f, 0.8905f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.659341, evaluator.auroc());
        assertEquals(0.735122, evaluator.positiveAUPRC());
        assertEquals(0.282618, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f};
        predictions = new float[]{0.7513f, 0.1355f, 0.4795f, 0.327f, 0.3266f, 0.6512f, 0.8348f, 0.1314f, 0.4035f, 0.2587f, 0.7601f, 0.4606f, 0.6415f, 0.2933f, 0.8713f, 0.3273f, 0.5128f, 0.4472f, 0.7502f, 0.4355f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.434343, evaluator.auroc());
        assertEquals(0.477349, evaluator.positiveAUPRC());
        assertEquals(0.564365, evaluator.negativeAUPRC());

        truth = new float[]{0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 1f};
        predictions = new float[]{0.482f, 0.5344f, 0.8681f, 0.1194f, 0.156f, 0.8167f, 0.0209f, 0.5027f, 0.6757f, 0.6566f, 0.8461f, 0.2632f, 0.6228f, 0.1406f, 0.7758f, 0.9432f, 0.2207f, 0.3166f, 0.6942f, 0.4624f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.619048, evaluator.auroc());
        assertEquals(0.452831, evaluator.positiveAUPRC());
        assertEquals(0.628401, evaluator.negativeAUPRC());

        truth = new float[]{1f, 1f, 0f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 0f};
        predictions = new float[]{0.9984f, 0.3183f, 0.4168f, 0.2012f, 0.8728f, 0.1547f, 0.2205f, 0.7517f, 0.4318f, 0.4949f, 0.1775f, 0.0492f, 0.7245f, 0.7741f, 0.5736f, 0.5983f, 0.7681f, 0.3828f, 0.2191f, 0.5303f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.488095, evaluator.auroc());
        assertEquals(0.785729, evaluator.positiveAUPRC());
        assertEquals(0.275980, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 0f, 0f};
        predictions = new float[]{0.2179f, 0.8949f, 0.1616f, 0.7093f, 0.3405f, 0.4439f, 0.4862f, 0.9185f, 0.7397f, 0.575f, 0.677f, 0.2633f, 0.5207f, 0.1846f, 0.4825f, 0.7629f, 0.8963f, 0.1597f, 0.8369f, 0.9792f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.450000, evaluator.auroc());
        assertEquals(0.427871, evaluator.positiveAUPRC());
        assertEquals(0.650216, evaluator.negativeAUPRC());

        truth = new float[]{0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
        predictions = new float[]{0.6276f, 0.6634f, 0.857f, 0.1931f, 0.5303f, 0.2302f, 0.2383f, 0.1771f, 0.067f, 0.6375f, 0.3318f, 0.3271f, 0.5287f, 0.7967f, 0.0307f, 0.9092f, 0.2946f, 0.659f, 0.2338f, 0.0915f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.535354, evaluator.auroc());
        assertEquals(0.538568, evaluator.positiveAUPRC());
        assertEquals(0.500034, evaluator.negativeAUPRC());

        truth = new float[]{0f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 1f};
        predictions = new float[]{0.6125f, 0.9842f, 0.1461f, 0.9745f, 0.5566f, 0.1739f, 0.3249f, 0.079f, 0.37f, 0.9759f, 0.8305f, 0.7079f, 0.1572f, 0.5226f, 0.7932f, 0.9201f, 0.2052f, 0.3427f, 0.3478f, 0.6804f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.571429, evaluator.auroc());
        assertEquals(0.775731, evaluator.positiveAUPRC());
        assertEquals(0.258924, evaluator.negativeAUPRC());

        truth = new float[]{0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 0f, 0f};
        predictions = new float[]{0.8475f, 0.7169f, 0.4021f, 0.9493f, 0.8403f, 0.4463f, 0.9123f, 0.2076f, 0.1988f, 0.8184f, 0.8935f, 0.4612f, 0.5462f, 0.8672f, 0.2745f, 0.1964f, 0.1985f, 0.2173f, 0.8797f, 0.5342f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.540000, evaluator.auroc());
        assertEquals(0.664196, evaluator.positiveAUPRC());
        assertEquals(0.439707, evaluator.negativeAUPRC());

        truth = new float[]{1f, 1f, 1f, 0f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 1f};
        predictions = new float[]{0.1405f, 0.5777f, 0.7009f, 0.9326f, 0.5101f, 0.9627f, 0.8778f, 0.4778f, 0.1665f, 0.1884f, 0.9137f, 0.1437f, 0.9071f, 0.0287f, 0.1988f, 0.1903f, 0.829f, 0.9173f, 0.0873f, 0.1737f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.505051, evaluator.auroc());
        assertEquals(0.500027, evaluator.positiveAUPRC());
        assertEquals(0.617837, evaluator.negativeAUPRC());

        truth = new float[]{0f, 0f, 1f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 1f, 0f, 1f};
        predictions = new float[]{0.8313f, 0.3338f, 0.8147f, 0.5272f, 0.3811f, 0.5543f, 0.4262f, 0.9379f, 0.4503f, 0.9323f, 0.3023f, 0.767f, 0.015f, 0.0204f, 0.8372f, 0.7105f, 0.8571f, 0.0867f, 0.0361f, 0.8348f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.670330, evaluator.auroc());
        assertEquals(0.698193, evaluator.positiveAUPRC());
        assertEquals(0.377438, evaluator.negativeAUPRC());

        truth = new float[]{1f, 1f, 1f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 0f, 0f};
        predictions = new float[]{0.2928f, 0.8056f, 0.2316f, 0.5017f, 0.0918f, 0.3684f, 0.9292f, 0.8591f, 0.8455f, 0.8134f, 0.3843f, 0.9434f, 0.0303f, 0.0697f, 0.2443f, 0.047f, 0.6987f, 0.6181f, 0.1449f, 0.1219f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.444444, evaluator.auroc());
        assertEquals(0.471122, evaluator.positiveAUPRC());
        assertEquals(0.574506, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 1f, 0f, 1f, 0f};
        predictions = new float[]{0.7348f, 0.8317f, 0.2794f, 0.2468f, 0.1622f, 0.2771f, 0.2222f, 0.7224f, 0.008f, 0.5536f, 0.0218f, 0.9189f, 0.3806f, 0.2951f, 0.8073f, 0.4525f, 0.0814f, 0.5725f, 0.7758f, 0.0743f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.616162, evaluator.auroc());
        assertEquals(0.679391, evaluator.positiveAUPRC());
        assertEquals(0.363322, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f};
        predictions = new float[]{0.3244f, 0.8568f, 0.0445f, 0.9385f, 0.1843f, 0.7762f, 0.7919f, 0.6201f, 0.4498f, 0.2642f, 0.1799f, 0.7783f, 0.791f, 0.7989f, 0.6661f, 0.1195f, 0.0738f, 0.6871f, 0.2186f, 0.5475f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.545455, evaluator.auroc());
        assertEquals(0.440478, evaluator.positiveAUPRC());
        assertEquals(0.606677, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 0f, 1f, 1f, 0f, 0f, 1f, 1f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 1f, 1f, 1f};
        predictions = new float[]{0.2027f, 0.9584f, 0.3749f, 0.8901f, 0.6645f, 0.5583f, 0.0964f, 0.7465f, 0.6272f, 0.69f, 0.2839f, 0.4572f, 0.5565f, 0.7818f, 0.1958f, 0.3039f, 0.7251f, 0.3444f, 0.674f, 0.6733f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.575758, evaluator.auroc());
        assertEquals(0.549080, evaluator.positiveAUPRC());
        assertEquals(0.491238, evaluator.negativeAUPRC());

        truth = new float[]{1f, 1f, 1f, 1f, 0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 1f};
        predictions = new float[]{0.292f, 0.7778f, 0.5906f, 0.9842f, 0.6979f, 0.0371f, 0.3442f, 0.7081f, 0.1299f, 0.8195f, 0.8583f, 0.5671f, 0.1629f, 0.0973f, 0.025f, 0.26f, 0.7195f, 0.2239f, 0.1304f, 0.6407f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.737374, evaluator.auroc());
        assertEquals(0.738097, evaluator.positiveAUPRC());
        assertEquals(0.326252, evaluator.negativeAUPRC());

        truth = new float[]{0f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 1f, 0f, 0f, 1f, 0f, 0f};
        predictions = new float[]{0.1987f, 0.1848f, 0.4376f, 0.2554f, 0.3905f, 0.578f, 0.4156f, 0.6053f, 0.0704f, 0.2705f, 0.9555f, 0.4404f, 0.6578f, 0.2747f, 0.0378f, 0.4284f, 0.0775f, 0.6285f, 0.1261f, 0.0416f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.780220, evaluator.auroc());
        assertEquals(0.824571, evaluator.positiveAUPRC());
        assertEquals(0.486957, evaluator.negativeAUPRC());

        truth = new float[]{1f, 1f, 1f, 1f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 1f, 1f, 0f, 0f, 0f, 0f, 1f};
        predictions = new float[]{0.5129f, 0.7529f, 0.5049f, 0.7711f, 0.3719f, 0.7701f, 0.0357f, 0.6133f, 0.9735f, 0.5171f, 0.9124f, 0.753f, 0.1f, 0.7657f, 0.6783f, 0.6809f, 0.9546f, 0.6686f, 0.7341f, 0.2634f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.281250, evaluator.auroc());
        assertEquals(0.461711, evaluator.positiveAUPRC());
        assertEquals(0.701329, evaluator.negativeAUPRC());

        truth = new float[]{1f, 1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 1f, 0f, 0f};
        predictions = new float[]{0.453f, 0.1141f, 0.8012f, 0.1928f, 0.1965f, 0.3308f, 0.8917f, 0.0128f, 0.2003f, 0.0287f, 0.9558f, 0.238f, 0.0073f, 0.8608f, 0.0667f, 0.1515f, 0.5792f, 0.9297f, 0.2759f, 0.435f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.484848, evaluator.auroc());
        assertEquals(0.453849, evaluator.positiveAUPRC());
        assertEquals(0.572165, evaluator.negativeAUPRC());

        truth = new float[]{1f, 0f, 0f, 0f, 1f, 0f, 1f, 1f, 1f, 1f, 0f, 1f, 0f, 0f, 0f, 0f, 0f, 0f, 1f, 0f};
        predictions = new float[]{0.1189f, 0.5681f, 0.2917f, 0.3119f, 0.3544f, 0.984f, 0.2943f, 0.6003f, 0.5822f, 0.2655f, 0.3975f, 0.7453f, 0.0541f, 0.3904f, 0.9671f, 0.4914f, 0.4318f, 0.255f, 0.6661f, 0.7569f};
        init(predictions, truth);
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.479167, evaluator.auroc());
        assertEquals(0.371452, evaluator.positiveAUPRC());
        assertEquals(0.689192, evaluator.negativeAUPRC());
    }
}
