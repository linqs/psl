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

import java.util.Map;

/**
 * A {@link FunctionTerm} with constant value.
 */
public class ConstantNumber implements FunctionSingleton {

	private final double value;
	
	public ConstantNumber(double val) {
		value = val;
	}
	
	public double getValue() {
		return value;
	}
	
	
	@Override
	public double getValue(Map<? extends FunctionVariable,Double> values, boolean useCurrentValues) {
		return getValue();
	}

	@Override
	public boolean isLinear() {
		return true;
	}

	@Override
	public boolean isConstant() {
		return true;
	}
	
	@Override
	public String toString() {
		return String.valueOf(value);
	}
	
}
