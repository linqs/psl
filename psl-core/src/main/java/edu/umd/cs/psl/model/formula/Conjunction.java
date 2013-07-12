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

import java.util.ArrayList;

public class Conjunction extends AbstractBranchFormula {

	public Conjunction(Formula... f) {
		super(f);
	}
	
	@Override
	public Formula getDNF() {
		Formula[] components = new Formula[getNoFormulas()];
		ArrayList<Integer> disjunctions = new ArrayList<Integer>();
		int size = 1;
		for (int i = 0; i < components.length; i++) {
			components[i] = get(i).getDNF();
			if (components[i] instanceof Disjunction) {
				size *= ((Disjunction) components[i]).getNoFormulas();
				disjunctions.add(i);
			}
		}
		
		if (disjunctions.size() == 0)
			return new Conjunction(components);
		
		/* Distributes the conjunction operator over disjunctions */
		Formula[] dnfComponents = new Formula[size];
		Formula[] conjunctionComponents = new Formula[getNoFormulas()];
		int[] indices = new int[disjunctions.size()];
		for (int i = 0; i < size; i++) {
			/*
			 * First, increments a vector of indices corresponding to the
			 * current formula in each disjunction such that each combination
			 * of formulas will be selected once
			 */
			for (int j = 0; j < indices.length; j++) {
				indices[j]++;
				if (indices[j] == ((Disjunction) components[disjunctions.get(j)]).getNoFormulas())
					indices[j] = 0;
				else
					break;
			}
			
			/*
			 * Next, creates a conjunction from the selected formulas, finds
			 * its DNF, and adds it to the set of formulas that will be returned
			 * as a disjunction
			 */
			for (int j = 0; j < conjunctionComponents.length; j++) {
				if (components[j] instanceof Disjunction)
					conjunctionComponents[j] = ((Disjunction) components[j]).get(indices[j]);
				else
					conjunctionComponents[j] = components[j];
			}
			
			dnfComponents[i] = new Conjunction(conjunctionComponents).getDNF();
		}
		
		return new Disjunction(dnfComponents);
	}
	
	/**
	 * Collapses nested Conjunctions.
	 * <p>
	 * Stops descending where ever a Formula other than a Conjunction is.
	 * 
	 * @return the flattened Conjunction
	 */
	public Conjunction flatten() {
		ArrayList<Formula> conj = new ArrayList<Formula>(getNoFormulas());
		for (Formula f : formulas) {
			if (f instanceof Conjunction) {
				Formula[] newFormulas = ((Conjunction) f).flatten().formulas;
				for (Formula newF : newFormulas)
					conj.add(newF);
			}
			else
				conj.add(f);
		}
		return new Conjunction((Formula[]) conj.toArray(new Formula[conj.size()]));
	}

	@Override
	protected String separatorString() {
		return "&";
	}

}
