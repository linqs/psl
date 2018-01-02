/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import static org.junit.Assert.assertEquals;

import org.linqs.psl.config.EmptyBundle;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.DatabasePopulator;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.PredicateFactory;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.Variable;

import java.io.File;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class DatabasePopulatorTest {
	private static StandardPredicate p1;
	private static StandardPredicate p2;

	static {
		PredicateFactory predicateFactory = PredicateFactory.getFactory();
		p1 = predicateFactory.createStandardPredicate("DatabasePopulatorTest_P1", ConstantType.UniqueIntID, ConstantType.UniqueIntID);
		p2 = predicateFactory.createStandardPredicate("DatabasePopulatorTest_P2", ConstantType.String, ConstantType.Double);
	}

	@Test
	public void testH2SimplePopulateDatabase() {
		DatabaseDriver driver = DatabaseTestUtil.getH2Driver();
		simplePopulateDatabase(driver);
		DatabaseTestUtil.cleanH2Driver();
	}

	@Test
	public void testPostgresSimplePopulateDatabase() {
		DatabaseDriver driver = DatabaseTestUtil.getPostgresDriver();
		if (driver == null) {
			return;
		}

		simplePopulateDatabase(driver);
		DatabaseTestUtil.cleanPostgresDriver();
	}

	@Test
	public void testH2ComplexPopulateDatabase() {
		DatabaseDriver driver = DatabaseTestUtil.getH2Driver();
		complexPopulateDatabase(driver);
		DatabaseTestUtil.cleanH2Driver();
	}

	@Test
	public void testPostgresComplexPopulateDatabase() {
		DatabaseDriver driver = DatabaseTestUtil.getPostgresDriver();
		if (driver == null) {
			return;
		}

		complexPopulateDatabase(driver);
		DatabaseTestUtil.cleanPostgresDriver();
	}

	private void simplePopulateDatabase(DatabaseDriver driver) {
		DataStore datastore = new RDBMSDataStore(driver, new EmptyBundle());
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);

		Database db = datastore.getDatabase(datastore.getPartition("0"));
		DatabasePopulator populator = new DatabasePopulator(db);

		// Generate the population
		Variable x = new Variable("X");
		Variable y = new Variable("Y");
		HashSet<Constant> terms = new HashSet<Constant>();
		terms.add(new UniqueIntID(1));
		terms.add(new UniqueIntID(2));
		terms.add(new UniqueIntID(3));
		terms.add(new UniqueIntID(4));
		HashMap<Variable, Set<Constant>> substitutions = new HashMap<Variable, Set<Constant>>();
		substitutions.put(x, terms);
		substitutions.put(y, terms);

		QueryAtom qAtom = new QueryAtom(p1, x, y);

		// Commit the population to the database
		populator.populate(qAtom, substitutions);

		Formula f = qAtom;
		ResultList results = db.executeQuery(new DatabaseQuery(f));

		// Generate the expected results
		HashSet<String> expected = new HashSet<String>();
		for (Constant termA : terms) {
			for (Constant termB : terms) {
				expected.add("" + ((UniqueIntID)termA).getID() + "," + ((UniqueIntID)termB).getID());
			}
		}

		// Confirm there are the correct number of results
		assertEquals(expected.size(), results.size());

		// Compare the expected with the actual
		for (int i = 0; i < results.size(); i ++) {
			String result = "" + ((UniqueIntID)results.get(i, x)).getID() + "," + ((UniqueIntID)results.get(i, y)).getID();
			expected.remove(result);
		}

		assert(expected.size() == 0);

		db.close();
		datastore.close();
	}

	private void complexPopulateDatabase(DatabaseDriver driver) {
		DataStore datastore = new RDBMSDataStore(driver, new EmptyBundle());
		datastore.registerPredicate(p1);
		datastore.registerPredicate(p2);

		Database db = datastore.getDatabase(datastore.getPartition("0"));
		DatabasePopulator populator = new DatabasePopulator(db);

		// Generate the population for p1
		Variable x = new Variable("X");
		Variable y = new Variable("Y");
		HashSet<Constant> p1terms = new HashSet<Constant>();
		p1terms.add(new UniqueIntID(1));
		p1terms.add(new UniqueIntID(2));
		HashMap<Variable, Set<Constant>> substitutions = new HashMap<Variable, Set<Constant>>();
		substitutions.put(x, p1terms);
		substitutions.put(y, p1terms);

		// Generate the population for p2
		Variable a = new Variable("A");
		Variable b = new Variable("B");
		HashSet<Constant> p2termsA = new HashSet<Constant>();
		p2termsA.add(new StringAttribute("Mordecai"));
		p2termsA.add(new StringAttribute("Rigby"));
		HashSet<Constant> p2termsB = new HashSet<Constant>();
		p2termsB.add(new DoubleAttribute(1.0));
		p2termsB.add(new DoubleAttribute(2.0));
		substitutions.put(a, p2termsA);
		substitutions.put(b, p2termsB);

		QueryAtom qAtomP1 = new QueryAtom(p1, x, y);
		QueryAtom qAtomP2 = new QueryAtom(p2, a, b);

		// Commit the population to the database
		populator.populate(qAtomP1, substitutions);
		populator.populate(qAtomP2, substitutions);

		// Query for P1
		Formula fP1 = qAtomP1;
		ResultList results = db.executeQuery(new DatabaseQuery(fP1));

		// Generate the expected results for P1
		HashSet<String> expected = new HashSet<String>();
		for (Constant termA : p1terms) {
			for (Constant termB : p1terms) {
				expected.add("" + ((UniqueIntID)termA).getID() + "," + ((UniqueIntID)termB).getID());
			}
		}

		// Confirm there are the correct number of results for P1
		assertEquals(expected.size(), results.size());

		// Compare the expected with the actual for P1
		for (int i = 0; i < results.size(); i ++) {
			String result = "" + ((UniqueIntID)results.get(i, x)).getID() + "," + ((UniqueIntID)results.get(i, y)).getID();
			expected.remove(result);
		}
		assert(expected.size() == 0);

		// Query for P2
		Formula fP2 = qAtomP2;
		results = db.executeQuery(new DatabaseQuery(fP2));

		// Generate the expected results for P2
		expected = new HashSet<String>();
		for (Constant termA : p2termsA) {
			for (Constant termB : p2termsB) {
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
		datastore.close();
	}
}
