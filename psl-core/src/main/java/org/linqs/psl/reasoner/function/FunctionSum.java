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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A numeric function defined as a sum of {@link FunctionSummand FunctionSummands}.
 */
public class FunctionSum implements Iterable<FunctionSummand>, FunctionTerm {
	private final List<FunctionSummand> sum;
	private boolean isConstant;
	private boolean isLinear;

	public FunctionSum() {
		sum = new ArrayList<FunctionSummand>();
		isConstant = true;
		isLinear = true;
	}

	/**
	 * Adds a {@link FunctionSummand} to the sum.
	 *
	 * @param summand the summand to add
	 */
	public void add(FunctionSummand summand) {
		sum.add(summand);

		isConstant = isConstant && summand.isConstant();
		isLinear = isLinear && summand.isLinear();
	}

	@Override
	public Iterator<FunctionSummand> iterator() {
		return sum.iterator();
	}

	public int size() {
		return sum.size();
	}

	public FunctionSummand get(int pos) {
		return sum.get(pos);
	}

	/**
	 * Returns the sum of the {@link FunctionSummand} values.
	 *
	 * @return the FunctionSum's value
	 */
	@Override
	public double getValue() {
		double val = 0.0;
		// Use numeric for loops instead of iterators in high traffic code.
		for (int i = 0; i < sum.size(); i++) {
			val += sum.get(i).getValue();
		}
		return val;
	}

	/**
	 * Get the value of this sum, but using the values passed in place of non-constants for the term.
	 * Note that the constant still applies.
	 * This is a fragile function that should only be called by the code that constructed
	 * this FunctionSum in the first place,
	 * The passed in values must match the order of non-ConstantNumber values added to this sum.
	 */
	public double getValue(double[] values) {
		double val = 0.0;

		int valueIndex = 0;
		// Use numeric for loops instead of iterators in high traffic code.
		for (int i = 0; i < sum.size(); i++) {
			FunctionSummand summand = sum.get(i);
			if (summand.getTerm() instanceof ConstantNumber) {
				val += summand.getValue();
			} else{
				val += summand.getCoefficient() * values[valueIndex];
				valueIndex++;
			}
		}

		return val;
	}

	@Override
	public boolean isLinear() {
		return isLinear;
	}

	@Override
	public boolean isConstant() {
		return isConstant;
	}

	@Override
	public String toString() {
		StringBuilder string = new StringBuilder();
		string.append("(");
		boolean skip = true;
		for (FunctionTerm term : sum) {
			if (skip) {
				skip = false;
			} else {
				string.append("+");
			}
			string.append(" " + term.toString() + " ");
		}
		string.append(")");
		return string.toString();
	}
}
