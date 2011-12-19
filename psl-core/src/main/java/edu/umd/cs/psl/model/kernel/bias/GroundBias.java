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
package edu.umd.cs.psl.model.kernel.bias;

import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;

public class GroundBias implements GroundCompatibilityKernel {

	private final BiasKernel kernel;
	
	private final Atom atom;
	
	private final int hashcode;
	
	public GroundBias(BiasKernel t, Atom a) {
		kernel = t;
		atom = a;
		hashcode = new HashCodeBuilder().append(kernel).append(atom).toHashCode();
	}
		

	@Override
	public boolean updateParameters() {
		return true;
	}
	
	@Override
	public FunctionTerm getFunctionDefinition() {
		return new FunctionSummand(1.0, atom.getVariable());
	}
	
	@Override
	public Set<Atom> getAtoms() {
		return ImmutableSet.of(atom);
	}
	
	@Override
	public Weight getWeight() {
		return kernel.getWeight();
	}
	

	@Override
	public BindingMode getBinding(Atom atom) {
		if (this.atom.equals(atom)) {
			return BindingMode.WeakRV;
		} else return BindingMode.NoBinding;
	}
	
	@Override
	public int hashCode() {
		return hashcode;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		GroundBias con = (GroundBias)oth;
		return kernel.equals(con.kernel) && atom.equals(con.atom);
	}

	@Override
	public Kernel getKernel() {
		return kernel;
	}

	@Override
	public String toString() {
		return "Bias on " + atom + " " + kernel.getWeight();
	}
	
	/*
	 * ######## Derivatives ###########
	 */

	@Override
	public double getIncompatibilityDerivative(int parameterNo) {
		assert parameterNo==0;
		return getL1Distance();
	}

	@Override
	public double getIncompatibility() {
		return getWeight().getWeight()*getL1Distance();
	}
	
	protected double getL1Distance() {
		return atom.getValue();
	}

	@Override
	public double getIncompatibilityHessian(int parameterNo1, int parameterNo2) {
		assert parameterNo1==0 && parameterNo2==0;
		return 0;
	}

}
