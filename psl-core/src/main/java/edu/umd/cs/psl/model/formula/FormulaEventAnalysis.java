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
package edu.umd.cs.psl.model.formula;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomEvent;
import edu.umd.cs.psl.model.atom.AtomEventFramework;
import edu.umd.cs.psl.model.atom.VariableAssignment;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

public class FormulaEventAnalysis {

	private Multimap<Predicate,Atom> dependence;
	private Set<Formula> queries;
	
	public FormulaEventAnalysis(List<Atom> atoms) {
		dependence = ArrayListMultimap.create();
		queries = new HashSet<Formula>();
		
		for (int i = 0; i < atoms.size(); i++)
			dependence.put(atoms.get(i).getPredicate(), atoms.get(i));
		
		if (atoms.size() == 0)
			throw new IllegalArgumentException("Must provide at least one Atom.");
		else if (atoms.size() == 1)
			queries.add(atoms.get(0));
		else
			queries.add(new Conjunction(atoms.toArray(new Formula[atoms.size()])));
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
	
	public void registerFormulaForEvents(AtomEventFramework eventFramework, Kernel k, Set<AtomEvent> events) {
		for (Predicate p : dependence.keySet()) {
			if (p instanceof StandardPredicate && !eventFramework.getDatabase().isClosed((StandardPredicate) p)) {
				eventFramework.registerAtomEventListener(events, k);
			}
		}
	}
	
	public void unregisterFormulaForEvents(AtomEventFramework eventFramework, Kernel k, Set<AtomEvent> events) {
		for (Predicate p : dependence.keySet()) {
			if (p instanceof StandardPredicate && !eventFramework.getDatabase().isClosed((StandardPredicate) p)) {
				eventFramework.unregisterAtomEventListener(events, k);
			}
		}
	}
}
