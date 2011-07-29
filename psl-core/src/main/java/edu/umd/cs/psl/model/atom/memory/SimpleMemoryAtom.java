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
package edu.umd.cs.psl.model.atom.memory;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.predicate.Predicate;

public class SimpleMemoryAtom extends MemoryAtom {

	private double softValue;
	private double confidenceValue;
	
	SimpleMemoryAtom(Predicate p, GroundTerm[] args, AtomStatus status) {
		this(p,args,status,p.getDefaultValues()[0],ConfidenceValues.defaultConfidence);
	}
	
	SimpleMemoryAtom(Predicate p, GroundTerm[] args, AtomStatus status, double softval, double confidence) {
		super(p,args,status);
		Preconditions.checkArgument(p.getNumberOfValues()==1);
		assert p.validValue(0, softval);
		assert ConfidenceValues.isValidValue(confidence);
		softValue = softval;
		confidenceValue = confidence;
	}
	
	@Override
	public void setSoftValues(double[] val) {
		Preconditions.checkArgument(val.length==1);
		setSoftValue(0,val[0]);
	}

	@Override
	public void setSoftValue(int pos, double val) {
		checkAccess();
		if (!getPredicate().validValue(pos, val)) throw new IllegalArgumentException("Illegal truth value: "+ val);
		Preconditions.checkArgument(pos==0);
		softValue = val;
	}
	
	@Override
	public void setConfidenceValues(double[] val) {
		Preconditions.checkArgument(val.length==1);
		setConfidenceValue(0,val[0]);
	}
	
	@Override
	public void setConfidenceValue(int pos, double val) {
		checkAccess();
		if (!ConfidenceValues.isValidValue(val)) throw new IllegalArgumentException("Illegal confidence value: "+ val);
		Preconditions.checkArgument(pos==0);
		confidenceValue = val;
	}
	
	@Override
	public boolean hasNonDefaultValues() {
		return getPredicate().isNonDefaultValues(new double[]{softValue});
	}
	
	@Override
	public double getSoftValue(int pos) {
		Preconditions.checkArgument(pos==0);
		return softValue;
	}
	
	@Override
	public double[] getSoftValues() {
		return new double[]{softValue};
	}
	
	@Override
	public double getConfidenceValue(int pos) {
		Preconditions.checkArgument(pos==0);
		return confidenceValue;
	}

	@Override
	public double[] getConfidenceValues() {
		return new double[]{confidenceValue};
	}
	
}
