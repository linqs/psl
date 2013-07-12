/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.groovy.syntax;

import edu.umd.cs.psl.model.set.term.FormulaSetTerm;
import edu.umd.cs.psl.model.set.term.SetTerm;

import java.util.Set;

import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.formula.*;
import edu.umd.cs.psl.model.atom.*;
import edu.umd.cs.psl.model.predicate.*;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.groovy.PSLModel;
import edu.umd.cs.psl.model.set.*;

class SetFormulaConstructor extends SetTermConstructor {
	
	private static final String auxVarName = "tv";
	private static int auxVarNr = 0;
	
	private Set<Variable> anchorVars;
	private Variable leafVar;
	
	private Formula formula;
	private final PSLModel model;
	
	SetFormulaConstructor(PSLModel m, String predname, GenericVariable anchor, OIPModifier modifier) {
		model = m;
		anchorVars = new HashSet<Variable>();
		anchorVars.add anchor.toAtomVariable();
		leafVar = anchor.toAtomVariable();
		appendPredicate(predname,modifier);
	}
	
	private void appendPredicate(String predname, OIPModifier modifier) {
		Predicate pred = model.getPredicate(predname);
		if (pred==null) throw new IllegalArgumentException("Unrecognized predicate in set term construction: ${predname}");
		if (!(pred instanceof StandardPredicate)) throw new IllegalArgumentException("Only basic predicates are supported in the set term construction, but was given: ${predname}");
		if (pred.getArity()!=2) throw new IllegalArgumentException("Can only use arity 2 predicates in set term construction, but encountered: ${pred}");
		def auxVar = new Variable(auxVarName + (++auxVarNr));
		Term[] terms = new Term[2];
		if (modifier==OIPModifier.None) {
			terms[0] = leafVar;
			terms[1] = auxVar;
		} else if (modifier==OIPModifier.Inverse) {
			terms[0] = auxVar;
			terms[1] = leafVar;
		}
		if (formula==null) formula = new QueryAtom(pred,terms);			
		else formula = new Conjunction(formula, new QueryAtom(pred,terms));
		leafVar = auxVar;
	}
	
	def propertyMissing(String name) {
		appendPredicate(name,OIPModifier.None);
	}
	
	def methodMissing(String name, args) {
		if (args.length==0) appendPredicate(name,OIPModifier.None);
		else if (args.length==1) return new SetFormulaConstructor(model,name,this,OIPModifier.parse(args[0]));
		else throw new IllegalArgumentException("Unrecognized modifier used on predicate [${name}] in set term construction: ${args}");
	}
	
	
	public SetTerm getSetTerm() {
		return new FormulaSetTerm(formula,leafVar,anchorVars);
	}
	
	
	
	
}
