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

public class ContinuousEvaluatorTest extends EvaluatorTest<ContinuousEvaluator> {
    @Override
    protected ContinuousEvaluator getEvaluator() {
        return new ContinuousEvaluator();
    }

    @Test
    public void testMAE() {
        ContinuousEvaluator computer = new ContinuousEvaluator();
        computer.compute(trainingMap, predicate);
        assertEquals(0.40, computer.mae());
    }

    @Test
    public void testMSE() {
        ContinuousEvaluator computer = new ContinuousEvaluator();
        computer.compute(trainingMap, predicate);
        assertEquals(0.24, computer.mse());
    }
}
