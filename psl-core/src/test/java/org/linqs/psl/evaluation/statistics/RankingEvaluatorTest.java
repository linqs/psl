/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import static org.junit.Assert.assertEquals;

import org.linqs.psl.util.MathUtils;

import org.junit.Test;

public class RankingEvaluatorTest extends EvaluatorTest<RankingEvaluator> {
    @Override
    protected RankingEvaluator getEvaluator() {
        return new RankingEvaluator();
    }

    @Test
    public void testAUROC() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.75, evaluator.auroc(), MathUtils.EPSILON);
    }

    @Test
    public void testPositiveAUPRC() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.875, evaluator.positiveAUPRC(), MathUtils.EPSILON);
    }

    @Test
    public void testNegativeAUPRC() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(13.0 / 24.0, evaluator.negativeAUPRC(), MathUtils.EPSILON);
    }
}
