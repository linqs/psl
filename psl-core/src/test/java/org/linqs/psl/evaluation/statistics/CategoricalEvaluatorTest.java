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
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.UnmanagedRandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;

import org.junit.Before;
import org.junit.Test;

import java.util.Set;

public class CategoricalEvaluatorTest extends EvaluatorTest<CategoricalEvaluator> {
    @Before
    @Override
    public void setUp() {
        dataStore = DatabaseTestUtil.getDataStore();

        predicate = StandardPredicate.get(
                "CategoricalEvaulatorTestPredicate",
                new ConstantType[]{ConstantType.UniqueIntID, ConstantType.UniqueIntID});
        dataStore.registerPredicate(predicate);

        Partition targetPartition = dataStore.getPartition("targets");
        Partition truthPartition = dataStore.getPartition("truth");

        // Create the RVAs.
        Inserter inserter = dataStore.getInserter(predicate, targetPartition);
        inserter.insertValueRaw(1.0, new UniqueIntID(1), new UniqueIntID(1));

        inserter.insertValueRaw(0.99, new UniqueIntID(2), new UniqueIntID(1));
        inserter.insertValueRaw(1.00, new UniqueIntID(2), new UniqueIntID(2));

        inserter.insertValueRaw(0.0, new UniqueIntID(3), new UniqueIntID(1));
        inserter.insertValueRaw(0.0, new UniqueIntID(3), new UniqueIntID(2));
        inserter.insertValueRaw(1.0, new UniqueIntID(3), new UniqueIntID(3));

        inserter.insertValueRaw(0.30, new UniqueIntID(4), new UniqueIntID(1));
        inserter.insertValueRaw(0.20, new UniqueIntID(4), new UniqueIntID(2));
        inserter.insertValueRaw(0.25, new UniqueIntID(4), new UniqueIntID(3));
        inserter.insertValueRaw(0.25, new UniqueIntID(4), new UniqueIntID(4));

        // Create the truth atoms.
        inserter = dataStore.getInserter(predicate, truthPartition);
        inserter.insertValueRaw(1.0, new UniqueIntID(1), new UniqueIntID(1));  // Hit
        inserter.insertValueRaw(1.0, new UniqueIntID(2), new UniqueIntID(1));  // Miss
        inserter.insertValueRaw(1.0, new UniqueIntID(3), new UniqueIntID(1));  // Miss
        inserter.insertValueRaw(1.0, new UniqueIntID(4), new UniqueIntID(1));  // Hit

        // Redefine the truth database with no atoms in the write partition.
        Database results = dataStore.getDatabase(targetPartition);
        Database truth = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        trainingMap = new TrainingMap(results, truth);

        // Since we only need the map, we can close all the databases.
        results.close();
        truth.close();
    }

    @Override
    protected CategoricalEvaluator getEvaluator() {
        return new CategoricalEvaluator();
    }

    @Test
    public void testAccuracy() {
        CategoricalEvaluator evaluator = new CategoricalEvaluator();
        evaluator.compute(trainingMap, predicate);
        assertEquals(0.5, evaluator.accuracy());

        // Check fgr all the predicted atoms.
        GroundAtom[] expected = new GroundAtom[]{
            new UnmanagedRandomVariableAtom(predicate, new Constant[]{new UniqueIntID(1), new UniqueIntID(1)}, 1.0f),
            new UnmanagedRandomVariableAtom(predicate, new Constant[]{new UniqueIntID(2), new UniqueIntID(2)}, 1.0f),
            new UnmanagedRandomVariableAtom(predicate, new Constant[]{new UniqueIntID(3), new UniqueIntID(3)}, 1.0f),
            new UnmanagedRandomVariableAtom(predicate, new Constant[]{new UniqueIntID(4), new UniqueIntID(1)}, 0.3f),
        };

        Set<GroundAtom> actual = evaluator.getPredictedCategories(trainingMap, predicate);
        for (GroundAtom atom : expected) {
            if (!actual.contains(atom)) {
                fail("Did not exoected atom in predictions: " + atom);
            }
        }
    }
}
