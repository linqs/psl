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
package edu.umd.cs.psl.model.kernel.externalinducer;

import java.util.Set;

import org.apache.commons.lang.builder.HashCodeBuilder;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.MaxFunction;

public class GroundExternalInducer implements GroundCompatibilityKernel {

	private final ExternalInducerKernel kernel;
	private final Atom atom;
	private double[] values;
	
	public GroundExternalInducer(ExternalInducerKernel k, Atom atom, double[] vals) {
		Preconditions.checkArgument(atom.getNumberOfValues()==1 && vals.length==1);
		kernel = k;
		this.atom = atom;
		values = vals.clone();
	}
	
//	public void updateValues(double[] values) {
//		this.values = values.clone();
//	}
	

	@Override
	public boolean updateParameters() {
		return true;
	}
	
	@Override
	public FunctionTerm getFunctionDefinition() {
		FunctionSum sum = new FunctionSum();
		sum.add(new FunctionSummand(1.0,new ConstantNumber(values[0])));
		sum.add(new FunctionSummand(-1.0,atom.getVariable()));
		return MaxFunction.of(sum,new ConstantNumber(0.0));
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
			return BindingMode.StrongRV;
		} else return BindingMode.NoBinding;
	}
	
	@Override
	public int hashCode() {
		return new HashCodeBuilder().append(atom).append(kernel).toHashCode();
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		GroundExternalInducer con = (GroundExternalInducer)oth;
		return kernel.equals(con.kernel) && atom.equals(con.atom);
	}

	@Override
	public Kernel getKernel() {
		return kernel;
	}

	@Override
	public String toString() {
		return "{" + kernel.getWeight() + "} External Function: " + atom + " | values " + values;
	}
	
	/*
	 * ######## Derivatives ###########
	 */

	private double getValue() {
		return Math.max(0.0, values[0]-atom.getSoftValue(0));
	}
	
	@Override
	public double getIncompatibilityDerivative(int parameterNo) {
		assert parameterNo==0 && getWeight().getWeight()>=0;
		return getValue();
	}

	@Override
	public double getIncompatibility() {
		assert getWeight().getWeight()>=0;
		return getWeight().getWeight()*getValue();
	}

	@Override
	public double getIncompatibilityHessian(int parameterNo1, int parameterNo2) {
		assert parameterNo1==0 && parameterNo2==0 && getWeight().getWeight()<=0;
		return 0;
	}


}
