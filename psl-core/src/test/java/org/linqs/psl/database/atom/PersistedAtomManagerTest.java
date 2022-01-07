/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.database.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class PersistedAtomManagerTest extends PSLBaseTest {
    /**
     * Base test to see if the PAM gets populated and can get atoms.
     */
    @Test
    public void baseTest() {
        TestModel.ModelInformation info = TestModel.getModel();

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database database = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);

        PersistedAtomManager atomManager = new PersistedAtomManager(database);

        GroundAtom atom = atomManager.getAtom(info.predicates.get("Friends"), new UniqueStringID("Alice"), new UniqueStringID("Bob"));
        assertTrue(atom instanceof RandomVariableAtom);
        assertEquals(atom.getValue(), 1.0);

        atom = atomManager.getAtom(info.predicates.get("Nice"), new UniqueStringID("Alice"));
        assertTrue(atom instanceof ObservedAtom);
        assertEquals(atom.getValue(), 0.9);

        database.close();
    }

    /**
     * Having an atom as both observed and a target should throw an exception.
     */
    @Test
    public void testErrorOnObservedTargets() {
        TestModel.ModelInformation info = TestModel.getModel();

        Inserter inserter = info.dataStore.getInserter(info.predicates.get("Friends"), info.observationPartition);
        inserter.insert(new UniqueStringID("Alice"), new UniqueStringID("Bob"));

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database database = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);

        try {
            PersistedAtomManager atomManager = new PersistedAtomManager(database);
            fail("No exception thrown on a target atom that is also observed.");
        } catch (IllegalStateException ex) {
            // Expected
        } finally {
            database.close();
        }
    }
}
