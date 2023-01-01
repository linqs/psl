/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Base testing functionality for all metric computers.
 */
public abstract class EvaluatorTest<T extends Evaluator> extends PSLBaseTest {
    protected DataStore dataStore;
    protected StandardPredicate predicate;
    protected TrainingMap trainingMap;

    protected abstract T getEvaluator();

    @Before
    public void setUp() {
        // Initialize with the default setup:
        // The full map will be (target, truth):
        // (1.0, 1.0)
        // (0.8, 0.0)
        // (0.6, 1.0)
        // (0.4, 0.0)

        float[] predictions = new float[]{1.0f, 0.8f, 0.6f, 0.4f, 0.2f};
        float[] truth = new float[]{1.0f, 0.0f, 1.0f, 0.0f};

        init(predictions, truth);
    }

    protected void init(float[] predictions, float[] truth) {
        cleanup();

        dataStore = DatabaseTestUtil.getDataStore();

        predicate = StandardPredicate.get(
                "EvaulatorTestPredicate",
                new ConstantType[]{ConstantType.UniqueIntID, ConstantType.UniqueIntID});
        dataStore.registerPredicate(predicate);

        Partition targetPartition = dataStore.getPartition("targets");
        Partition truthPartition = dataStore.getPartition("truth");

        Inserter inserter = dataStore.getInserter(predicate, targetPartition);
        for (int i = 0; i < predictions.length; i++) {
            inserter.insertValueRaw(predictions[i], new UniqueIntID(i), new UniqueIntID(i));
        }

        inserter = dataStore.getInserter(predicate, truthPartition);
        for (int i = 0; i < truth.length; i++) {
            inserter.insertValueRaw(truth[i], new UniqueIntID(i), new UniqueIntID(i));
        }

        // Redefine the truth database with no atoms in the write partition.
        Database resultsDB = dataStore.getDatabase(targetPartition);
        Database truthDB = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        trainingMap = new TrainingMap(resultsDB, truthDB);

        // Since we only need the map, we can close all the databases.
        resultsDB.close();
        truthDB.close();
    }

    @After
    public void cleanup() {
        trainingMap = null;

        if (dataStore != null) {
            dataStore.close();
            dataStore = null;
        }
    }

    /**
     * Just make sure the evaluator runs, don't worry about specific numbers.
     */
    @Test
    public void testBase() {
        Evaluator evaluator = getEvaluator();
        evaluator.compute(trainingMap, predicate);
        double score = evaluator.getNormalizedRepMetric();
    }
}
