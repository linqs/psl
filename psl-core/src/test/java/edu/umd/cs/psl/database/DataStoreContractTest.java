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
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.ObservedAtom;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.SpecialPredicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.optimizer.conic.ConicProgramSolver;

/**
 * Contract tests for classes that implement {@link ConicProgramSolver}.
 */
abstract public class DataStoreContractTest {
	
	private DataStore datastore;
	private PredicateFactory predicateFactory;
	
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

	@Before
	public void setUp() throws Exception {
		datastore = getDataStore();
		predicateFactory = PredicateFactory.getFactory();
	}

	@After
	public void tearDown() throws Exception {
		datastore.close();
		cleanUp();
	}

	@Test
	public void testInsertAndGetAtom() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
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
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
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
	}
	
	@Test
	public void testPredicateRegistration() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
		datastore.registerPredicate(p1);
		
		Set<Predicate> registeredPredicates = datastore.getRegisteredPredicates();
		assertTrue(registeredPredicates.contains(p1));
		assertTrue(registeredPredicates.contains(SpecialPredicate.Equal));
		assertTrue(registeredPredicates.contains(SpecialPredicate.NotEqual));
		assertTrue(registeredPredicates.contains(SpecialPredicate.NonSymmetric));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetAtomUnregisteredPredicate() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.String, ArgumentType.String);
		
		Database db = datastore.getDatabase(new Partition(0));
		db.getAtom(p1, new StringAttribute("a"), new StringAttribute("b"));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testLateRegisteredPredicate() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.String, ArgumentType.String);
		
		Database db = datastore.getDatabase(new Partition(0));
		datastore.registerPredicate(p1);
		db.getAtom(p1, new StringAttribute("a"), new StringAttribute("b"));
	}
	
	@Test(expected=IllegalStateException.class)
	public void testAtomInReadAndWritePartitions() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		inserter.insert(a, b);
		
		inserter = datastore.getInserter(p1, new Partition(1));
		inserter.insert(a, b);
		
		Database db = datastore.getDatabase(new Partition(0), new Partition(1));
		db.getAtom(p1, a, b);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testAtomInTwoReadPartitions() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Inserter inserter = datastore.getInserter(p1, new Partition(0));
		inserter.insert(a, b);
		
		inserter = datastore.getInserter(p1, new Partition(1));
		inserter.insert(a, b);
		
		Database db = datastore.getDatabase(new Partition(2), new Partition(0), new Partition(1));
		db.getAtom(p1, a, b);
	}
	
	@Test
	public void testSharedReadPartition() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
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
		
		GroundAtom atom = db1.getAtom(p1, b, c);
		assertTrue(atom instanceof ObservedAtom);
		
		atom = db2.getAtom(p1, b, c);
		assertTrue(atom instanceof ObservedAtom);
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedWritePartition() {
		datastore.getDatabase(new Partition(0));
		datastore.getDatabase(new Partition(0));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedReadWritePartition1() {
		datastore.getDatabase(new Partition(0));
		datastore.getDatabase(new Partition(1), new Partition(0));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testSharedReadWritePartition2() {
		datastore.getDatabase(new Partition(0), new Partition(1));
		datastore.getDatabase(new Partition(1));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterUnregisteredPredicate() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		datastore.getInserter(p1, new Partition(0));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterPartitionInUseWrite() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		datastore.getDatabase(new Partition(0));
		datastore.getInserter(p1, new Partition(0));
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testGetInserterPartitionInUseRead() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		datastore.getDatabase(new Partition(1), new Partition(0));
		datastore.getInserter(p1, new Partition(0));
	}
	
	@Test(expected=IllegalStateException.class)
	public void testGetAtomAfterClose() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
		datastore.registerPredicate(p1);
		
		UniqueID a = datastore.getUniqueID(0);
		UniqueID b = datastore.getUniqueID(1);
		
		Database db = datastore.getDatabase(new Partition(0));
		db.close();
		db.getAtom(p1, a, b);
	}
	
	@Test(expected=IllegalStateException.class)
	public void testCommitAfterClose() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
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
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
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
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
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
		Variable X = new Variable("X");
		Variable Y = new Variable("Y");
		DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1, X, Y));
		
		ResultList results = db.executeQuery(query);
		assertEquals(0, results.size());
	}
	
	@Test(expected=IllegalArgumentException.class)
	public void testDeletePartitionInUse() {
		datastore.getDatabase(new Partition(0));
		datastore.deletePartition(new Partition(0));
	}
	
	@Test
	public void testIsClosed() {
		StandardPredicate p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		StandardPredicate p2 = predicateFactory.createStandardPredicate("P2", ArgumentType.UniqueID, ArgumentType.UniqueID);
		
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);
		
		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		toClose.add(p1);
		
		Database db = datastore.getDatabase(new Partition(0), toClose);
		assertTrue(db.isClosed(p1));
		assertTrue(!db.isClosed(p2));
	}

}
