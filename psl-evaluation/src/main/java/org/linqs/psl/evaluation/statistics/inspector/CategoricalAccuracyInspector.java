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
import org.linqs.psl.evaluation.statistics.CategoricalPredictionComparator;
import org.linqs.psl.evaluation.statistics.CategoricalPredictionStatistics;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.inspector.DatabaseReasonerInspector;
import org.linqs.psl.reasoner.inspector.ReasonerInspector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ReasonerInspector that will look compute various discrete accuracy stats (reccall, precision, etc).
 */
public class CategoricalAccuracyInspector extends DatabaseReasonerInspector {
	private static final Logger log = LoggerFactory.getLogger(CategoricalAccuracyInspector.class);

	// TODO(eriq): Config option for desired predicates.
	// TODO(eriq): We are currently asuming that the last argument is always the category index.

	public CategoricalAccuracyInspector(ConfigBundle config) {
		super(config);
	}

	@Override
	public boolean update(Reasoner reasoner, ReasonerInspector.ReasonerStatus status) {
		log.info("Reasoner inspection update -- " + status);

		Database rvDatabase = getRandomVariableDatabase();
		Database truthDatabase = getTruthDatabase(rvDatabase);

		CategoricalPredictionComparator comparator = new CategoricalPredictionComparator(rvDatabase);
		comparator.setBaseline(truthDatabase);

		for (StandardPredicate targetPredicate : rvDatabase.getRegisteredPredicates()) {
			// Before we run evaluation, ensure that the truth database actaully has instances of the target predicate.
			if (Queries.countAllGroundAtoms(truthDatabase, targetPredicate) == 0) {
				continue;
			}

			int[] categoryIndexes = new int[]{targetPredicate.getArity() - 1};
			comparator.setCategoryIndexes(categoryIndexes);

			CategoricalPredictionStatistics stats = comparator.compare(targetPredicate);

			double accuracy = stats.getAccuracy();
			double error = stats.getError();

			log.info("{} -- Accuracy: {}, Error: {}", targetPredicate.getName(), accuracy, (int)error);
		}

		truthDatabase.close();
		log.info("Reasoner inspection update complete");

		return true;
	}
}
