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
package org.linqs.psl.database;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.ReadableDatabase;
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
import org.linqs.psl.model.predicate.SpecialPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class ReadableTest {

	/**
	 * Helper function for testing the ReadableDatabase interface functions.
	 */
	private void testHelper(DatabaseFunction function) {
		TestModelFactory.ModelInformation info = TestModelFactory.getModel();
		Predicate functionPredicate = ExternalFunctionalPredicate.get("TestFun", function);

		// Add a rule using the new function.
		// 10: Person(A) & Person(B) & UnaryFunction(A) & (A != B) -> Friends(A, B) ^2
		Formula ruleFormula = new Implication(
			new Conjunction(
				new QueryAtom(info.predicates.get("Person"), new Variable("A")),
				new QueryAtom(info.predicates.get("Person"), new Variable("B")),
				new QueryAtom(functionPredicate, new Variable("A")),
				new QueryAtom(SpecialPredicate.NotEqual, new Variable("A"), new Variable("B"))
			),
			new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
		);

		Rule rule = new WeightedLogicalRule(ruleFormula, 11.0, true);
		info.model.addRule(rule);

		Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
		Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);

		MPEInference mpe = null;

		try {
			mpe = new MPEInference(info.model, inferDB, info.config);
		} catch (Exception ex) {
			System.out.println(ex);
			ex.printStackTrace();
			fail("Exception thrown during MPE constructor.");
		}

		mpe.mpeInference();
		mpe.close();
		inferDB.close();
	}

	@Test
	/**
	 * Ensure that getAtom() works with the ReadableDatabase interface.
	 */
	public void testGetAtom() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
				GroundAtom atom = db.getAtom(p1, arg);
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that hasAtom() works with the ReadableDatabase interface.
	 */
	public void testHasAtom() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg){
				StandardPredicate p1;
				p1 = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
				db.hasAtom(p1, arg);
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that countAllGroundAtoms() works with the ReadableDatabase interface.
	 */
	public void testCountAllGroundAtoms() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
				int count = db.countAllGroundAtoms(p1);
				assertTrue("Got " + count + ", expected 5", 5 == count);
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that countAllGroundRandomVariableAtoms() works with the ReadableDatabase interface.
	 */
	public void testCountAllGroundRandomVariableAtoms() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Friends", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
				int count = db.countAllGroundRandomVariableAtoms(p1);
				assertTrue("Got " + count + ", expected 20", 20 == count);
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that getAllGroundAtoms() works with the ReadableDatabase interface.
	 */
	public void testGetAllGroundAtoms() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
				List<GroundAtom> l1 = db.getAllGroundAtoms(p1);
				assertTrue("Got " + l1.size() + ", expected 5", 5 == l1.size());
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that getAllGroundRandomVariableAtoms() works with the ReadableDatabase interface.
	 */
	public void testGetAllGroundRandomVariableAtoms() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Friends", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
				List<RandomVariableAtom> l1 = db.getAllGroundRandomVariableAtoms(p1);
				assertTrue("Got " + l1.size() + ", expected 20", 20 == l1.size());
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that getAllGroundObservedAtoms() works with the ReadableDatabase interface.
	 */
	public void testGetAllGroundObservedAtoms() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Nice", ConstantType.UniqueStringID);
				List<ObservedAtom> l1 = db.getAllGroundObservedAtoms(p1);
				assertTrue("Got " + l1.size() + ", expected 5", 5 == l1.size());
			}
		};
		testHelper(function);
	}


	@Test
	/**
	 * Ensure that executeQuery() works with the ReadableDatabase interface.
	 */
	public void testExecuteQuery() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1 = StandardPredicate.get("Friends");
				DatabaseQuery query = new DatabaseQuery(new QueryAtom(p1, arg, new Variable("A")));
				ResultList results = db.executeQuery(query);
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that executeGroundingQuery() works with the ReadableDatabase interface.
	 */
	public void testExecuteGroundingQuery() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1 = StandardPredicate.get("Friends");
				ResultList results = db.executeGroundingQuery(new QueryAtom(p1, arg, new Variable("A")));
			}
		};
		testHelper(function);
	}

	@Test
	/**
	 * Ensure that isClosed() works with the ReadableDatabase interface.
	 */
	public void testIsClosed() {
		DatabaseFunction function = new DatabaseFunction() {
			@Override
			public void doWork(ReadableDatabase db, Constant arg) {
				StandardPredicate p1;
				p1 = StandardPredicate.get("Friends", ConstantType.UniqueStringID, ConstantType.UniqueStringID);
				db.isClosed(p1);
			}
		};
		testHelper(function);
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
			return 1;
		}

		public abstract void doWork(ReadableDatabase db, Constant arg);
	}
}
