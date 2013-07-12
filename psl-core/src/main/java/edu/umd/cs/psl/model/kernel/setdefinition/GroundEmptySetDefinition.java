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
package edu.umd.cs.psl.model.kernel.setdefinition;

import java.util.Set;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.atom.RandomVariableAtom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

public class GroundEmptySetDefinition implements GroundConstraintKernel {

	private final SetDefinitionKernel kernel;
	private final GroundAtom atom;
	private double value;
	
	public GroundEmptySetDefinition(SetDefinitionKernel k, GroundAtom atom, double val) {
		this.atom = atom;
		value = val;
		kernel = k;
		
		if (atom instanceof RandomVariableAtom)
			((RandomVariableAtom) atom).setValue(value);
	}
	
	@Override
	public ConstraintTerm getConstraintDefinition() {
		FunctionSum sum = new FunctionSum();
		sum.add(new FunctionSummand(1,atom.getVariable()));
		return new ConstraintTerm(sum,FunctionComparator.Equality,value);
	}
	
	@Override
	public double getInfeasibility() {
		return Math.abs(atom.getValue() - value);
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		return ImmutableSet.of((GroundAtom)atom);
	}

	@Override
	public BindingMode getBinding(Atom atom) {
		if (atom.equals(this.atom)) {
			if (((GroundAtom) atom).getValue() > 0.0)
				return BindingMode.StrongCertainty;
			else
				return BindingMode.WeakCertainty;
		} else
			return BindingMode.NoBinding;
	}

	@Override
	public ConstraintKernel getKernel() {
		return kernel;
	}

	@Override
	public boolean updateParameters() {
		throw new UnsupportedOperationException("Parameters of data certainty cannot change since there are none!");
	}
	
	@Override
	public int hashCode() {
		return atom.hashCode()*779;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		GroundEmptySetDefinition es = (GroundEmptySetDefinition)oth;
		return es.kernel.equals(this.kernel) && atom.equals(es.atom);
	}	

}
