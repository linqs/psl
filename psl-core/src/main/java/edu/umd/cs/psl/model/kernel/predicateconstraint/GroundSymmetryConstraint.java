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
package edu.umd.cs.psl.model.kernel.predicateconstraint;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.ConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.optimizer.NumericUtilities;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

/**
 * Constrains two {@link GroundAtom GroundAtoms} to be equal.
 */
public class GroundSymmetryConstraint implements GroundConstraintKernel {

	private final ConstraintKernel kernel;
	private final GroundAtom atomA;
	private final GroundAtom atomB;
	private final Set<GroundAtom> atoms;

	private final int hashcode;

	public GroundSymmetryConstraint(ConstraintKernel k, GroundAtom a, GroundAtom b) {
		kernel = k;
		atomA = a;
		atomB = b;
		
		Set<GroundAtom> tempAtoms = new HashSet<GroundAtom>();
		tempAtoms.add(atomA);
		tempAtoms.add(atomB);
		atoms = Collections.unmodifiableSet(tempAtoms);
				
		hashcode = new HashCodeBuilder().append(kernel).append(atomA).append(atomB)
				.toHashCode()
				+ new HashCodeBuilder().append(kernel).append(atomB).append(atomA)
				.toHashCode();
	}

	@Override
	public boolean updateParameters() {
		throw new UnsupportedOperationException();
	}

	public ConstraintTerm getConstraintDefinition() {
		FunctionSum sum = new FunctionSum();
		sum.add(new FunctionSummand(1.0, atomA.getVariable()));
		sum.add(new FunctionSummand(-1.0, atomB.getVariable()));
		return new ConstraintTerm(sum, FunctionComparator.Equality, 0.0);
	}

	@Override
	public Kernel getKernel() {
		return kernel;
	}
	
	@Override
	public BindingMode getBinding(Atom atom) {
		if (atomA.equals(atom) || atomB.equals(atomB))
			return BindingMode.WeakRV;
		else
			return BindingMode.NoBinding;
	}

	@Override
	public double getIncompatibility() {
		if (NumericUtilities.equals(atomA.getValue(), atomB.getValue())) {
			return 0.0;
		} else
			return Double.POSITIVE_INFINITY;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		return atoms;
	}

	@Override
	public int hashCode() {
		return hashcode;
	}

	@Override
	public boolean equals(Object oth) {
		if (oth == this)
			return true;
		if (oth == null || !(getClass().isInstance(oth)))
			return false;
		GroundSymmetryConstraint con = (GroundSymmetryConstraint) oth;
		return (atomA.equals(con.atomA) && atomB.equals(con.atomB)
				|| atomA.equals(con.atomB) && atomB.equals(con.atomA)); 
	}

	@Override
	public String toString() {
		return "{Symmetry} on " + atomA.toString() + " and " + atomB.toString();
	}

}
