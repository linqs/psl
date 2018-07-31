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

import org.linqs.psl.application.learning.weight.TrainingMap;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Base testing functionality for all metric computers.
 */
public abstract class EvaluatorTest<T extends Evaluator> {
	protected DataStore dataStore;
	protected StandardPredicate predicate;
	protected TrainingMap trainingMap;

	protected abstract T getComputer();

	@Before
	public void setUp() {
		dataStore = new RDBMSDataStore(new H2DatabaseDriver(
				H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

		predicate = StandardPredicate.get(
				"DiscretePredictionComparatorTest_same"
				, new ConstantType[]{ConstantType.UniqueIntID, ConstantType.UniqueIntID}
			);
		dataStore.registerPredicate(predicate);

		Partition targetPartition = dataStore.getPartition("targets");
		Partition truthPartition = dataStore.getPartition("truth");

		// Create some canned ground inference atoms
		Constant[][] cannedTerms = new Constant[5][];
		cannedTerms[0] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(1) };
		cannedTerms[1] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(2) };
		cannedTerms[2] = new Constant[]{ new UniqueIntID(3), new UniqueIntID(3) };
		cannedTerms[3] = new Constant[]{ new UniqueIntID(4), new UniqueIntID(4) };
		cannedTerms[4] = new Constant[]{ new UniqueIntID(5), new UniqueIntID(5) };

		// Insert the predicated values.
		Inserter inserter = dataStore.getInserter(predicate, targetPartition);
		for (Constant[] terms : cannedTerms) {
			inserter.insertValue(0.8, terms);
		}

		// create some ground truth atoms
		Constant[][] baselineTerms = new Constant[4][];
		baselineTerms[0] = new Constant[]{ new UniqueIntID(1), new UniqueIntID(1) };
		baselineTerms[1] = new Constant[]{ new UniqueIntID(2), new UniqueIntID(2) };
		baselineTerms[2] = new Constant[]{ new UniqueIntID(3), new UniqueIntID(3) };
		baselineTerms[3] = new Constant[]{ new UniqueIntID(4), new UniqueIntID(4) };

		// Insert the truth values.
		inserter = dataStore.getInserter(predicate, truthPartition);
		for (Constant[] terms : baselineTerms) {
			inserter.insertValue(1.0, terms);
		}

		// Redefine the truth database with no atoms in the write partition.
		Database results = dataStore.getDatabase(targetPartition);
		Database truth = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		PersistedAtomManager atomManager = new PersistedAtomManager(results);
		trainingMap = new TrainingMap(atomManager, truth, true);

		// Since we only need the map, we can close all the databases.
		results.close();
		truth.close();
	}

	@After
	public void cleanup() {
		trainingMap = null;
		dataStore.close();
	}

	/**
	 * Just make sure it runs, don't worry about specific numbers.
	 */
	@Test
	public void baseTest() {
		Evaluator computer = getComputer();
		computer.compute(trainingMap, predicate);

		boolean higherBetter = computer.isHigherRepresentativeBetter();
		double score = computer.getRepresentativeMetric();
	}
}
