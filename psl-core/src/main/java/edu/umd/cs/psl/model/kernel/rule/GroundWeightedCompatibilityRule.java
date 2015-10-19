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
package edu.umd.cs.psl.model.kernel.rule;

import java.util.List;

import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

/**
 * A Ground Compatibility Rule with weights on the literals
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class GroundWeightedCompatibilityRule extends GroundCompatibilityRule {
	protected final List<Double> posLiteralsWeights; 
	protected final List<Double> negLiteralsWeights;
	
	GroundWeightedCompatibilityRule(CompatibilityRuleKernel k, List<GroundAtom> posLiterals,
			List<GroundAtom> negLiterals, boolean squared, List<Double> posLiteralsWeights, List<Double> negLiteralsWeights) {
		super(k, posLiterals, negLiterals, squared);
		this.posLiteralsWeights = posLiteralsWeights;
		this.negLiteralsWeights = negLiteralsWeights;
	}
	
	@Override
	protected FunctionSum getFunction() {
		FunctionSum sum = new FunctionSum();
		
		double totalPosWeight = 0;
		for (int i = 0; i < posLiterals.size(); i++) {
			GroundAtom atom = posLiterals.get(i);
			double weight = posLiteralsWeights.get(i);
			sum.add(new FunctionSummand(weight, atom.getVariable()));
			totalPosWeight += weight;
		}
		
		for (int i = 0; i < negLiterals.size(); i++) {
			GroundAtom atom = negLiterals.get(i);
			double weight = negLiteralsWeights.get(i);
			sum.add(new FunctionSummand(-1.0 * weight, atom.getVariable()));
		}
		
		sum.add(new FunctionSummand(1.0, new ConstantNumber(1.0 - totalPosWeight)));
		
		return sum;
	}

	@Override
	public String toString() {	
		String str = super.toString();
		str += "literal weights: ";
		for (int i = 0; i < posLiterals.size(); i++) {
			str += posLiterals.get(i).toString() + " " + posLiteralsWeights.get(i).toString() + " ";
		}
		for (int i = 0; i < negLiterals.size(); i++) {
			str += negLiterals.get(i).toString() + " " + negLiteralsWeights.get(i).toString() + " ";
		}
		return str;
	}
}
