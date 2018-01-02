/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.reasoner.function;

import java.util.*;

/**
 * A {@link FunctionTerm} raised to the power of two.
 */
public class PowerOfTwo implements FunctionTerm {

	private final FunctionTerm innerFunction;
	
	public PowerOfTwo(FunctionTerm innerFunction) {
		this.innerFunction = innerFunction;
	}
	
	public FunctionTerm getInnerFunction() {
		return innerFunction;
	}

	@Override
	public double getValue() {
		double val = innerFunction.getValue();
		return val * val;
	}
	
	@Override
	public double getValue(Map<? extends FunctionVariable,Double> values, boolean useCurrentValues) {
		double val = innerFunction.getValue(values, useCurrentValues);
		return val * val;
	}
	
	@Override
	public boolean isLinear() {
		return false;
	}
	
	@Override
	public boolean isConstant() {
		return innerFunction.isConstant();
	}
	
	@Override
	public String toString() {
		return "( " + innerFunction + " )^2";
	}
	
}
