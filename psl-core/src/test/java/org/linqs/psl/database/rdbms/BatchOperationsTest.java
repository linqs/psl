/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BatchOperationsTest extends PSLBaseTest {
    private TestModel.ModelInformation model;
    private Database database;

    @Before
    public void setup() {
        model = TestModel.getModel();
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(model.predicates.get("Nice"));
        toClose.add(model.predicates.get("Person"));
        database = model.dataStore.getDatabase(model.targetPartition, toClose, model.observationPartition);
    }

    @After
    public void cleanup() {
        database.close();
        model.dataStore.close();
    }

    @Test
    public void testSerial() {
        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                RandomVariableAtom atom = new RandomVariableAtom(
                        model.predicates.get("Friends"),
                        new Constant[]{new UniqueStringID("" + i), new UniqueStringID("" + j)},
                        1.0f, model.targetPartition.getID());
                atoms.add(atom);
            }
        }

        for (RandomVariableAtom atom : atoms) {
            database.getAtomStore().addAtom(atom);
            database.getAtomStore().commit();
        }

        assertEquals(database.countAllGroundAtoms(model.predicates.get("Friends")), 120);
    }

    @Test
    public void testBatch() {
        List<RandomVariableAtom> atoms = new ArrayList<RandomVariableAtom>();

        for (int i = 0; i < 10; i++) {
            for (int j = 0; j < 10; j++) {
                RandomVariableAtom atom = new RandomVariableAtom(
                        model.predicates.get("Friends"),
                        new Constant[]{new UniqueStringID("" + i), new UniqueStringID("" + j)},
                        1.0f, model.targetPartition.getID());
                atoms.add(atom);
            }
        }

        for (RandomVariableAtom atom : atoms) {
            database.getAtomStore().addAtom(atom);
        }
        database.getAtomStore().commit();

        assertEquals(database.countAllGroundAtoms(model.predicates.get("Friends")), 120);
    }
}
