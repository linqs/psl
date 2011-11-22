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
package edu.umd.cs.psl.evaluation.statistics;

import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.DatabaseEventObserver;
import edu.umd.cs.psl.database.PredicatePosition;
import edu.umd.cs.psl.database.AtomRecord;
import edu.umd.cs.psl.database.AtomRecord.Status;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.database.ResultListValues;
import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.database.RDBMS.RDBMSResultList;
import edu.umd.cs.psl.database.RDBMS.RDBMSUniqueIntID;
import edu.umd.cs.psl.evaluation.statistics.ResultComparison.BinaryClass;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomStore;
import edu.umd.cs.psl.model.atom.memory.SimpleMemoryAtom;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;

public class SimpleResultComparatorTest {

	private Predicate predicate;
	private DatabaseAtomStoreQuery infQuery;
	private DatabaseAtomStoreQuery truthQuery;
	private ResultComparator comparator;
	
	/*
	 * Fake database with limited functionality
	 */
	private class FakeDatabase implements Database {
		
		private RDBMSResultList resultList;
		private HashMap<GroundTerm[],AtomRecord> data;
		
		public FakeDatabase(Predicate p, int[][] terms, double[] atoms) {
			resultList = new RDBMSResultList(2);
			data = new HashMap<GroundTerm[],AtomRecord>();
			for (int i = 0; i < atoms.length; i++) {
				GroundTerm[] t = new GroundTerm[2];
				t[0] = Entity.getEntity(new RDBMSUniqueIntID(terms[i][0]));
				t[1] = Entity.getEntity(new RDBMSUniqueIntID(terms[i][1]));
				resultList.addResult(t);
				AtomRecord a = new AtomRecord(new double[]{atoms[i]}, new double[]{1.0}, Status.FACT);
				data.put(t, a);
			}
		}
		
		@Override
		public AtomRecord getAtom(Predicate p, GroundTerm[] arguments) {
			if (data.containsKey(arguments))
				return data.get(arguments);
			return new AtomRecord(p.getDefaultValues(), new double[]{1.0}, Status.FACT);
		}

		@Override
		public ResultListValues getFacts(Predicate p, Term[] arguments) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<PredicatePosition, ResultListValues> getAllFactsWith(GroundTerm e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void persist(Atom atom) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResultList query(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo) {
			return resultList;
		}

		@Override
		public ResultList query(Formula f, VariableAssignment partialGrounding) {
			return resultList;
		}

		@Override
		public ResultList query(Formula f, List<Variable> projectTo) {
			return resultList;
		}

		@Override
		public ResultList query(Formula f) {
			return resultList;
		}

		@Override
		public void registerDatabaseEventObserver(DatabaseEventObserver atomEvents) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void deregisterDatabaseEventObserver(DatabaseEventObserver atomEvents) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Entity getEntity(Object entity, ArgumentType type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Set<Entity> getEntities(ArgumentType type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public int getNumEntities(ArgumentType type) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean isClosed(Predicate p) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void close() {
			throw new UnsupportedOperationException();
		}
	}
	
	private static double compare(double v1, double v2, double tol) {
		double diff = Math.abs(v1 - v2);
		if (diff <= tol)
			return 0.0;
		return diff;
	}
	
	@Before
	public void setUp() throws Exception {
		// create a predicate
		PredicateFactory factory = new PredicateFactory();
		predicate = factory.createStandardPredicate(
				"same"
				, PredicateTypes.SoftTruth
				, new ArgumentType[]{ArgumentTypes.Entity, ArgumentTypes.Entity}
				, new double[]{0.0}
			);
		
		// create some canned ground inference atoms
		int[][] terms = new int[5][];
		terms[0] = new int[]{ 1, 2 };
		terms[1] = new int[]{ 2, 1 };
		terms[2] = new int[]{ 3, 4 };
		terms[3] = new int[]{ 5, 6 };			
		terms[4] = new int[]{ 6, 5 };
		double[] atoms = new double[] { 0.8, 0.8, 0.8, 0.8, 0.8 };
		Database db = new FakeDatabase(predicate, terms, atoms);
		AtomStore store = new MemoryAtomStore(db);
		infQuery = new DatabaseAtomStoreQuery(store);
		
		// create some ground truth atoms
		terms = new int[4][];
		terms[0] = new int[]{ 1, 2 };
		terms[1] = new int[]{ 2, 1 };
		terms[2] = new int[]{ 3, 4 };
		terms[3] = new int[]{ 4, 3 };
		atoms = new double[] { 1.0, 1.0, 1.0, 1.0 };
		db = new FakeDatabase(predicate, terms, atoms);
		store = new MemoryAtomStore(db);
		truthQuery = new DatabaseAtomStoreQuery(store);
		
		// create result comparison
		comparator = new SimpleResultComparator(infQuery);
		comparator.setBaseline(truthQuery);
	}

	@Test
	public void testPrecision() {
		for (double tol = 0.1; tol <= 1.0; tol += 0.1) {
			comparator.setTolerance(tol);
			ResultComparison comparison = comparator.compare(predicate, 6*5);
			double prec = comparison.getPrecision(BinaryClass.POSITIVE);
			if (tol <= 0.8)
				assertTrue(compare(prec, 0.6, 1e-10) == 0.0);
			else
				assertTrue(compare(prec, 0.4, 1e-10) == 0.0);
		}
	}
	
	@Test
	public void testRecall() {
		for (double tol = 0.1; tol <= 1.0; tol += 0.1) {
			comparator.setTolerance(tol);
			ResultComparison comparison = comparator.compare(predicate, 6*5);
			double recall = comparison.getRecall(BinaryClass.POSITIVE);
			if (tol <= 0.8)
				assertTrue(compare(recall, 0.75, 1e-10) == 0.0);
			else
				assertTrue(compare(recall, 2.0/3.0, 1e-10) == 0.0);
		}		
	}
	
	@Test
	public void testF1() {
		for (double tol = 0.1; tol <= 1.0; tol += 0.1) {
			comparator.setTolerance(tol);
			ResultComparison comparison = comparator.compare(predicate, 6*5);
			double f1 = comparison.getF1(BinaryClass.POSITIVE);
			if (tol <= 0.8)
				assertTrue(compare(f1, 2.0/3.0, 1e-10) == 0.0);
			else
				assertTrue(compare(f1, 0.5, 1e-10) == 0.0);
		}		
	}
	
	@Test
	public void testAccuracy() {
		for (double tol = 0.1; tol <= 1.0; tol += 0.1) {
			comparator.setTolerance(tol);
			ResultComparison comparison = comparator.compare(predicate, 6*5);
			double acc = comparison.getAccuracy();
			if (tol <= 0.8)
				assertTrue(compare(acc, 0.9, 1e-10) == 0.0);
			else
				assertTrue(compare(acc, 26.0/30.0, 1e-10) == 0.0);
		}		
	}
}
