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

import org.linqs.psl.model.atom.GroundAtom;

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

	/**
	 * Get the value of the sum, but replace the value of a single RVA with the given value.
	 * This should only be called by people who really know what they are doing.
	 * Note that the value of the RVA is NOT used, it is only used to find the matching function term.
	 * The general version of would be to just have a map,
	 * However, the common use case is just having one variable change value and this is typically
	 * very high traffic making the map (and autoboxing double) overhead noticable.
	 */
	public double getValue(GroundAtom replacementAtom, double replacementValue) {
		double val = 0.0;

		// Use numeric for loops instead of iterators in high traffic code.
		for (int i = 0; i < sum.size(); i++) {
			// Only one instance of each atom exists and we are not tring to match a query atom.
			if (sum.get(i).getTerm() instanceof MutableAtomFunctionVariable &&
					((MutableAtomFunctionVariable)sum.get(i).getTerm()).getAtom() == replacementAtom) {
				val += sum.get(i).getCoefficient() * replacementValue;
			} else {
				val += sum.get(i).getValue();
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
