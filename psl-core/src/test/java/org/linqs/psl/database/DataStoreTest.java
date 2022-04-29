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

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.test.PSLBaseTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * Tests for classes that implement {@link DataStore}.
 */
public abstract class DataStoreTest extends PSLBaseTest {
    public static final String DATA_DIRNAME = "data";

    protected static final String[] GOOD_DATA_FILES = new String[]{
        "binary_no_value.txt",
        "binary_with_value.txt",
    };

    protected static final String[] BAD_DATA_FILES = new String[]{
        "binary_bad_value.txt",
        "binary_extra_arg.txt",
        "binary_missing_arg.txt",
        "binary_missing_value.txt",
    };

    protected StandardPredicate p1;
    protected StandardPredicate p2;
    protected StandardPredicate p3;
    protected StandardPredicate p4;
    protected FunctionalPredicate functionalPredicate1;

    protected DataStore datastore;

    protected List<Database> dbs;

    /**
     * @return the DataStore to be tested, should always be backed by the same persistence mechanism.
     */
    public abstract DataStore getDataStore(boolean clearDB, boolean persisted);

    /**
     * Default to a non-persisted data store (if available).
     */
    public DataStore getDataStore(boolean clearDB) {
        return getDataStore(clearDB, false);
    }

    /**
     * Deletes any files and releases any resources used by the tested DataStore
     * and its persistence mechanism
     */
    public abstract void cleanUp();

