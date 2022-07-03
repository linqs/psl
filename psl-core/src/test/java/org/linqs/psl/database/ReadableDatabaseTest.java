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
package org.linqs.psl.database;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.mpe.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class ReadableDatabaseTest extends PSLBaseTest {
    @Test
    public void testGetAtom() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
                assertNotNull(db.getAtom(predicate, arg));
            }
        };
        testHelper(function, "getAtom");
    }

    @Test
    public void testHasAtom() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg){
                StandardPredicate predicate = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
                assertTrue(db.hasAtom(predicate, arg));
            }
        };
        testHelper(function, "hasAtom");
    }

    @Test
    public void testCountAllGroundAtoms() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
                assertEquals(5, db.countAllGroundAtoms(predicate));
            }
        };
        testHelper(function, "countAllGroundAtoms");
    }

    @Test
    public void testCountAllGroundRandomVariableAtoms() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Friends", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
                assertEquals(20, db.countAllGroundRandomVariableAtoms(predicate));
            }
        };
        testHelper(function, "countAllGroundRandomVariableAtoms");
    }

    @Test
    public void testGetAllGroundAtoms() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
                assertEquals(5, db.getAllGroundAtoms(predicate).size());
            }
        };
        testHelper(function, "getAllGroundAtoms");
    }

    @Test
    public void testGetAllGroundRandomVariableAtoms() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Friends", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
                assertEquals(20, db.getAllGroundRandomVariableAtoms(predicate).size());
            }
        };
        testHelper(function, "getAllGroundRandomVariableAtoms");
    }

    @Test
    public void testGetAllGroundObservedAtoms() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
                assertEquals(5, db.getAllGroundObservedAtoms(predicate).size());
            }
        };
        testHelper(function, "getAllGroundObservedAtoms");
    }

    @Test
    public void testExecuteQuery() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Friends");
                DatabaseQuery query = new DatabaseQuery(new QueryAtom(predicate, arg, new Variable("A")));
                ResultList results = db.executeQuery(query);
                assertNotNull(results);
                assertEquals(4, results.size());
            }
        };
        testHelper(function, "executeQuery");
    }

    @Test
    public void testExecuteGroundingQuery() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Friends");
                QueryResultIterable results = db.executeGroundingQuery(new QueryAtom(predicate, arg, new Variable("A")));
                assertNotNull(results);

                int count = 0;
                for (Constant[] row : results) {
                    count++;
                }
                assertEquals(4, count);
            }
        };
        testHelper(function, "executeGroundingQuery");
    }

    @Test
    public void testIsClosed() {
        DatabaseFunction function = new DatabaseFunction() {
            @Override
            public void doWork(ReadableDatabase db, Constant arg) {
                StandardPredicate predicate = StandardPredicate.get("Friends", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
                assertFalse(db.isClosed(predicate));
            }
        };
        testHelper(function, "isClosed");
    }

    /**
     * Helper function for testing the ReadableDatabase interface functions.
     */
    private void testHelper(DatabaseFunction function, String name) {
        TestModel.ModelInformation info = TestModel.getModel();
        Predicate functionPredicate = ExternalFunctionalPredicate.get(name + "_test", function);

        // Add a rule using the new function.
        // 10: Person(A) & Person(B) & Function(A) & (A != B) -> Friends(A, B) ^2
        Formula ruleFormula = new Implication(
            new Conjunction(
                new QueryAtom(info.predicates.get("Person"), new Variable("A")),
                new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                new QueryAtom(functionPredicate, new Variable("A")),
                new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
            ),
            new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
        );

        Rule rule = new WeightedLogicalRule(ruleFormula, 11.0f, true);
        info.model.addRule(rule);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);

        InferenceApplication inference = new MPEInference(info.model.getRules(), inferDB);
        inference.inference();
        inference.close();
        inferDB.close();
    }

    /**
     * A database ExternalFunction.
     * Only returns 1, but keeps track of how many times it was called.
     * The number of arguments it accepts is set on construction.
     */
    private abstract class DatabaseFunction implements ExternalFunction {
        @Override
        public int getArity() {
            return 1;
        }

        @Override
        public ConstantType[] getArgumentTypes() {
            return new ConstantType[]{ConstantType.UniqueStringID};
        }

        @Override
        public double getValue(ReadableDatabase db, Constant... args) {
            doWork(db, args[0]);
            return 1.0;
        }

        public abstract void doWork(ReadableDatabase db, Constant arg);
    }
}
