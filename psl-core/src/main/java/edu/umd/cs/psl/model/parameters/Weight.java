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
package edu.umd.cs.psl.model.parameters;

import com.google.common.base.Preconditions;

public abstract class Weight implements Parameters {

	private static final double comparisonEpsilon = 1e-100;
	
	private double weight;
	
	public Weight() {
		weight = Double.NaN;
	}
	
	public Weight(double w) {
		checkWeight(w);
		weight = w;
	}
	
	abstract boolean isValidWeight(double w);
	
	void checkWeight(double w) {
		Preconditions.checkArgument(isValidWeight(w),"Illegal weight:" + w);
	}

	@Override
	public int numParameters() {
		return 1;
	}
	
	public double getWeight() {
		return weight;
	}
	
	public abstract Weight duplicate();


	@Override
	public void setParameter(int parameterNo, double value) {
		assert parameterNo==0;
		checkWeight(value);
		weight = value;
	}

	@Override
	public double getParameter(int parameterNo) {
		assert parameterNo==0;
		return weight;
	}

	@Override
	public double[] getParameters() {
		return new double[]{weight};
	}
	
	@Override
	public String toString() {
		return "W="+weight;
	}
	
	@Override
	public boolean equals(Object oth) {
		if (oth==this) return true;
		if (oth==null || !(getClass().isInstance(oth)) ) return false;
		Weight w = (Weight)oth;
		return Math.abs(weight - w.weight)<comparisonEpsilon;
	}
	
	
}
