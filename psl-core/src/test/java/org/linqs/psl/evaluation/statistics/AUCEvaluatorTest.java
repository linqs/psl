/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.util.MathUtils;

import static org.junit.Assert.assertEquals;

import org.linqs.psl.util.MathUtils;

import org.junit.Before;
import org.junit.Test;

public class RankingEvaluatorTest extends EvaluatorTest<RankingEvaluator> {
    @Before
    @Override
    public void setUp() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(
                H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

        predicate = StandardPredicate.get(
                "OtherRankingEvaulatorTestPredicate",
                new ConstantType[]{ConstantType.UniqueIntID, ConstantType.UniqueIntID});
        dataStore.registerPredicate(predicate);

        Partition targetPartition = dataStore.getPartition("targets");
        Partition truthPartition = dataStore.getPartition("truth");

        // Create the RVAs.
        Inserter inserter = dataStore.getInserter(predicate, targetPartition);
        inserter.insertValue(0.93, new UniqueIntID(1), new UniqueIntID(0));
        inserter.insertValue(0.17, new UniqueIntID(2), new UniqueIntID(0));
        inserter.insertValue(0.91, new UniqueIntID(3), new UniqueIntID(1));

        inserter.insertValue(0.94, new UniqueIntID(4), new UniqueIntID(1));
        inserter.insertValue(0.63, new UniqueIntID(5), new UniqueIntID(1));
        inserter.insertValue(0.68, new UniqueIntID(6), new UniqueIntID(0));

        inserter.insertValue(0.52, new UniqueIntID(7), new UniqueIntID(1));
        inserter.insertValue(0.16, new UniqueIntID(8), new UniqueIntID(1));
        inserter.insertValue(0.76, new UniqueIntID(9), new UniqueIntID(0));


        // Create the truth atoms.
        inserter = dataStore.getInserter(predicate, truthPartition);
        inserter.insertValue(1.0, new UniqueIntID(3), new UniqueIntID(1));  // Hit
        inserter.insertValue(1.0, new UniqueIntID(6), new UniqueIntID(0));  // Hit
        inserter.insertValue(0, new UniqueIntID(2), new UniqueIntID(0));  // Not ranked

        // Redefine the truth database with no atoms in the write partition.
        Database results = dataStore.getDatabase(targetPartition);
        Database truth = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        PersistedAtomManager atomManager = new PersistedAtomManager(results);
        trainingMap = new TrainingMap(atomManager, truth);

        // Since we only need the map, we can close all the databases.
        results.close();
        truth.close();
    }

    @Override
    protected RankingEvaluator getEvaluator() {
        return new RankingEvaluator();
    }

    @Test
    public void testMRR() {
        RankingEvaluator evaluator = new RankingEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.416667, evaluator.mrr(), MathUtils.EPSILON);
    }
}