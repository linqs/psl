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

public class NegativeWeight extends Weight {

	public NegativeWeight() {
		super();
	}
	
	public NegativeWeight(double w) {
		super(w);
	}
	
	boolean isValidWeight(double w) {
		return Double.isNaN(w) || w<=0;
	}

	@Override
	public double[] bounds(int parameterNo) {
		assert parameterNo==0;
		return new double[]{Double.NEGATIVE_INFINITY,0.0};
	}

	@Override
	public Weight duplicate() {
		return new NegativeWeight(getWeight());
	}

	
	
}
