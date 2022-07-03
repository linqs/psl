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

public class DiscreteEvaluatorTest extends EvaluatorTest<DiscreteEvaluator> {
    @Override
    protected DiscreteEvaluator getEvaluator() {
        return new DiscreteEvaluator();
    }

    @Test
    public void testPrecision() {
        double threshold = -1.0;
        DiscreteEvaluator evaluator = null;

        threshold = 0.3;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.positivePrecision());

        threshold = 0.7;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.positivePrecision());

        threshold = 1.0;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 1.0, evaluator.positivePrecision());
    }

    @Test
    public void testRecall() {
        double threshold = -1.0;
        DiscreteEvaluator evaluator = null;

        threshold = 0.3;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 1.0, evaluator.positiveRecall());

        threshold = 0.7;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.positiveRecall());

        threshold = 1.0;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.positiveRecall());
    }

    @Test
    public void testF1() {
        double threshold = -1.0;
        DiscreteEvaluator evaluator = null;

        threshold = 0.3;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, (2.0 * 0.5 * 1.0) / (0.5 + 1.0), evaluator.f1());

        threshold = 0.7;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, (2.0 * 0.5 * 0.5) / (0.5 + 0.5), evaluator.f1());

        threshold = 1.0;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, (2.0 * 1.0 * 0.5) / (1.0 + 0.5), evaluator.f1());
    }

    @Test
    public void testAccuracy() {
        double threshold = -1.0;
        DiscreteEvaluator evaluator = null;

        threshold = 0.3;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.accuracy());

        threshold = 0.7;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.accuracy());

        threshold = 1.0;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.75, evaluator.accuracy());
    }

    @Test
    public void testPrecisionNegativeClass() {
        double threshold = -1.0;
        DiscreteEvaluator evaluator = null;

        threshold = 0.3;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.0, evaluator.negativePrecision());

        threshold = 0.7;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.negativePrecision());

        threshold = 1.0;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 2.0 / 3.0, evaluator.negativePrecision());
    }

    @Test
    public void testRecallNegativeClass() {
        double threshold = -1.0;
        DiscreteEvaluator evaluator = null;

        threshold = 0.3;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.0, evaluator.negativeRecall());

        threshold = 0.7;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 0.5, evaluator.negativeRecall());

        threshold = 1.0;
        evaluator = new DiscreteEvaluator(threshold);
        evaluator.compute(trainingMap, predicate);
        assertEquals("Threshold: " + threshold, 1.0, evaluator.negativeRecall());
    }
}