    @Before
    public void setUp() throws Exception {
        datastore = getDataStore(true);
        dbs = new LinkedList<Database>();

        p1 = StandardPredicate.get("P1", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        p2 = StandardPredicate.get("P2", ConstantType.String, ConstantType.String);
        p3 = StandardPredicate.get("P3", ConstantType.Double, ConstantType.Double);
        p4 = StandardPredicate.get("P4", ConstantType.UniqueIntID, ConstantType.Double);

        functionalPredicate1 = ExternalFunctionalPredicate.get("FP1", new ExternalFunction() {
            @Override
            public double getValue(ReadableDatabase db, Constant... args) {
                double a = ((DoubleAttribute) args[0]).getValue();
                double b = ((DoubleAttribute) args[1]).getValue();

                return Math.max(0.0, Math.min(1.0, (a + b) / 2));
            }

            @Override
            public int getArity() {
                return 2;
            }

            @Override
            public ConstantType[] getArgumentTypes() {
                return new ConstantType[] {ConstantType.Double, ConstantType.Double};
            }

            // Hack for testing.
            @Override
            public boolean equals(Object other) {
                return true;
            }
        });
    }

    @After
    public void tearDown() throws Exception {
        for (Database db : dbs) {
            db.close();
        }

        if (datastore != null) {
            datastore.close();
        }

        cleanUp();
    }

    @Test
    public void testInsertAndGetAtom() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);
        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);
        UniqueIntID c = new UniqueIntID(2);
        UniqueIntID d = new UniqueIntID(3);

        inserter.insert(a, b);
        inserter.insertValue(0.5, b, c);
        inserter.insertValue(0.25, c, d);

        Database db;
        GroundAtom atom;

        // Tests open predicate with atoms in write partition.
        db = datastore.getDatabase(datastore.getPartition("0"));
        atom = db.getAtom(p1, a, b);
        assertEquals(1.0, atom.getValue(), 0.0);
        assertTrue(atom instanceof RandomVariableAtom);

        atom = db.getAtom(p1, b, c);
        assertEquals(0.5, atom.getValue(), 0.0);
        assertTrue(atom instanceof RandomVariableAtom);

        atom = db.getAtom(p1, c, d);
        assertEquals(0.25, atom.getValue(), 0.0);
        assertTrue(atom instanceof RandomVariableAtom);

        atom = db.getAtom(p1, d, a);
        assertEquals(0.0, atom.getValue(), 0.0);
        assertTrue(atom instanceof RandomVariableAtom);

        db.close();

        // Tests open predicate with atoms in read partition.
        db = datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0"));
        atom = db.getAtom(p1, a, b);
        assertEquals(1.0, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, b, c);
        assertEquals(0.5, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, c, d);
        assertEquals(0.25, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        db.close();

        // Tests closed predicate with atoms in write partition.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(p1);
        db = datastore.getDatabase(datastore.getPartition("0"), toClose);
        atom = db.getAtom(p1, a, b);
        assertEquals(1.0, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, b, c);
        assertEquals(0.5, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, c, d);
        assertEquals(0.25, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, d, a);
        assertEquals(0.0, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        db.close();

        // Tests closed predicate with atoms in read partition.
        db = datastore.getDatabase(datastore.getPartition("1"), toClose, datastore.getPartition("0"));
        atom = db.getAtom(p1, a, b);
        assertEquals(1.0, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, b, c);
        assertEquals(0.5, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        atom = db.getAtom(p1, c, d);
        assertEquals(0.25, atom.getValue(), 0.0);
        assertTrue(atom instanceof ObservedAtom);

        db.close();
    }

    @Test
    public void testCommit() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Database db = datastore.getDatabase(datastore.getPartition("0"));

        RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        atom.setValue(0.5f);
        db.commit(atom);
        db.close();

        db = datastore.getDatabase(datastore.getPartition("0"));
        atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        assertEquals(0.5f, atom.getValue(), 0.0f);
        atom.setValue(1.0f);
        db.commit(atom);
        db.close();

        db = datastore.getDatabase(datastore.getPartition("0"));
        atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        assertEquals(1.0f, atom.getValue(), 0.0f);
        db.close();
    }

    @Test
    public void testDoubleCommit() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        atom.setValue(0.25f);
        db.commit(atom);
        atom.setValue(0.5f);
        db.commit(atom);
        db.close();

        db = datastore.getDatabase(datastore.getPartition("0"));
        atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        assertEquals(0.5f, atom.getValue(), 0.0f);
        atom.setValue(0.75f);
        db.commit(atom);
        atom.setValue(1.0f);
        db.commit(atom);
        db.close();

        db = datastore.getDatabase(datastore.getPartition("0"));
        atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        assertEquals(1.0f, atom.getValue(), 0.0f);
        db.close();
    }

    @Test
    public void testInsertTwoAtoms() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);
        UniqueIntID c = new UniqueIntID(2);
        UniqueIntID d = new UniqueIntID(3);

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        RandomVariableAtom atom1 = (RandomVariableAtom) db.getAtom(p1, a, b);
        RandomVariableAtom atom2 = (RandomVariableAtom) db.getAtom(p1, c, d);
        atom1.setValue(0.25f);
        atom2.setValue(0.75f);
        db.commit(atom1);
        db.commit(atom2);
        DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1,  new Variable("X"), new Variable("Y")));
        ResultList results = db.executeQuery(query);
        assertEquals(2, results.size());

        db.close();
    }

    @Test
    public void testStringEscaping() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p2);
        Database db = datastore.getDatabase(datastore.getPartition("0"));
        DatabaseQuery query = new DatabaseQuery(new QueryAtom(p2, new StringAttribute("a"), new StringAttribute("jk'a")));
        db.executeQuery(query);
    }

    @Test
    public void testPredicateRegistration() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        Set<StandardPredicate> registeredPredicates = datastore.getRegisteredPredicates();
        assertTrue(registeredPredicates.contains(p1));
    }

    // Functional predicates should be ignored at the database level.
    @Test
    public void testExternalFunctionalPredicate() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p3);
        Inserter inserter = datastore.getInserter(p3, datastore.getPartition("0"));
        inserter.insert(0.5, 1.0);
        inserter.insert(0.0, 0.0);

        Database db = datastore.getDatabase(datastore.getPartition("0"));

        Variable X = new Variable("X");
        Variable Y = new Variable("Y");
        Formula f = new Conjunction(new QueryAtom(p3, X, Y), new QueryAtom(functionalPredicate1, X, Y));
        ResultList results = db.executeQuery(new DatabaseQuery(f));
        assertEquals(2, results.size());

        GroundAtom atom = db.getAtom(functionalPredicate1, new DoubleAttribute(0.5), new DoubleAttribute(1.0));
        assertEquals(0.75f, atom.getValue(), 0.0f);

        atom = db.getAtom(functionalPredicate1, new DoubleAttribute(0.0), new DoubleAttribute(0.0));
        assertEquals(0.0f, atom.getValue(), 0.0f);
    }

    @Test
    public void testExecuteQuery() {
        if (datastore == null) {
            return;
        }

        Inserter inserter;
        Database db;
        DatabaseQuery query;
        Formula formula;
        ResultList results;
        Constant[] grounding;

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);
        UniqueIntID c = new UniqueIntID(2);
        UniqueIntID d = new UniqueIntID(3);
        UniqueIntID e = new UniqueIntID(4);
        UniqueIntID f = new UniqueIntID(5);

        Variable X = new Variable("X");
        Variable Y = new Variable("Y");
        Variable Z = new Variable("Z");

        datastore.registerPredicate(p1);
        datastore.registerPredicate(p4);

        // Tests a simple query
        inserter = datastore.getInserter(p1, datastore.getPartition("0"));
        inserter.insert(a, b);

        db = datastore.getDatabase(datastore.getPartition("0"));

        formula = new QueryAtom(p1, X, Y);
        results = db.executeQuery(new DatabaseQuery(formula));
        assertEquals(1, results.size());
        assertEquals(a, results.get(0, X));
        assertEquals(b, results.get(0, Y));

        grounding = results.get(0);
        assertEquals(a, grounding[0]);
        assertEquals(b, grounding[1]);

        db.close();

        // Tests a simple query with mixed argument types
        inserter.insert(b, a);
        inserter = datastore.getInserter(p4, datastore.getPartition("0"));
        inserter.insert(a, -0.1);

        db = datastore.getDatabase(datastore.getPartition("0"));

        formula = new QueryAtom(p4, X, Y);
        results = db.executeQuery(new DatabaseQuery(formula));
        assertEquals(1, results.size());
        assertEquals(a, results.get(0, X));
        assertEquals(new DoubleAttribute(-0.1), results.get(0, Y));

        grounding = results.get(0);
        assertEquals(a, grounding[0]);
        assertEquals(new DoubleAttribute(-0.1), grounding[1]);

        db.close();

        // Tests a simple query with multiple results
        inserter.insert(b, 4.0);
        inserter.insert(c, 4.0);
        inserter.insert(d, 4.0);
        inserter.insert(e, 4.0);
        inserter.insert(f, 4.0);

        db = datastore.getDatabase(datastore.getPartition("0"));

        results = db.executeQuery(new DatabaseQuery(formula));
        assertEquals(6, results.size());
        for (int i = 0; i < 6; i++) {
            assertTrue(results.get(i)[0] instanceof UniqueIntID);
        }

        // Tests a query with multiple Atoms
        formula = new Conjunction(new QueryAtom(p1, Y, X),
                new QueryAtom(p4, X, Z));
        results = db.executeQuery(new DatabaseQuery(formula));
        assertEquals(2, results.size());

        // Tests a query with a constant specified in the formula
        formula = new Conjunction(new QueryAtom(p4, X, new DoubleAttribute(4.0)),
                        new QueryAtom(p1, Y, X));
        results = db.executeQuery(new DatabaseQuery(formula));
        assertEquals(1, results.size());
        assertEquals(b, results.get(0)[0]);
        assertEquals(a, results.get(0)[1]);

        // Tests the same query with a different Variable ordering
        formula = new Conjunction(new QueryAtom(p1, Y, X),
                new QueryAtom(p4, X, new DoubleAttribute(4.0)));
        results = db.executeQuery(new DatabaseQuery(formula));
        assertEquals(1, results.size());
        assertEquals(a, results.get(0)[0]);
        assertEquals(b, results.get(0)[1]);
    }

    @Test
    public void testGroundingOnlyPredicates() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
        inserter.insert(a, a);
        inserter.insert(a, b);

        Variable X = new Variable("X");
        Variable Y = new Variable("Y");

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        Formula f;
        ResultList results;
        GroundAtom atom;

        // Tests equality
        f = new Conjunction(
                new QueryAtom(p1, X, Y),
                new QueryAtom(GroundingOnlyPredicate.Equal, X, Y));
        results = db.executeQuery(new DatabaseQuery(f));
        assertEquals(1, results.size());
        assertEquals(a, results.get(0, X));
        assertEquals(a, results.get(0, Y));

        atom = db.getAtom(GroundingOnlyPredicate.Equal, a, a);
        assertEquals(1.0, atom.getValue(), 0.0);

        atom = db.getAtom(GroundingOnlyPredicate.Equal, a, b);
        assertEquals(0.0, atom.getValue(), 0.0);

        // Tests inequality
        f = new Conjunction(
                new QueryAtom(p1, X, Y),
                new QueryAtom(GroundingOnlyPredicate.NotEqual, X, Y));
        results = db.executeQuery(new DatabaseQuery(f));
        assertEquals(1, results.size());
        assertEquals(a, results.get(0, X));
        assertEquals(b, results.get(0, Y));

        atom = db.getAtom(GroundingOnlyPredicate.NotEqual, a, a);
        assertEquals(0.0, atom.getValue(), 0.0);

        atom = db.getAtom(GroundingOnlyPredicate.NotEqual, a, b);
        assertEquals(1.0, atom.getValue(), 0.0);

        // Tests non-symmetry
        f = new Conjunction(
                new QueryAtom(p1, X, Y),
                new QueryAtom(GroundingOnlyPredicate.NonSymmetric, X, Y));
        results = db.executeQuery(new DatabaseQuery(f));
        assertEquals(1, results.size());
        assertEquals(a, results.get(0, X));
        assertEquals(b, results.get(0, Y));

        atom = db.getAtom(GroundingOnlyPredicate.NonSymmetric, b, a);
        assertEquals(0.0, atom.getValue(), 0.0);

        atom = db.getAtom(GroundingOnlyPredicate.NonSymmetric, a, b);
        assertEquals(1.0, atom.getValue(), 0.0);
    }

    @Test
    public void testGetAtomUnregisteredPredicate() {
        if (datastore == null) {
            return;
        }

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        dbs.add(db);

        try {
            db.getAtom(p2, new StringAttribute("a"), new StringAttribute("b"));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testLateRegisteredPredicate() {
        if (datastore == null) {
            return;
        }

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        dbs.add(db);
        datastore.registerPredicate(p1);

        try {
            db.getAtom(p2, new StringAttribute("a"), new StringAttribute("b"));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testAtomInReadAndWritePartitions() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
        inserter.insert(a, b);

        inserter = datastore.getInserter(p1, datastore.getPartition("1"));
        inserter.insert(a, b);

        Database db = datastore.getDatabase(datastore.getPartition("0"), datastore.getPartition("1"));
        dbs.add(db);

        try {
            db.getAtom(p1, a, b);
            fail("IllegalStateException not thrown as expected.");
        } catch (IllegalStateException ex) {
            // Expected
        }
    }

    @Test
    public void testAtomInTwoReadPartitions() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
        inserter.insert(a, b);

        inserter = datastore.getInserter(p1, datastore.getPartition("1"));
        inserter.insert(a, b);

        Database db = datastore.getDatabase(datastore.getPartition("2"), datastore.getPartition("0"), datastore.getPartition("1"));
        dbs.add(db);

        try {
            db.getAtom(p1, a, b);
            fail("IllegalStateException not thrown as expected.");
        } catch (IllegalStateException ex) {
            // Expected
        }
    }

    @Test
    public void testSharedReadPartition() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);
        UniqueIntID c = new UniqueIntID(2);
        UniqueIntID d = new UniqueIntID(3);

        inserter.insert(a, b);
        inserter.insert(b, c);
        inserter.insert(c, d);
        inserter.insert(a, d);

        Database db1 = datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0"));
        Database db2 = datastore.getDatabase(datastore.getPartition("2"), datastore.getPartition("0"));
        dbs.add(db1);
        dbs.add(db2);

        GroundAtom atom = db1.getAtom(p1, b, c);
        assertTrue(atom instanceof ObservedAtom);

        atom = db2.getAtom(p1, b, c);
        assertTrue(atom instanceof ObservedAtom);
    }

    @Test
    public void testSharedWritePartition() {
        if (datastore == null) {
            return;
        }

        dbs.add(datastore.getDatabase(datastore.getPartition("0")));

        try {
            dbs.add(datastore.getDatabase(datastore.getPartition("0")));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testSharedReadWritePartition1() {
        if (datastore == null) {
            return;
        }

        dbs.add(datastore.getDatabase(datastore.getPartition("0")));

        try {
            dbs.add(datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0")));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testSharedReadWritePartition2() {
        if (datastore == null) {
            return;
        }

        dbs.add(datastore.getDatabase(datastore.getPartition("0"), datastore.getPartition("1")));

        try {
            dbs.add(datastore.getDatabase(datastore.getPartition("1")));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testGetInserterUnregisteredPredicate() {
        if (datastore == null) {
            return;
        }

        try {
            datastore.getInserter(p1, datastore.getPartition("0"));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testGetInserterPartitionInUseWrite() {
        if (datastore == null) {
            return;
        }

        dbs.add(datastore.getDatabase(datastore.getPartition("0")));

        try {
            datastore.getInserter(p1, datastore.getPartition("0"));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testGetInserterPartitionInUseRead() {
        if (datastore == null) {
            return;
        }

        dbs.add(datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0")));

        try {
            datastore.getInserter(p1, datastore.getPartition("0"));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testGetAtomAfterClose() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        db.close();

        try {
            db.getAtom(p1, a, b);
            fail("IllegalStateException not thrown as expected.");
        } catch (IllegalStateException ex) {
            // Expected
        }
    }

    @Test
    public void testCommitAfterClose() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
        db.close();

        try {
            db.commit(atom);
            fail("IllegalStateException not thrown as expected.");
        } catch (IllegalStateException ex) {
            // Expected
        }
    }

    @Test
    public void testQueryAfterClose() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        Variable X = new Variable("X");
        Variable Y = new Variable("Y");

        DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1, X, Y));

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        db.close();

        try {
            db.executeQuery(query);
            fail("IllegalStateException not thrown as expected.");
        } catch (IllegalStateException ex) {
            // Expected
        }
    }

    @Test
    public void testDeletePartition() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);
        UniqueIntID c = new UniqueIntID(2);
        UniqueIntID d = new UniqueIntID(3);

        inserter.insert(a, b);
        inserter.insert(b, c);
        inserter.insert(c, d);
        inserter.insert(a, d);

        int numDeleted = datastore.deletePartition(datastore.getPartition("0"));
        assertEquals(4, numDeleted);

        Database db = datastore.getDatabase(datastore.getPartition("0"));
        dbs.add(db);
        Variable X = new Variable("X");
        Variable Y = new Variable("Y");
        DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1, X, Y));

        ResultList results = db.executeQuery(query);
        assertEquals(0, results.size());
    }

    @Test
    public void testDeletePartitionInUse() {
        if (datastore == null) {
            return;
        }

        dbs.add(datastore.getDatabase(datastore.getPartition("0")));

        try {
            datastore.deletePartition(datastore.getPartition("0"));
            fail("IllegalArgumentException not thrown as expected.");
        } catch (IllegalArgumentException ex) {
            // Expected
        }
    }

    @Test
    public void testIsClosed() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);
        datastore.registerPredicate(p2);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(p1);

        Database db = datastore.getDatabase(datastore.getPartition("0"), toClose);
        dbs.add(db);
        assertTrue(db.isClosed(p1));
        assertTrue(!db.isClosed(p2));
    }

    @Test
    /**
     * Ensure that quotes are not over/under escaped.
     */
    public void testQuotes() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p2);
        Inserter inserter = datastore.getInserter(p2, datastore.getPartition("0"));

        Set<Object> values = new HashSet<Object>(Arrays.asList(
            "1",
            "'2'",
            "'3",
            "4'",
            "'",
            "''",
            "\"2\"",
            "\"3",
            "4\"",
            "\"",
            "\"\""
        ));

        for (Object value : values) {
            inserter.insert(value, value);
        }

        Database db = datastore.getDatabase(datastore.getPartition("0"));

        // Check all the terms in all the atoms
        for (GroundAtom atom : db.getAllGroundAtoms(p2)) {
            if (!values.contains(((StringAttribute)atom.getArguments()[0]).getValue())) {
                fail("First argument of atom (" + atom + ") is an unseen value.");
            }

            if (!values.contains(((StringAttribute)atom.getArguments()[1]).getValue())) {
                fail("Second argument of atom (" + atom + ") is an unseen value.");
            }
        }

        db.close();
    }

    @Test
    public void testLongPredicateName() {
        if (datastore == null) {
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < PredicateInfo.MAX_TABLE_NAME_LENGTH; i++) {
            builder.append("A");
        }

        // Largest allowed size.
        String name = builder.toString();
        StandardPredicate predicate = StandardPredicate.get(name, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        datastore.registerPredicate(predicate);

        // One too large.
        name = name + "A";
        predicate = StandardPredicate.get(name, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        datastore.registerPredicate(predicate);

        // Much too large.
        name = name + "_" + name;
        predicate = StandardPredicate.get(name, ConstantType.UniqueIntID, ConstantType.UniqueIntID);
        datastore.registerPredicate(predicate);
    }

    @Test
    public void testBadTruthValues() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));

        float[] badValues = new float[]{
            Float.NEGATIVE_INFINITY,
            -Float.MAX_VALUE,
            -1.0f,
            -0.01f,
            1.01f,
            2.0f,
            Float.MAX_VALUE,
            Float.POSITIVE_INFINITY,
        };

        for (int i = 0; i < badValues.length; i++) {
            try {
                inserter.insertValue(badValues[i], a, b);
                fail("IllegalArgumentException not thrown as expected on index " + i + ", value: " + badValues[i]);
            } catch (IllegalArgumentException ex) {
                // Expected
            }
        }
    }

    @Test
    public void testLoadFile() {
        if (datastore == null) {
            return;
        }

        datastore.registerPredicate(p1);

        UniqueIntID a = new UniqueIntID(0);
        UniqueIntID b = new UniqueIntID(1);

        for (int i = 0; i < GOOD_DATA_FILES.length; i++) {
            String filename = GOOD_DATA_FILES[i];

            // Use a clean partition each time.
            String partition = "" + i;
            Inserter inserter = datastore.getInserter(p1, datastore.getPartition(partition));

            String path = Paths.get(RESOURCE_DIR, DATA_DIRNAME, filename).toString();
            inserter.loadDelimitedDataAutomatic(path);
        }

        for (int i = 0; i < BAD_DATA_FILES.length; i++) {
            String filename = BAD_DATA_FILES[i];

            // Use a clean partition each time.
            String partition = "" + i + GOOD_DATA_FILES.length;
            Inserter inserter = datastore.getInserter(p1, datastore.getPartition(partition));

            String path = Paths.get(RESOURCE_DIR, DATA_DIRNAME, filename).toString();
            try {
                inserter.loadDelimitedDataAutomatic(path);
                fail("Exception not thrown as expected on index " + i + ", filename: " + filename);
            } catch (RuntimeException ex) {
                // Expected
            }
        }
    }
}
