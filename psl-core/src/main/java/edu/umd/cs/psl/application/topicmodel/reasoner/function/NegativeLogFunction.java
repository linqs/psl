/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.application.topicmodel.reasoner.function;

import java.util.Map;

import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;
import edu.umd.cs.psl.reasoner.function.FunctionVariable;

/**
 * Computes minus the sum of the logs of the terms of {@link FunctionSummand FunctionSummands}.
 * In the sum, the logarithms are multiplied by the coefficients of the FunctionSummands.
 */
public class NegativeLogFunction extends FunctionSum {
	
	public NegativeLogFunction() {
		super();
	}
	
	
	/**
	 * Returns the sum of the logs of the values of the atoms
	 *
	 * @return  the NegativeLogFunction's value
	 */
	@Override
	public double getValue() {
		double val = 0.0;
		for (FunctionSummand s : sum) val-= s.getCoefficient() * Math.log(s.getTerm().getValue());
		return val;
	}
	
	@Override
	public double getValue(Map<? extends FunctionVariable,Double> values, boolean useCurrentValues) {
		//TODO I'm not at all sure that this is what should be done here.
		double val = 0.0;
		for (FunctionSummand s : sum) val-= s.getCoefficient() * Math.log(s.getTerm().getValue(values, useCurrentValues));
		return val;
	}


	
	@Override
	public boolean isLinear() {
		return false;
	}
	
		
	@Override
	public String toString() {
		StringBuilder string = new StringBuilder();
		string.append("(");
		boolean skip = true;
		for (FunctionTerm term : sum) {
			if (skip)
				skip = false;
			else
				string.append("-");
			string.append("log( " + term.toString() + ") ");
		}
		string.append(")");
		return string.toString();
	}
	
}
