/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.model.kernel.linearconstraint;

import java.util.HashSet;
import java.util.Set;

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

/**
 * A simple constraint that fixes the truth value of a {@link RandomVariableAtom}
 * 
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public class GroundValueConstraint implements GroundConstraintKernel {
	
	private final RandomVariableAtom atom;
	
	private final double value;
	
	public GroundValueConstraint(RandomVariableAtom atom, double value) {
		this.atom = atom;
		this.value = value;
	}

	@Override
	public boolean updateParameters() {
		return false;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		Set<GroundAtom> atoms = new HashSet<GroundAtom>();
		atoms.add(atom);
		return atoms;
	}

	@Override
	public BindingMode getBinding(Atom atom) {
		if (this.atom.equals(atom))
			return BindingMode.StrongCertainty;
		else
			return BindingMode.NoBinding;
	}

	@Override
	public ConstraintKernel getKernel() {
		return null;
	}

	@Override
	public ConstraintTerm getConstraintDefinition() {
		FunctionSum sum = new FunctionSum();
		sum.add(new FunctionSummand(1.0, atom.getVariable()));
		return new ConstraintTerm(sum, FunctionComparator.Equality, value);
	}

	@Override
	public double getInfeasibility() {
		return Math.abs(atom.getValue() - value);
	}

}
