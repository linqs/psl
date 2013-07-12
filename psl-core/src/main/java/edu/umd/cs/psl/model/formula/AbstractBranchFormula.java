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
package edu.umd.cs.psl.model.formula;

import java.util.Arrays;
import java.util.Set;

import edu.umd.cs.psl.model.argument.VariableTypeMap;
import edu.umd.cs.psl.model.atom.Atom;

/**
 * An abstract branching formula.
 * <p>
 * Note, the order in which formulas appear in an AbstractBranchFormula is important!
 */
abstract class AbstractBranchFormula implements Formula {

	protected final Formula[] formulas;
	
	public AbstractBranchFormula(Formula... f) {
		if (f.length<2) throw new IllegalArgumentException("Must provide at least two formulas!");
		//TODO: Should we copy here?
		formulas = f;
		for (int i=0;i<f.length;i++) {
			if (formulas[i]==null) throw new IllegalArgumentException("Formulas must not be null!");
		}
	}

	public int getNoFormulas() {
		return formulas.length;
	}
	
	public Formula get(int pos) {
		return formulas[pos];
	}
	
	@Override
	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		for (int i=0;i<formulas.length;i++) {
			formulas[i].collectVariables(varMap);
		}
		return varMap;
	}
	
	@Override
	public Set<Atom> getAtoms(Set<Atom> atoms) {
		for (int i=0;i<formulas.length;i++) {
			formulas[i].getAtoms(atoms);
		}
		return atoms;
		
	}	

	@Override
	public int hashCode() {
		return Arrays.hashCode(formulas);
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		AbstractBranchFormula of = (AbstractBranchFormula)oth;
		return Arrays.equals(formulas, of.formulas);
	}
	
	protected abstract String separatorString();

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		s.append("( ");
		for (int i=0;i<formulas.length;i++) {
			if (i>0) s.append(" ").append(separatorString()).append(" ");
			s.append(formulas[i]);
		}
		s.append(" )");
		return s.toString();
	}


}
