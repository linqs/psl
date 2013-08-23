/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.DoubleAttribute;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.function.ExternalFunction;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * Contract tests for classes that implement {@link DataStore}.
 */
abstract public class DataStoreContractTest {
	
	private static StandardPredicate p1;
	private static StandardPredicate p2;
	private static StandardPredicate p3;
	private static StandardPredicate p4;
	private static FunctionalPredicate fp1;
	
	private DataStore datastore;
	
	private List<Database> dbs;
	
	/**
	 * @return the DataStore to be tested, should always be backed by the same
	 *             persistence mechanism
	 */
	abstract public DataStore getDataStore();
	
	/**
	 * Deletes any files and releases any resources used by the tested DataStore
	 * and its persistence mechanism
	 */
	abstract public void cleanUp();
	
	static {
		PredicateFactory predicateFactory = PredicateFactory.getFactory();
		p1 = predicateFactory.createStandardPredicate("DataStoreContractTest_P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		p2 = predicateFactory.createStandardPredicate("DataStoreContractTest_P2", ArgumentType.String, ArgumentType.String);
		p3 = predicateFactory.createStandardPredicate("DataStoreContractTest_P3", ArgumentType.Double, ArgumentType.Double);
		p4 = predicateFactory.createStandardPredicate("DataStoreContractTest_P4", ArgumentType.UniqueID, ArgumentType.Double);
		fp1 = predicateFactory.createFunctionalPredicate("DataStoreContractTest_FP1", new ExternalFunction() {
			
			@Override
			public double getValue(ReadOnlyDatabase db, GroundTerm... args) {
				double a = ((DoubleAttribute) args[0]).getValue();
				double b = ((DoubleAttribute) args[1]).getValue();
				
				return Math.max(0.0, Math.min(1.0, (a + b) / 2));
			}
			
			@Override
			public int getArity() {
				return 2;
			}
			
			@Override
			public ArgumentType[] getArgumentTypes() {
				return new ArgumentType[] {ArgumentType.Double, ArgumentType.Double};
			}
		});
	}

	@Before
	public void setUp() throws Exception {
		datastore = getDataStore();
		dbs = new LinkedList<Database>();
	}

	@After
	public void tearDown() throws Exception {
		for (Database db : dbs)
			db.close();
		datastore.close();
		cleanUp();
	}

	@Test
	public void testInsertAndGetAtom() {
		datastore.registerPredicate(p1);
		Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		
		inserter.insert(a, b);
		inserter.insertValue(0.5, b, c);
		inserter.insertValueConfidence(0.25, 10, c, d);
		
		Database db;
		GroundAtom atom;
		
		/* Tests open predicate with atoms in write partition */
		db = datastore.getDatabase(datastore.getPartition("0"));
		atom = db.getAtom(p1, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof RandomVariableAtom);
		
		atom = db.getAtom(p1, b, c);
		assertEquals(0.5, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof RandomVariableAtom);
		
		atom = db.getAtom(p1, c, d);
		assertEquals(0.25, atom.getValue(), 0.0);
		assertEquals(10, atom.getConfidenceValue(), 0.0);
		assertTrue(atom instanceof RandomVariableAtom);
		
		atom = db.getAtom(p1, d, a);
		assertEquals(0.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof RandomVariableAtom);
		
		db.close();
		
		/* Tests open predicate with atoms in read partition */
		db = datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0"));
		atom = db.getAtom(p1, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, b, c);
		assertEquals(0.5, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, c, d);
		assertEquals(0.25, atom.getValue(), 0.0);
		assertEquals(10, atom.getConfidenceValue(), 0.0);
		assertTrue(atom instanceof ObservedAtom);
		
		db.close();
		
		/* Tests closed predicate with atoms in write partition */
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(p1);
		db = datastore.getDatabase(datastore.getPartition("0"), toClose);
		atom = db.getAtom(p1, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, b, c);
		assertEquals(0.5, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, c, d);
		assertEquals(0.25, atom.getValue(), 0.0);
		assertEquals(10, atom.getConfidenceValue(), 0.0);
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, d, a);
		assertEquals(0.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		db.close();
		
		/* Tests closed predicate with atoms in read partition */
		db = datastore.getDatabase(datastore.getPartition("1"), toClose, datastore.getPartition("0"));
		atom = db.getAtom(p1, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, b, c);
		assertEquals(0.5, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db.getAtom(p1, c, d);
		assertEquals(0.25, atom.getValue(), 0.0);
		assertEquals(10, atom.getConfidenceValue(), 0.0);
		assertTrue(atom instanceof ObservedAtom);
		
		db.close();
	}
	
	@Test
	public void testCommit() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		
		RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		atom.setValue(.5);
		atom.setConfidenceValue(2.0);
		db.commit(atom);
		db.close();
		
		db = datastore.getDatabase(datastore.getPartition("0"));
		atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		assertEquals(.5, atom.getValue(), 0.0);
		assertEquals(2.0, atom.getConfidenceValue(), 0.0);
		atom.setValue(1.0);
		db.commit(atom);
		db.close();
		
		db = datastore.getDatabase(datastore.getPartition("0"));
		atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		db.close();
	}
	
	@Test
	public void testDoubleCommit() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		atom.setValue(0.25);
		atom.commitToDB();
		atom.setValue(0.5);
		atom.commitToDB();
		db.close();
		
		db = datastore.getDatabase(datastore.getPartition("0"));
		atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		assertEquals(0.5, atom.getValue(), 0.0);
		atom.setValue(0.75);
		atom.commitToDB();
		atom.setValue(1.0);
		atom.commitToDB();
		db.close();
		
		db = datastore.getDatabase(datastore.getPartition("0"));
		atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		db.close();
		
	}
	
