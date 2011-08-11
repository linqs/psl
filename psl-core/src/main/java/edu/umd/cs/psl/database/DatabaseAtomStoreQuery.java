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

import java.util.*;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomStore;
import edu.umd.cs.psl.model.predicate.Predicate;

public class DatabaseAtomStoreQuery extends DatabaseQuery {

	private final AtomStore store;
	
	public DatabaseAtomStoreQuery(AtomStore store) {
		super(store.getDatabase());
		this.store = store;
	}
	
	private DatabaseAtomStoreQuery(Database db) {
		super(db);
		store = new MemoryAtomStore(db);
	}
	
	public static DatabaseAtomStoreQuery getIndependentInstance(Database db) {
		return new DatabaseAtomStoreQuery(db);
	}
	
	public Atom getConsideredAtom(Predicate p, GroundTerm[] arguments) {
		return store.getConsideredAtom(p, arguments);
	}
	
	public Set<Atom> getAtomSet(Predicate p) {
		ResultList res  = getAtoms(p);
		Set<Atom> resultAtoms = new HashSet<Atom>();
		for (int i=0;i<res.size();i++) {
			Atom a = store.getAtom(p, res.get(i));
			assert a!=null;
			resultAtoms.add(a);
		}
		return resultAtoms;
	}
	
	public Atom getAtom(Predicate p, GroundTerm[] arguments) {
		return store.getAtom(p, arguments);
	}
	
	public List<Atom> getConsideredAtoms(Predicate p) {
		ResultList res  = getAtoms(p);
		List<Atom> resultAtoms = new ArrayList<Atom>();
		for (int i=0;i<res.size();i++) {
			Atom a = getConsideredAtom(p,res.get(i));
			if (a!=null) resultAtoms.add(a);
		}
		return resultAtoms;
	}
	
	public List<Atom> getConsideredAtoms(Predicate p, Object...terms) {
		Atom query = getQueryAtom(database,p,terms);
		ResultList res = database.query(query);
		List<Atom> resultAtoms = new ArrayList<Atom>();
		for (int i=0;i<res.size();i++) {
			GroundTerm[] ground = new GroundTerm[query.getArguments().length];
			for (int k=0;k<ground.length;k++) {
				Term arg = query.getArguments()[k];
				if (arg instanceof Variable) {
					ground[k]=res.get(i, (Variable)arg);
				} else {
					assert arg instanceof GroundTerm;
					ground[k]=(GroundTerm)arg;
				}
			}
			Atom a = getConsideredAtom(p,ground);
			if (a!=null) resultAtoms.add(a);
		}
		return resultAtoms;
	}

	
}
