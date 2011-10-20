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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.AtomEventSets;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;

public class FormulaEventAnalysis {

	private static final AtomEventSets defaultFactEvent = AtomEventSets.NonDefaultFactEvent;
	
	private final Multimap<Predicate,Atom> dependence;
	private final Formula formula;
	private final Set<Formula> queries;
	
	public FormulaEventAnalysis(Formula f) {
		formula = f;
		dependence = ArrayListMultimap.create();
		//FormulaTraverser.traverse(formula, new FormulaAnalyser());
		queries = new HashSet<Formula>();
		Conjunction c = ((Conjunction) formula).flatten();
		
		Vector<Formula> necessary = new Vector<Formula>(c.getNoFormulas());
		Vector<Formula> oneOf = new Vector<Formula>(c.getNoFormulas());
		Atom a;
		for (int i = 0; i < c.getNoFormulas(); i++) {
			if (c.get(i) instanceof Atom) {
				a = (Atom) c.get(i);
				if (a.getPredicate().getNumberOfValues() == 1) {
					if (a.getPredicate().getDefaultValues()[0] == 0.0) {
						necessary.add(a);
						dependence.put(a.getPredicate(), a);
					}
					else {
						oneOf.add(a);
					}
				}
				else {
					oneOf.add(a);
				}
			}
			else if (c.get(i) instanceof Negation) {
				a = (Atom) ((Negation) c.get(i)).getFormula();
				if (a.getPredicate().getNumberOfValues() == 1) {
					if (a.getPredicate().getDefaultValues()[0] != 0.0) {
						oneOf.add(a);
					}
				}
			}
		}
		
		if (necessary.size() == 1) {
			queries.add(necessary.get(0));
		}
		else {
			//if (oneOf.isEmpty()) {
				queries.add(new Conjunction((Formula[]) necessary.toArray(new Formula[necessary.size()])));
			//}
			//else {
			//	for (Formula formula : oneOf) {
			//		queries.add(new Conjunction(new Conjunction((Formula[]) necessary.toArray(new Formula[necessary.size()])), formula));
			//	}
			//}			
		}
	}
	
	public Formula getFormula() {
		return formula;
	}
	
	public Set<Formula> getQueryFormulas() {
		return queries;
	}
	
	public List<VariableAssignment> traceAtomEvent(Atom atom) {
		Collection<Atom> atoms = dependence.get(atom.getPredicate());
		List<VariableAssignment> vars = new ArrayList<VariableAssignment>(atoms.size());
		for (Atom entry : atoms) {
			//Check whether arguments match
			VariableAssignment var = new VariableAssignment();
			Term[] argsGround = atom.getArguments();
			Term[] argsTemplate = entry.getArguments();
			assert argsGround.length==argsTemplate.length;
			for (int i=0;i<argsGround.length;i++) {
				if (argsTemplate[i] instanceof Variable) {
					//Add mapping
					assert argsGround[i] instanceof GroundTerm;
					var.assign((Variable)argsTemplate[i], (GroundTerm)argsGround[i]);
				} else {
					//They must be the same
					if (!argsTemplate[i].equals(argsGround[i])) {
						var = null;
						break;
					}
				}
			}
			if (var!=null) vars.add(var);
		}
		return vars;
	}
	
	public void registerFormulaForEvents(AtomEventFramework af, Kernel me, AtomEventSets inferenceAtomEvent, DatabaseAtomStoreQuery db) {
		for (Predicate p : dependence.keySet()) {
			if (db.isClosed(p)) af.registerAtomEventObserver(p, defaultFactEvent, me);
			else af.registerAtomEventObserver(p, inferenceAtomEvent, me);
		}
	}
	
	public void unregisterFormulaForEvents(AtomEventFramework af, Kernel me, AtomEventSets inferenceAtomEvent, DatabaseAtomStoreQuery db) {
		for (Predicate p : dependence.keySet()) {
			if (db.isClosed(p)) af.unregisterAtomEventObserver(p, defaultFactEvent, me);
			else af.unregisterAtomEventObserver(p, inferenceAtomEvent, me);
		}
	}
}