	@Test
	public void testInsertTwoAtoms() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		RandomVariableAtom atom1 = (RandomVariableAtom) db.getAtom(p1, a, b);
		RandomVariableAtom atom2 = (RandomVariableAtom) db.getAtom(p1, c, d);
		atom1.setValue(0.25);
		atom2.setValue(0.75);
		atom1.commitToDB();
		atom2.commitToDB();
		DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1,  new Variable("X"), new Variable("Y")));
		ResultList results = db.executeQuery(query);
		assertEquals(2, results.size());
		
		db.close();
	}
	
	@Test
	public void testStringEscaping() {
		datastore.registerPredicate(p2);
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		DatabaseQuery query = new DatabaseQuery(new QueryAtom(p2, new StringAttribute("a"), new StringAttribute("jk'a")));
		db.executeQuery(query);
	}
	
	@Test
	public void testPredicateRegistration() {
		datastore.registerPredicate(p1);
		
		Set<StandardPredicate> registeredPredicates = datastore.getRegisteredPredicates();
		assertTrue(registeredPredicates.contains(p1));
	}
	
	@Test
	public void testPredicateSerialization() {
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);
		
		datastore.close();
		datastore = getDataStore();
		
		Set<StandardPredicate> registeredPredicates = datastore.getRegisteredPredicates();
		assertTrue(registeredPredicates.contains(p1));
		assertTrue(registeredPredicates.contains(p2));
	}
	
	@Test
	public void testExternalFunctionalPredicate() {
		datastore.registerPredicate(p3);
		Inserter inserter = datastore.getInserter(p3, datastore.getPartition("0"));
		inserter.insert(0.5, 1.0);
		inserter.insert(0.0, 0.0);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		Formula f = new Conjunction(new QueryAtom(p3, X, Y), new QueryAtom(fp1, X, Y));
		ResultList results = db.executeQuery(new DatabaseQuery(f));
		assertEquals(1, results.size());
		assertEquals(0.5, ((DoubleAttribute) results.get(0, X)).getValue(), 0.0);
		assertEquals(1.0, ((DoubleAttribute) results.get(0, Y)).getValue(), 0.0);
		
		GroundAtom atom = db.getAtom(fp1, new DoubleAttribute(0.5), new DoubleAttribute(1.0));
		assertEquals(0.75, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		
		atom = db.getAtom(fp1, new DoubleAttribute(0.0), new DoubleAttribute(0.0));
		assertEquals(0.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
	}
	
	@Test
	public void testExecuteQuery() {
		Inserter inserter;
		Database db;
		DatabaseQuery query;
		Formula formula;
		ResultList results;
		GroundTerm[] grounding;
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		UniqueID e = datastore.getUniqueID(4);
		UniqueID f = datastore.getUniqueID(5);
		
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		Variable Z = new Variable("Z");
		
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p4);
		
		/*
		 * Tests a simple query
		 */
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
		
		/*
		 * Tests a simple query with mixed argument types
		 */
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
		
		/*
		 * Tests a simple query with multiple results
		 */
		inserter.insert(b, 4.0);
		inserter.insert(c, 4.0);
		inserter.insert(d, 4.0);
		inserter.insert(e, 4.0);
		inserter.insert(f, 4.0);
		
		db = datastore.getDatabase(datastore.getPartition("0"));
		
		results = db.executeQuery(new DatabaseQuery(formula));
		assertEquals(6, results.size());
		for (int i = 0; i < 6; i++) {
			assertTrue(results.get(i)[0] instanceof UniqueID);
		}
		
		/*
		 * Tests a query with multiple Atoms
		 */
		formula = new Conjunction(new QueryAtom(p1, Y, X),
				new QueryAtom(p4, X, Z));
		results = db.executeQuery(new DatabaseQuery(formula));
		assertEquals(2, results.size());
		
		/*
		 * Tests a query with a constant specified in the formula 
		 */
		formula = new Conjunction(new QueryAtom(p4, X, new DoubleAttribute(4.0)),
						new QueryAtom(p1, Y, X));
		results = db.executeQuery(new DatabaseQuery(formula));
		assertEquals(1, results.size());
		assertEquals(b, results.get(0)[0]);
		assertEquals(a, results.get(0)[1]);
		
		/*
		 * Tests the same query with a different Variable ordering
		 */
		formula = new Conjunction(new QueryAtom(p1, Y, X),
				new QueryAtom(p4, X, new DoubleAttribute(4.0)));
		results = db.executeQuery(new DatabaseQuery(formula));
		assertEquals(1, results.size());
		assertEquals(a, results.get(0)[0]);
		assertEquals(b, results.get(0)[1]);
		
		/*
		 * Tests the same query using the partial grounding to specify constants
		 */
		formula = new Conjunction(new QueryAtom(p1, Y, X),
				new QueryAtom(p4, X, Z));
		query = new DatabaseQuery(formula);
		query.getPartialGrounding().assign(Z, new DoubleAttribute(4.0));
		results = db.executeQuery(query);
		assertEquals(1, results.size());
		assertEquals(a, results.get(0)[0]);
		assertEquals(b, results.get(0)[1]);
		
		/*
		 * Tests a multi-atom query with a projection set
		 */
		formula = new Conjunction(new QueryAtom(p1, Y, X),
				new QueryAtom(p4, X, Z));
		query = new DatabaseQuery(formula);
		query.getProjectionSubset().add(Y);
		query.getProjectionSubset().add(Z);
		results = db.executeQuery(query);
		assertEquals(2, results.size());
		grounding = results.get(0);
		if (grounding[0].equals(a)) {
			assertEquals(new DoubleAttribute(4.0), grounding[1]);
			grounding = results.get(1);
			assertEquals(b, grounding[0]);
			assertEquals(new DoubleAttribute(-0.1), grounding[1]);
		}
		else if (grounding[0].equals(b)){
			assertEquals(new DoubleAttribute(-0.1), grounding[1]);
			grounding = results.get(1);
			assertEquals(a, grounding[0]);
			assertEquals(new DoubleAttribute(4.0), grounding[1]);
		}
		else
			assertTrue(false);
		
		/*
		 * Tests a query with a projection set that collapses multiple
		 * groundings to one
		 */
		formula = new QueryAtom(p4, X, Z);
		query = new DatabaseQuery(formula);
		query.getProjectionSubset().add(Z);
		results = db.executeQuery(query);
		assertEquals(2, results.size());
		grounding = results.get(0);
		if (grounding[0].equals(new DoubleAttribute(4.0))) {
			grounding = results.get(1);
			assertEquals(new DoubleAttribute(-0.1), grounding[0]);
		}
		else if (grounding[0].equals(new DoubleAttribute(-0.1))) {
			grounding = results.get(1);
			assertEquals(new DoubleAttribute(4.0), grounding[0]);
		}
		else
			assertTrue(false);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testExecuteQueryIllegalProjectionVariable() {
		Inserter inserter;
		Database db;
		DatabaseQuery query;
		Formula formula;
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		UniqueID e = datastore.getUniqueID(4);
		UniqueID f = datastore.getUniqueID(5);
		
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		Variable Z = new Variable("Z");
		
		datastore.registerPredicate(p1);
		
		inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		inserter.insert(a, b);
		inserter.insert(c, d);
		inserter.insert(e, f);
		
		db = datastore.getDatabase(datastore.getPartition("0"));
		
		formula = new QueryAtom(p1, X, Y);
		query = new DatabaseQuery(formula);
		query.getProjectionSubset().add(X);
		query.getProjectionSubset().add(Z);
		db.executeQuery(query);
	}
	
	@Test
	public void testSpecialPredicates() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);

		Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		inserter.insert(a, a);
		inserter.insert(a, b);
		
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		Formula f;
		ResultList results;
		GroundAtom atom;
		
		/*
		 * Tests equality
		 */
		f = new Conjunction(
				new QueryAtom(p1, X, Y),
				new QueryAtom(SpecialPredicate.Equal, X, Y));
		results = db.executeQuery(new DatabaseQuery(f));
		assertEquals(1, results.size());
		assertEquals(a, results.get(0, X));
		assertEquals(a, results.get(0, Y));
		
		atom = db.getAtom(SpecialPredicate.Equal, a, a);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		
		atom = db.getAtom(SpecialPredicate.Equal, a, b);
		assertEquals(0.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		
		/*
		 * Tests inequality
		 */
		f = new Conjunction(
				new QueryAtom(p1, X, Y),
				new QueryAtom(SpecialPredicate.NotEqual, X, Y));
		results = db.executeQuery(new DatabaseQuery(f));
		assertEquals(1, results.size());
		assertEquals(a, results.get(0, X));
		assertEquals(b, results.get(0, Y));
		
		atom = db.getAtom(SpecialPredicate.NotEqual, a, a);
		assertEquals(0.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		
		atom = db.getAtom(SpecialPredicate.NotEqual, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		
		/*
		 * Tests non-symmetry
		 */
		f = new Conjunction(
				new QueryAtom(p1, X, Y),
				new QueryAtom(SpecialPredicate.NonSymmetric, X, Y));
		results = db.executeQuery(new DatabaseQuery(f));
		assertEquals(1, results.size());
		assertEquals(a, results.get(0, X));
		assertEquals(b, results.get(0, Y));
		
		atom = db.getAtom(SpecialPredicate.NonSymmetric, b, a);
		assertEquals(0.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
		
		atom = db.getAtom(SpecialPredicate.NonSymmetric, a, b);
		assertEquals(1.0, atom.getValue(), 0.0);
		assertTrue(Double.isNaN(atom.getConfidenceValue()));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetAtomUnregisteredPredicate() {
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		dbs.add(db);
		db.getAtom(p2, new StringAttribute("a"), new StringAttribute("b"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testLateRegisteredPredicate() {
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		dbs.add(db);
		datastore.registerPredicate(p1);
		db.getAtom(p2, new StringAttribute("a"), new StringAttribute("b"));
	}
	
	@Test(expected=IllegalStateException.class)
	public void testAtomInReadAndWritePartitions() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		inserter.insert(a, b);
		
		inserter = datastore.getInserter(p1, datastore.getPartition("1"));
		inserter.insert(a, b);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"), datastore.getPartition("1"));
		dbs.add(db);
		db.getAtom(p1, a, b);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testAtomInTwoReadPartitions() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		inserter.insert(a, b);
		
		inserter = datastore.getInserter(p1, datastore.getPartition("1"));
		inserter.insert(a, b);
		
		Database db = datastore.getDatabase(datastore.getPartition("2"), datastore.getPartition("0"), datastore.getPartition("1"));
		dbs.add(db);
		db.getAtom(p1, a, b);
	}
	
	@Test
	public void testSharedReadPartition() {
		datastore.registerPredicate(p1);
		
		Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		
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
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedWritePartition() {
		dbs.add(datastore.getDatabase(datastore.getPartition("0")));
		dbs.add(datastore.getDatabase(datastore.getPartition("0")));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedReadWritePartition1() {
		dbs.add(datastore.getDatabase(datastore.getPartition("0")));
		dbs.add(datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0")));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedReadWritePartition2() {
		dbs.add(datastore.getDatabase(datastore.getPartition("0"), datastore.getPartition("1")));
		dbs.add(datastore.getDatabase(datastore.getPartition("1")));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterUnregisteredPredicate() {
		datastore.getInserter(p1, datastore.getPartition("0"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterPartitionInUseWrite() {
		dbs.add(datastore.getDatabase(datastore.getPartition("0")));
		datastore.getInserter(p1, datastore.getPartition("0"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterPartitionInUseRead() {
		dbs.add(datastore.getDatabase(datastore.getPartition("1"), datastore.getPartition("0")));
		datastore.getInserter(p1, datastore.getPartition("0"));
	}
	
	@Test
	public void testGetInserterForDeserializedPredicate() {
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);
		
		datastore.close();
		datastore = getDataStore();
		datastore.getInserter(p1, datastore.getPartition("0"));
	}
	
	@Test(expected=IllegalStateException.class)
	public void testGetAtomAfterClose() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		db.close();
		db.getAtom(p1, a, b);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCommitAfterClose() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		db.close();
		db.commit(atom);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testQueryAfterClose() {
		datastore.registerPredicate(p1);
		
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		
		DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1, X, Y));
		
		Database db = datastore.getDatabase(datastore.getPartition("0"));
		db.close();
		db.executeQuery(query);
	}
	
	@Test
	public void testDeletePartition() {
		datastore.registerPredicate(p1);
		
		Inserter inserter = datastore.getInserter(p1, datastore.getPartition("0"));
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		
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
	
	@Test(expected=IllegalArgumentException.class)
	public void testDeletePartitionInUse() {
		dbs.add(datastore.getDatabase(datastore.getPartition("0")));
		datastore.deletePartition(datastore.getPartition("0"));
	}
	
	@Test
	public void testIsClosed() {
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);
		
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(p1);
		
		Database db = datastore.getDatabase(datastore.getPartition("0"), toClose);
		dbs.add(db);
		assertTrue(db.isClosed(p1));
		assertTrue(!db.isClosed(p2));
	}

}
