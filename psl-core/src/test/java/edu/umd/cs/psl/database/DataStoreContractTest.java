/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;

/**
 * Contract tests for classes that implement {@link ConicProgramSolver}.
 */
abstract public class DataStoreContractTest {
	
	private static StandardPredicate p1;
	private static StandardPredicate p2;
	private static StandardPredicate p3;
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
		p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		p2 = predicateFactory.createStandardPredicate("P2", ArgumentType.String, ArgumentType.String);
		p3 = predicateFactory.createStandardPredicate("P3", ArgumentType.Double, ArgumentType.Double);
		fp1 = predicateFactory.createFunctionalPredicate("FP1", new ExternalFunction() {
			
			@Override
			public double getValue(GroundTerm... args) {
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
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		
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
		db = datastore.getDatabase(new Partition(0));
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
		db = datastore.getDatabase(new Partition(1), new Partition(0));
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
		db = datastore.getDatabase(new Partition(0), toClose);
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
		db = datastore.getDatabase(new Partition(1), toClose, new Partition(0));
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
		
		Database db = datastore.getDatabase(new Partition(0));
		
		RandomVariableAtom atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		atom.setValue(.5);
		atom.setConfidenceValue(2.0);
		db.commit(atom);
		
		db.close();
		db = datastore.getDatabase(new Partition(0));
		atom = (RandomVariableAtom) db.getAtom(p1, a, b);
		assertEquals(.5, atom.getValue(), 0.0);
		assertEquals(2.0, atom.getConfidenceValue(), 0.0);
		db.close();
	}
	
	@Test
	public void testPredicateRegistration() {
		datastore.registerPredicate(p1);
		
		Set<Predicate> registeredPredicates = datastore.getRegisteredPredicates();
		assertTrue(registeredPredicates.contains(p1));
		assertTrue(registeredPredicates.contains(SpecialPredicate.Equal));
		assertTrue(registeredPredicates.contains(SpecialPredicate.NotEqual));
		assertTrue(registeredPredicates.contains(SpecialPredicate.NonSymmetric));
	}
	
	@Test
	public void testExternalFunctionalPredicate() {
		datastore.registerPredicate(p3);
		datastore.registerPredicate(fp1);
		Inserter inserter = datastore.getInserter(p3, new Partition(0));
		inserter.insert(0.5, 1.0);
		inserter.insert(0.0, 0.0);
		
		Database db = datastore.getDatabase(new Partition(0));
		
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
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetAtomUnregisteredPredicate() {
		Database db = datastore.getDatabase(new Partition(0));
		dbs.add(db);
		db.getAtom(p2, new StringAttribute("a"), new StringAttribute("b"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testLateRegisteredPredicate() {
		Database db = datastore.getDatabase(new Partition(0));
		dbs.add(db);
		datastore.registerPredicate(p1);
		db.getAtom(p2, new StringAttribute("a"), new StringAttribute("b"));
	}
	
	@Test(expected=IllegalStateException.class)
	public void testAtomInReadAndWritePartitions() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		inserter.insert(a, b);
		
		inserter = datastore.getInserter(p1, new Partition(1));
		inserter.insert(a, b);
		
		Database db = datastore.getDatabase(new Partition(0), new Partition(1));
		dbs.add(db);
		db.getAtom(p1, a, b);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testAtomInTwoReadPartitions() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		inserter.insert(a, b);
		
		inserter = datastore.getInserter(p1, new Partition(1));
		inserter.insert(a, b);
		
		Database db = datastore.getDatabase(new Partition(2), new Partition(0), new Partition(1));
		dbs.add(db);
		db.getAtom(p1, a, b);
	}
	
	@Test
	public void testSharedReadPartition() {
		datastore.registerPredicate(p1);
		
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		
		inserter.insert(a, b);
		inserter.insert(b, c);
		inserter.insert(c, d);
		inserter.insert(a, d);
		
		Database db1 = datastore.getDatabase(new Partition(1), new Partition(0));
		Database db2 = datastore.getDatabase(new Partition(2), new Partition(0));
		dbs.add(db1);
		dbs.add(db2);
		
		
		GroundAtom atom = db1.getAtom(p1, b, c);
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db2.getAtom(p1, b, c);
		assertTrue(atom instanceof ObservedAtom);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedWritePartition() {
		dbs.add(datastore.getDatabase(new Partition(0)));
		dbs.add(datastore.getDatabase(new Partition(0)));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedReadWritePartition1() {
		dbs.add(datastore.getDatabase(new Partition(0)));
		dbs.add(datastore.getDatabase(new Partition(1), new Partition(0)));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedReadWritePartition2() {
		dbs.add(datastore.getDatabase(new Partition(0), new Partition(1)));
		dbs.add(datastore.getDatabase(new Partition(1)));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterUnregisteredPredicate() {
		datastore.getInserter(p1, new Partition(0));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterPartitionInUseWrite() {
		dbs.add(datastore.getDatabase(new Partition(0)));
		datastore.getInserter(p1, new Partition(0));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterPartitionInUseRead() {
		dbs.add(datastore.getDatabase(new Partition(1), new Partition(0)));
		datastore.getInserter(p1, new Partition(0));
	}
	
	@Test(expected=IllegalStateException.class)
	public void testGetAtomAfterClose() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(new Partition(0));
		db.close();
		db.getAtom(p1, a, b);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCommitAfterClose() {
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(new Partition(0));
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
		
		Database db = datastore.getDatabase(new Partition(0));
		db.close();
		db.executeQuery(query);
	}
	
	@Test
	public void testDeletePartition() {
		datastore.registerPredicate(p1);
		
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		UniqueID c = datastore.getUniqueID(2);
		UniqueID d = datastore.getUniqueID(3);
		
		inserter.insert(a, b);
		inserter.insert(b, c);
		inserter.insert(c, d);
		inserter.insert(a, d);
		
		int numDeleted = datastore.deletePartition(new Partition(0));
		assertEquals(4, numDeleted);
		
		Database db = datastore.getDatabase(new Partition(0));
		dbs.add(db);
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1, X, Y));
		
		ResultList results = db.executeQuery(query);
		assertEquals(0, results.size());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testDeletePartitionInUse() {
		dbs.add(datastore.getDatabase(new Partition(0)));
		datastore.deletePartition(new Partition(0));
	}
	
	@Test
	public void testIsClosed() {
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);
		
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(p1);
		
		Database db = datastore.getDatabase(new Partition(0), toClose);
		dbs.add(db);
		assertTrue(db.isClosed(p1));
		assertTrue(!db.isClosed(p2));
	}

}
