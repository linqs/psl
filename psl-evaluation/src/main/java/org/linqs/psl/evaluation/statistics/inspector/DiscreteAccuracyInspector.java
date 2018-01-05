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
package org.linqs.psl.evaluation.statistics.inspector;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Queries;
import org.linqs.psl.evaluation.statistics.DiscretePredictionComparator;
import org.linqs.psl.evaluation.statistics.DiscretePredictionStatistics;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.inspector.DatabaseReasonerInspector;
import org.linqs.psl.reasoner.inspector.ReasonerInspector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ReasonerInspector that will look compute various discrete accuracy stats (reccall, precision, etc).
 */
public class DiscreteAccuracyInspector extends DatabaseReasonerInspector {
	private static final Logger log = LoggerFactory.getLogger(DiscreteAccuracyInspector.class);

	// TODO(eriq): Use config
	public static final double DEFAULT_TRUTH_THRESHOLD = 0.5;

	private double truthThreshold;

	public DiscreteAccuracyInspector(ConfigBundle config) {
		super(config);

		truthThreshold = DEFAULT_TRUTH_THRESHOLD;
	}

	@Override
	public boolean update(Reasoner reasoner, ReasonerInspector.ReasonerStatus status) {
		log.info("Reasoner inspection update -- " + status);

		Database rvDatabase = getRandomVariableDatabase();
		Database truthDatabase = getTruthDatabase(rvDatabase);

		DiscretePredictionComparator comparator = new DiscretePredictionComparator(rvDatabase);
		comparator.setThreshold(truthThreshold);
		comparator.setBaseline(truthDatabase);

		for (StandardPredicate targetPredicate : rvDatabase.getRegisteredPredicates()) {
			// Before we run evaluation, ensure that the truth database actaully has instances of the target predicate.
			if (Queries.countAllGroundAtoms(truthDatabase, targetPredicate) == 0) {
				continue;
			}

			DiscretePredictionStatistics stats = comparator.compare(targetPredicate);

			double accuracy = stats.getAccuracy();
			double error = stats.getError();
			double positivePrecision = stats.getPrecision(DiscretePredictionStatistics.BinaryClass.POSITIVE);
			double positiveRecall = stats.getRecall(DiscretePredictionStatistics.BinaryClass.POSITIVE);
			double negativePrecision = stats.getPrecision(DiscretePredictionStatistics.BinaryClass.NEGATIVE);
			double negativeRecall = stats.getRecall(DiscretePredictionStatistics.BinaryClass.NEGATIVE);

			log.info("{} --" +
				" Accuracy: {}, Error: {}," +
				" Positive Class Precision: {}, Positive Class Recall: {}," +
				" Negative Class Precision: {}, Negative Class Recall: {},",
				targetPredicate.getName(),
				accuracy, error, positivePrecision, positiveRecall, negativePrecision, negativeRecall);
		}

		truthDatabase.close();
		log.info("Reasoner inspection update complete");

		return true;
	}
}
