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
 * Selects the maximum value among {@link FunctionTerm FunctionTerms}.
 */
public class MaxFunction implements Iterable<FunctionTerm>, FunctionTerm {

	private final List<FunctionTerm> terms;
	
	public MaxFunction() {
		terms = new ArrayList<FunctionTerm>();
	}
	
	/**
	 * Adds a {@link FunctionTerm} to the set of functions to consider.
	 */
	public void add(FunctionTerm t) {
		terms.add(t);
	}

	@Override
	public Iterator<FunctionTerm> iterator() {
		return terms.iterator();
	}
	
	public int size() {
		return terms.size();
	}
	
	/**
	 * Returns a function in the MaxFunction's set.
	 *
	 * @param pos  the index of the function to return
	 * @return  the function
	 */
	public FunctionTerm get(int pos) {
		return terms.get(pos);
	}

	/**
	 * Returns the maximum of the values of the functions.
	 *
	 * @return  the MaxFunction's value
	 */
	@Override
	public double getValue() {
		if (terms.isEmpty()) throw new AssertionError("Undefined max value for zero terms!");
		double val = Double.NEGATIVE_INFINITY;
		for (FunctionTerm t : terms) val = Math.max(val, t.getValue());
		return val;
	}
	
	@Override
	public double getValue(Map<? extends FunctionVariable,Double> values, boolean useCurrentValues) {
		if (terms.isEmpty()) throw new AssertionError("Undefined max value for zero terms!");
		double val = Double.NEGATIVE_INFINITY;
		for (FunctionTerm t : terms) val = Math.max(val, t.getValue(values,useCurrentValues));
		return val;
	}


	
	@Override
	public boolean isLinear() {
		for (FunctionTerm t : terms) {
			if (!t.isLinear()) return false;
		}
		return true;
	}
	
	@Override
	public boolean isConstant() {
		for (FunctionTerm t : terms) {
			if (!t.isConstant()) return false;
		}
		return true;
	}
	
	/**
	 * Constructs a MaxFunction term containing the specified terms.
	 *
	 * @param terms  the terms to add to the MaxFunction
	 * @return  the MaxFunction
	 */
	public static MaxFunction of(FunctionTerm...terms) {
		MaxFunction max = new MaxFunction();
		for (FunctionTerm t : terms) max.add(t);
		return max;
	}
	
	@Override
	public String toString() {
		StringBuilder string = new StringBuilder();
		string.append("Max{");
		boolean skip = true;
		for (FunctionTerm term : terms) {
			if (skip)
				skip = false;
			else
				string.append(",");
			string.append(" " + term.toString() + " ");
		}
		string.append("}");
		return string.toString();
	}
	
}
