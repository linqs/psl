/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
	protected RankingEvaluator getComputer() {
		return new RankingEvaluator();
	}

	@Test
	public void testAUROC() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			RankingEvaluator computer = new RankingEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double value = computer.auroc();

			if (threshold <= 0.8) {
				assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
			} else {
				assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testPositiveAUPRC() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			RankingEvaluator computer = new RankingEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double value = computer.positiveAUPRC();

			if (threshold <= 0.8) {
				assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
			} else {
				assertEquals("Threshold: " + threshold, 1.0, value, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testNegativeAUPRC() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			RankingEvaluator computer = new RankingEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double value = computer.negativeAUPRC();

			if (threshold <= 0.8) {
				assertEquals("Threshold: " + threshold, 0.5, value, MathUtils.EPSILON);
			} else {
				assertEquals("Threshold: " + threshold, 0.5, value, MathUtils.EPSILON);
			}
		}
	}
}
