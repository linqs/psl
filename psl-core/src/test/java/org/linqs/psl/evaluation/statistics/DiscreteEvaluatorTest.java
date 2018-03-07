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

public class DiscreteEvaluatorTest extends EvaluatorTest<DiscreteEvaluator> {
	@Override
	protected DiscreteEvaluator getComputer() {
		return new DiscreteEvaluator();
	}

	@Test
	public void testPrecision() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			DiscreteEvaluator computer = new DiscreteEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double precision = computer.positivePrecision();

			if (threshold <= 0.8) {
				assertEquals("Threshold: " + threshold, 0.8, precision, MathUtils.EPSILON);
			} else {
				assertEquals("Threshold: " + threshold, 0.0, precision, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testRecall() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			DiscreteEvaluator computer = new DiscreteEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double recall = computer.positiveRecall();

			if (threshold <= 0.8) {
				assertEquals("Threshold: " + threshold, 1.0, recall, MathUtils.EPSILON);
			} else {
				assertEquals("Threshold: " + threshold, 0.0, recall, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testF1() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			DiscreteEvaluator computer = new DiscreteEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double f1 = computer.f1();

			if (threshold <= 0.8) {
				assertEquals(2.0 * 0.8 / 1.8, f1, MathUtils.EPSILON);
			} else {
				assertEquals(0.0, f1, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testAccuracy() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			DiscreteEvaluator computer = new DiscreteEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double accuracy = computer.accuracy();

			if (threshold <= 0.8) {
				assertEquals(0.8, accuracy, MathUtils.EPSILON);
			} else {
				assertEquals(0.2, accuracy, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testPrecisionNegativeClass() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			DiscreteEvaluator computer = new DiscreteEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double precision = computer.negativePrecision();

			if (threshold <= 0.8) {
				assertEquals(0.0, precision, MathUtils.EPSILON);
			} else {
				assertEquals(0.2, precision, MathUtils.EPSILON);
			}
		}
	}

	@Test
	public void testRecallNegativeClass() {
		for (double threshold = 0.1; threshold <= 1.0; threshold += 0.1) {
			DiscreteEvaluator computer = new DiscreteEvaluator(threshold);
			computer.compute(trainingMap, predicate);
			double recall = computer.negativeRecall();

			if (threshold <= 0.8) {
				assertEquals(0.0, recall, MathUtils.EPSILON);
			} else {
				assertEquals(1.0, recall, MathUtils.EPSILON);
			}
		}
	}
}
