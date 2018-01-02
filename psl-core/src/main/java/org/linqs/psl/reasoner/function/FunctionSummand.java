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
 * Associates a {@link FunctionSingleton} with a coefficient.
 */
public class FunctionSummand implements FunctionTerm {

	private final double coeff;
	private final FunctionSingleton term;
	
	/**
	 * Constructs a FunctionSummand from a {@link FunctionSingleton} and a coefficient.
	 *
	 * @param c  the coefficient of the term
	 * @param t  the term to multiply with the coefficient
	 */
	public FunctionSummand(double c, FunctionSingleton t) {
		term = t;
		coeff = c;
	}

	/**
	 * Returns the value of the encapsulated {@link FunctionSingleton} multiplied
	 * by the coefficient.
	 *
	 * @return  the FunctionSummand's value
	 */
	@Override
	public double getValue() {
		return coeff*term.getValue();
	}
	
	@Override
	public double getValue(Map<? extends FunctionVariable,Double> values, boolean useCurrentValues) {
		return coeff*term.getValue(values,useCurrentValues);
	}
	
	public double getCoefficient() {
		return coeff;
	}
	
	public FunctionSingleton getTerm() {
		return term;
	}
	
	@Override
	public boolean isLinear() {
		return term.isLinear();
	}
	
	@Override
	public boolean isConstant() {
		return term.isConstant();
	}
	
	@Override
	public String toString() {
		return coeff + " * " + term.toString();
	}
	
}
