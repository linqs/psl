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
package edu.umd.cs.psl.model.formula.traversal;

import java.util.*;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.*;
import edu.umd.cs.psl.model.predicate.type.PredicateTypes;

public class ArrayFormulaGrounder extends FormulaTraverser {

	private final AtomManager atommanger;
	private final DatabaseAtomStoreQuery database;
	private final ResultList results;
	private final VariableAssignment varAssign;
	
	private int position;
	private boolean includeBooleanFacts;
	private ArrayList<Atom> atoms;
	
	public ArrayFormulaGrounder(AtomManager m, DatabaseAtomStoreQuery db, ResultList res, VariableAssignment var) {
		assert m!=null && res!=null;
		atommanger = m;
		database = db;
		results = res;
		position=0;
		varAssign = var;
		atoms = new ArrayList<Atom>();
	}
	
	public ArrayFormulaGrounder(AtomManager m, DatabaseAtomStoreQuery db, ResultList res) {
		this(m,db,res,null);
	}
	
	public Atom[] ground(Formula f, boolean includeBooleanFacts) {
		this.includeBooleanFacts=includeBooleanFacts;
		reset();
		FormulaTraverser.traverse(f, this);
		return get();
	}
	
	private Atom[] get() {
		return atoms.toArray(new Atom[atoms.size()]);
	}
	
	private void reset() {
		atoms.clear();
	}
	
	public boolean hasNext() {
		return position<results.size();
	}
	
	public void next() {
		position++;
		reset();
	}
	
	@Override
	public void visitAtom(Atom atom) {
		if (!includeBooleanFacts && atom.getPredicate().getType()==PredicateTypes.BooleanTruth && database.isClosed(atom.getPredicate()))
			return; //Skip this atom
		
		GroundTerm[] args = new GroundTerm[atom.getPredicate().getArity()];
		Term[] atomArgs = atom.getArguments();
		assert args.length==atomArgs.length;
		for (int i=0;i<args.length;i++) {
			if (atomArgs[i] instanceof Variable) {
				Variable v = (Variable)atomArgs[i];
				if (varAssign!=null && varAssign.hasVariable(v)) {
					args[i]=varAssign.getVariable(v);
				} else {
					args[i] = results.get(position, v);
				}
				
			} else {
				assert atomArgs[i] instanceof GroundTerm;
				args[i] = (GroundTerm)atomArgs[i];
			}
		}
		atoms.add(atommanger.getAtom(atom.getPredicate(), args));
	}
	
	public static List<Atom[]> ground(Formula f, AtomEventFramework store, DatabaseAtomStoreQuery db, ResultList results) {
		List<Atom[]> atoms = new ArrayList<Atom[]>(results.size());
		ArrayFormulaGrounder grounder = new ArrayFormulaGrounder(store, db, results);
		while (grounder.hasNext()) {
			FormulaTraverser.traverse(f, grounder);
			atoms.add(grounder.get());
			grounder.next();
		}
		return atoms;		
	}
		
}
