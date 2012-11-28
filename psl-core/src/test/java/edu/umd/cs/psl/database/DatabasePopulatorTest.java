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

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.DoubleAttribute;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.StringAttribute;
import edu.umd.cs.psl.model.argument.UniqueID;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.QueryAtom;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class DatabasePopulatorTest {
	
	private static StandardPredicate p1;
	private static StandardPredicate p2;
	
	private DataStore datastore;
	
	static {
		PredicateFactory predicateFactory = PredicateFactory.getFactory();
		p1 = predicateFactory.createStandardPredicate("P1", ArgumentType.UniqueID, ArgumentType.UniqueID);
		p2 = predicateFactory.createStandardPredicate("P2", ArgumentType.String, ArgumentType.Double);
	}
	
	@Before
	public void setUp() throws Exception {
		DatabaseDriver driver = new H2DatabaseDriver(H2DatabaseDriver.Type.Disk, "./psldb", false);
		datastore = new RDBMSDataStore(driver, "truth", "confidence", "partition", true);
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);
	}
	
	@After
	public void tearDown() throws Exception {
		datastore.close();
		File file;
		file = new File("./psldb.h2.db");
		file.delete();
		file = new File("./psldb.trace.db");
		file.delete();
	}
	
	@Test
	public void testSimplePopulateDatabase() {
		Database db = datastore.getDatabase(new Partition(0));
		DatabasePopulator populator = new DatabasePopulator(db);
		
		// Generate the population
		Variable x = new Variable("X");
		Variable y = new Variable("Y");
		HashSet<GroundTerm> terms = new HashSet<GroundTerm>();
		terms.add(db.getUniqueID("Bob"));
		terms.add(db.getUniqueID("Alice"));
		terms.add(db.getUniqueID("Eve"));
		terms.add(db.getUniqueID("John"));
		HashMap<Variable, Set<GroundTerm>> substitutions = new HashMap<Variable, Set<GroundTerm>>();
		substitutions.put(x, terms);
		substitutions.put(y, terms);
		
		QueryAtom qAtom = new QueryAtom(p1, x, y);
		HashSet<QueryAtom> qAtoms = new HashSet<QueryAtom>();
		qAtoms.add(qAtom);
		
		// Commit the population to the database
		populator.populate(qAtoms, substitutions);
		
		Formula f = qAtom;
		ResultList results = db.executeQuery(new DatabaseQuery(f));
		
		// Generate the expected results
		HashSet<String> expected = new HashSet<String>();
		for (GroundTerm termA : terms) {
			for (GroundTerm termB : terms) {
				expected.add(((UniqueID)termA).getInternalID() + "," + ((UniqueID)termB).getInternalID());
			}
		}
		
		// Confirm there are the correct number of results
		assertEquals(expected.size(), results.size());
		
		// Compare the expected with the actual
		for (int i = 0; i < results.size(); i ++) {
			String result = ((UniqueID)results.get(i, x)).getInternalID() + "," + ((UniqueID)results.get(i, y)).getInternalID();
			expected.remove(result);
		}
		
		assert(expected.size() == 0);
		
		db.close();
	}
	
	@Test
	public void testComplexPopulateDatabase() {
		Database db = datastore.getDatabase(new Partition(0));
		DatabasePopulator populator = new DatabasePopulator(db);
		
		// Generate the population for p1
		Variable x = new Variable("X");
		Variable y = new Variable("Y");
		HashSet<GroundTerm> p1terms = new HashSet<GroundTerm>();
		p1terms.add(db.getUniqueID("Bob"));
		p1terms.add(db.getUniqueID("Alice"));
		HashMap<Variable, Set<GroundTerm>> substitutions = new HashMap<Variable, Set<GroundTerm>>();
		substitutions.put(x, p1terms);
		substitutions.put(y, p1terms);
		
		// Generate the population for p2
		Variable a = new Variable("A");
		Variable b = new Variable("B");
		HashSet<GroundTerm> p2termsA = new HashSet<GroundTerm>();
		p2termsA.add(new StringAttribute("Mordecai"));
		p2termsA.add(new StringAttribute("Rigby"));
		HashSet<GroundTerm> p2termsB = new HashSet<GroundTerm>();
		p2termsB.add(new DoubleAttribute(1.0));
		p2termsB.add(new DoubleAttribute(2.0));
		substitutions.put(a, p2termsA);
		substitutions.put(b, p2termsB);
		
		QueryAtom qAtomP1 = new QueryAtom(p1, x, y);
		QueryAtom qAtomP2 = new QueryAtom(p2, a, b);
		HashSet<QueryAtom> qAtoms = new HashSet<QueryAtom>();
		qAtoms.add(qAtomP1);
		qAtoms.add(qAtomP2);
		
		// Commit the population to the database
		populator.populate(qAtoms, substitutions);
		
		// Query for P1
		Formula fP1 = qAtomP1;
		ResultList results = db.executeQuery(new DatabaseQuery(fP1));
		
		// Generate the expected results for P1
		HashSet<String> expected = new HashSet<String>();
		for (GroundTerm termA : p1terms) {
			for (GroundTerm termB : p1terms) {
				expected.add(((UniqueID)termA).getInternalID() + "," + ((UniqueID)termB).getInternalID());
			}
		}
		
		// Confirm there are the correct number of results for P1
		assertEquals(expected.size(), results.size());
		
		// Compare the expected with the actual for P1
		for (int i = 0; i < results.size(); i ++) {
			String result = ((UniqueID)results.get(i, x)).getInternalID() + "," + ((UniqueID)results.get(i, y)).getInternalID();
			expected.remove(result);
		}
		assert(expected.size() == 0);
		
		// Query for P2
		Formula fP2 = qAtomP2;
		results = db.executeQuery(new DatabaseQuery(fP2));
		
		// Generate the expected results for P2
		expected = new HashSet<String>();
		for (GroundTerm termA : p2termsA) {
			for (GroundTerm termB : p2termsB) {
				expected.add(((StringAttribute)termA).getValue() + "," + ((DoubleAttribute)termB).getValue());
			}
		}
		
		// Confirm there are the correct number of results for P2
		assertEquals(expected.size(), results.size());
		
		// Compare the expected with the actual for P2
		for (int i = 0; i < results.size(); i ++) {
			String result = ((StringAttribute)results.get(i, a)).getValue() + "," + ((DoubleAttribute)results.get(i, b)).getValue();
			expected.remove(result);
		}
		assert(expected.size() == 0);
		
		db.close();
	}
}
