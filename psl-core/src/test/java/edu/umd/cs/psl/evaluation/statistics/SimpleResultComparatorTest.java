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

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.DatabaseEventObserver;
import edu.umd.cs.psl.database.PredicatePosition;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.database.ResultListValues;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;

public class SimpleResultComparatorTest {

	/*
	 * Fake database with limited functionality
	 */
	private class FakeDatabase implements Database {

		@Override
		public Atom getAtom(Predicate p, GroundTerm[] arguments) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResultListValues getFacts(Predicate p, Term[] arguments) {
			throw new UnsupportedOperationException();
		}

		@Override
		public Map<PredicatePosition, ResultListValues> getAllFactsWith(
				GroundTerm e) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void persist(Atom atom) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResultList query(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResultList query(Formula f, VariableAssignment partialGrounding) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResultList query(Formula f, List<Variable> projectTo) {
			throw new UnsupportedOperationException();
		}

		@Override
		public ResultList query(Formula f) {
			throw new UnsupportedOperationException();
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
		public void setAtomStore(AtomStore store) {
			throw new UnsupportedOperationException();
		}

		@Override
		public AtomStore getAtomStore() {
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
	
	@Before
	public void setUp() throws Exception {
		// create a predicate
		PredicateFactory factory = new PredicateFactory();
		Predicate predicate = factory.createStandardPredicate(
				"same"
				, PredicateTypes.SoftTruth
				, new ArgumentType[]{ArgumentTypes.Entity, ArgumentTypes.Entity}
				, new double[]{0.0}
			);
		// create a DB
		
	}

}
