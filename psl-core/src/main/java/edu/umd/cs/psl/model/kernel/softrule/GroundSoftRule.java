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
package edu.umd.cs.psl.model.kernel.softrule;

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
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;

/**
 * Currently, we assume that the body of the rule is a conjunction and the head of the rule is
 * a disjunction of atoms.
 * 
 * @author Matthias Broecheler
 * @author Stephen Bach
 */

public class GroundSoftRule extends GroundCompatibilityKernel {
	
	public static final Tnorm tnorm = Tnorm.LUKASIEWICZ;
	public static final FormulaEvaluator formulaNorm =FormulaEvaluator.LUKASIEWICZ;
	
	private final SoftRuleKernel rule;
	private final Conjunction formula;
	
	private int numGroundings;

	
	private final int hashcode;
	
	public GroundSoftRule(SoftRuleKernel t, Formula f) {
		rule = t;
		formula = ((Conjunction) f).flatten();
		numGroundings=1;
		
		hashcode = new HashCodeBuilder().append(rule).append(f).toHashCode();
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
	public Weight getWeight() {
		return rule.getWeight();
	}
	
	@Override
	public boolean updateParameters() {
		return true;
	}
	
	@Override
	public FunctionTerm getFunctionDefinition() {
		assert numGroundings>=0;
		return getFunctionDefinition(numGroundings);
	}
	
	private FunctionTerm getFunctionDefinition(double multiplier) {
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
		
		return MaxFunction.of(sum,new ConstantNumber(0.0));
	}
	
	@Override
	public Set<Atom> getAtoms() {
		return (Set<Atom>) formula.getAtoms(new HashSet<Atom>());
	}

	@Override
	public Kernel getKernel() {
		return rule;
	}
	
	@Override
	public String toString() {
		return formula + " : " + rule.getWeight().toString();
	}
	

	@Override
	public double getIncompatibilityDerivative(int parameterNo) {
		assert parameterNo==0;
		return numGroundings*(1.0-getTruthValue());
	}
	
	public double getTruthValue() {
		return 1 - getFunctionDefinition(1.0).getValue();
	}

	@Override
	public double getIncompatibility() {
		return numGroundings*getWeight().getWeight()*(1.0-getTruthValue());
	}
	
	@Override
	public double getIncompatibilityHessian(int parameterNo1, int parameterNo2) {
		assert parameterNo1==0 && parameterNo2==0;
		return 0;
	}
	
	@Override
	public BindingMode getBinding(Atom atom) {
		if (getAtoms().contains(atom)) {
			return BindingMode.StrongRV;
		}
		return BindingMode.NoBinding;
	}
	

	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		GroundSoftRule rule = (GroundSoftRule)oth;
		return formula.equals(rule.formula) && rule.equals(rule.rule);
	}


	
}
