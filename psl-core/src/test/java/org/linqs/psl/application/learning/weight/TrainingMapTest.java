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
package org.linqs.psl.application.learn.weight;

import static org.junit.Assert.assertEquals;

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
 * Test training maps, which create the mapping between RVAs and their observed truth values.
 */
public class TrainingMapTest {
    private DataStore dataStore;
    private StandardPredicate predicate;
    private TrainingMap trainingMap;
    private Database targetsDatabase;
    private Database truthDatabase;

    /**
     * Create the six types of interactions that can occur bwteen the target and truth databases:
     * (unobserved, observed, not existent) x (observed, not existent).
     * An unobserved atom will also be added to the truth database, but it should be ignored.
     */
    @Before
    public void setUp() {
        dataStore = new RDBMSDataStore(new H2DatabaseDriver(
                H2DatabaseDriver.Type.Memory, this.getClass().getName(), true));

        predicate = StandardPredicate.get(
                "TrainingMapTestPredicate",
                new ConstantType[]{ConstantType.UniqueIntID});
        dataStore.registerPredicate(predicate);

        // Create four targets: two unobserved and two observed.
        // (0, 1) = unobserved.
        // (2, 3) = observed.
        // (4, 5) = not existent.

        Partition targetOpenPartition = dataStore.getPartition("targetOpen");
        Partition targetClosedPartition = dataStore.getPartition("targetClosed");

        Inserter targetOpenInserter = dataStore.getInserter(predicate, targetOpenPartition);
        Inserter targetClosedInserter = dataStore.getInserter(predicate, targetClosedPartition);

        for (int i = 0; i < 4; i++) {
            if (i < 2) {
                targetOpenInserter.insertValue(1.0, new UniqueIntID(i));
            } else {
                targetClosedInserter.insertValue(1.0, new UniqueIntID(i));
            }
        }

        // Create four truth atoms: three observed, one unobserved.
        // (0, 2, 4) = observed.
        // (1, 3, 5) = not existent.
        // (6) = unobserved.

        Partition truthOpenPartition = dataStore.getPartition("truthOpen");
        Partition truthClosedPartition = dataStore.getPartition("truthClosed");

        Inserter truthOpenInserter = dataStore.getInserter(predicate, truthOpenPartition);
        Inserter truthClosedInserter = dataStore.getInserter(predicate, truthClosedPartition);

        for (int i = 0; i < 7; i++) {
            if (i == 6) {
                truthOpenInserter.insertValue(1.0, new UniqueIntID(i));
            } else if (i % 2 == 0) {
                truthClosedInserter.insertValue(1.0, new UniqueIntID(i));
            }
        }

        // Set up the databases.
        targetsDatabase = dataStore.getDatabase(targetOpenPartition, targetClosedPartition);
        truthDatabase = dataStore.getDatabase(truthOpenPartition, truthClosedPartition);

        PersistedAtomManager atomManager = new PersistedAtomManager(targetsDatabase);
        trainingMap = new TrainingMap(atomManager, truthDatabase);
    }

    @After
    public void cleanup() {
        targetsDatabase.close();
        truthDatabase.close();

        trainingMap = null;
        dataStore.close();
    }

    /**
     * Make sure all the atoms got sorted correctly.
     */
    @Test
    public void testBase() {
        assertEquals(1, trainingMap.getLabelMap().size());
        assertEquals(1, trainingMap.getObservedMap().size());
        assertEquals(1, trainingMap.getLatentVariables().size());
        assertEquals(1, trainingMap.getMissingLabels().size());
        assertEquals(1, trainingMap.getMissingTargets().size());
    }
}
