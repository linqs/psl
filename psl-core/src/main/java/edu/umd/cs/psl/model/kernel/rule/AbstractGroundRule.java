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
package edu.umd.cs.psl.model.kernel.rule;

import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.formula.Negation;
import edu.umd.cs.psl.model.formula.Tnorm;
import edu.umd.cs.psl.model.formula.traversal.FormulaEvaluator;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

/**
 * Currently, we assume that the body of the rule is a conjunction and the head of the rule is
 * a disjunction of atoms.
 * 
 * @author Matthias Broecheler
 * @author Stephen Bach
 */

abstract public class AbstractGroundRule implements GroundKernel {
	
	public static final Tnorm tnorm = Tnorm.LUKASIEWICZ;
	public static final FormulaEvaluator formulaNorm =FormulaEvaluator.LUKASIEWICZ;
	
	protected final AbstractRuleKernel kernel;
	protected final Conjunction formula;
	
	protected int numGroundings;

	private final int hashcode;
	
	public AbstractGroundRule(AbstractRuleKernel k, Formula f) {
		kernel = k;
		formula = ((Conjunction) f).flatten();
		numGroundings=1;
		
		hashcode = new HashCodeBuilder().append(kernel).append(f).toHashCode();
	}
	
	int getNumGroundings() {
		return numGroundings;
	}
	
	void increaseGroundings() {
		numGroundings++;
	}
	
	void decreaseGroundings() {
		numGroundings--;
		assert numGroundings>0;
	}
	
	@Override
	public boolean updateParameters() {
		return true;
	}
	
	protected FunctionSum getFunction(double multiplier) {
		Formula f;
		Atom a;
		double constant = 0.0;
		FunctionSum sum = new FunctionSum();
		
		for (int i = 0; i < formula.getNoFormulas(); i++) {
			f = formula.get(i);
			if (f instanceof Atom) {
				a = (Atom) f;
				assert a.getNumberOfValues() == 1;
				sum.add(new FunctionSummand(multiplier, a.getVariable()));
				constant++;
			}
			else if (f instanceof Negation) {
				a = (Atom) ((Negation) f).getFormula();
				assert a.getNumberOfValues() == 1;
				sum.add(new FunctionSummand(-1*multiplier, a.getVariable()));
			}
			else
				throw new IllegalStateException();
		}
		
		sum.add(new FunctionSummand(multiplier, new ConstantNumber(1.0 - constant)));
		
		return sum;
	}
	
	@Override
	public Set<Atom> getAtoms() {
		return (Set<Atom>) formula.getAtoms(new HashSet<Atom>());
	}

	@Override
	public Kernel getKernel() {
		return kernel;
	}
	
	public double getTruthValue() {
		return 1 - Math.max(getFunction(1.0).getValue(), 0);
	}
	
	@Override
	public BindingMode getBinding(Atom atom) {
		if (getAtoms().contains(atom)) {
			return BindingMode.StrongRV;
		}
		return BindingMode.NoBinding;
	}
	
	@Override
	public boolean equals(Object other) {
		if (other==this) return true;
		if (other==null || !(other instanceof GroundCompatibilityRule)) return false;
		GroundCompatibilityRule otherRule = (GroundCompatibilityRule) other;
		return kernel.equals(otherRule.kernel) && formula.equals(otherRule.formula);
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
}
