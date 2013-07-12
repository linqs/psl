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
package edu.umd.cs.psl.application.learning.weight.maxmargin;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.kernel.BindingMode;
import edu.umd.cs.psl.model.kernel.CompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.parameters.Weight;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;

/**
 * Special ground kernel that penalizes being close to a fixed value of 1.0 or 0.0.
 * 
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class LossAugmentingGroundKernel implements GroundCompatibilityKernel {

	private static final Logger log = LoggerFactory.getLogger(LossAugmentingGroundKernel.class);

	private GroundAtom atom;
	private double groundTruth;	
	private Weight weight;
	
	public LossAugmentingGroundKernel(GroundAtom atom, double truthValue, Weight weight) {
		this.atom = atom;
		this.groundTruth = truthValue;
		if (!(groundTruth == 1.0 || groundTruth == 0.0))
			throw new IllegalArgumentException("Truth value must be 1.0 or 0.0.");
		this.weight = weight;
	}

	@Override
	public boolean updateParameters() {
		log.warn("Called unsupported function on LossAugmentedGroundKernel");
		return false;
	}

	@Override
	public CompatibilityKernel getKernel() {
		return null;
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		Set<GroundAtom> ret = new HashSet<GroundAtom>();
		ret.add(atom);
		return ret;
	}

	@Override
	public double getIncompatibility() {
		return Math.abs(atom.getValue() - this.groundTruth);
	}

	@Override
	public BindingMode getBinding(Atom atom) {
		return null;
	}

	@Override
	public Weight getWeight() {
		return weight;
	}

	@Override
	public void setWeight(Weight w) {
		this.weight = w;
	}
	
	@Override
	public FunctionTerm getFunctionDefinition() {
		FunctionSum sum = new FunctionSum();
		if (groundTruth == 1.0) {
			sum.add(new FunctionSummand(1.0, new ConstantNumber(1.0)));
			sum.add(new FunctionSummand(-1.0, atom.getVariable()));
		}
		else if (groundTruth == 0.0) {
			sum.add(new FunctionSummand(1.0, atom.getVariable()));
		}
		else {
			throw new IllegalStateException("Ground truth is not 0 or 1.");
		}
		
		return sum;
	}
	
	public GroundAtom getAtom() {
		return atom;
	}
	
}
