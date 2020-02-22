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

    protected abstract T getEvaluator();

    @Before
    public void setUp() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(
                H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

        predicate = StandardPredicate.get(
                "EvaulatorTestPredicate",
                new ConstantType[]{ConstantType.UniqueIntID, ConstantType.UniqueIntID});
        dataStore.registerPredicate(predicate);

        Partition targetPartition = dataStore.getPartition("targets");
        Partition truthPartition = dataStore.getPartition("truth");

        // Create five RVAs with values [1.0, 0.8, 0.6, 0.4, 0.2].
        Inserter inserter = dataStore.getInserter(predicate, targetPartition);
        for (int i = 0; i < 5; i++) {
            inserter.insertValue(1.0 - (i / 5.0), new UniqueIntID(i), new UniqueIntID(i));
        }

        // Create four truth atoms (note that this makes one latent target).
        // Evens will have the value 1.0, odds will have 0.0.
        inserter = dataStore.getInserter(predicate, truthPartition);
        for (int i = 0; i < 4; i++) {
            inserter.insertValue((i % 2 == 0) ? 1.0 : 0.0, new UniqueIntID(i), new UniqueIntID(i));
        }

        // The full map will be (target, truth):
        // (1.0, 1.0)
        // (0.8, 0.0)
        // (0.6, 1.0)
        // (0.4, 0.0)

        // Redefine the truth database with no atoms in the write partition.
        Database results = dataStore.getDatabase(targetPartition);
        Database truth = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        PersistedAtomManager atomManager = new PersistedAtomManager(results);
        trainingMap = new TrainingMap(atomManager, truth);

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
     * Just make sure the evaluator runs, don't worry about specific numbers.
     */
    @Test
    public void testBase() {
        Evaluator evaluator = getEvaluator();
        evaluator.compute(trainingMap, predicate);

        boolean higherBetter = evaluator.isHigherRepresentativeBetter();
        double score = evaluator.getRepresentativeMetric();
    }
}
