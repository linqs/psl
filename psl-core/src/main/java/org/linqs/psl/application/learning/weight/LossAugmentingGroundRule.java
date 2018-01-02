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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.ConstantNumber;
import org.linqs.psl.reasoner.function.FunctionSum;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.reasoner.function.FunctionTerm;

import java.util.HashSet;
import java.util.Set;

/**
 * Special ground rule that penalizes being close to a fixed value of 1.0 or 0.0.
 *
 * @author Bert Huang <bert@cs.umd.edu>
 */
public class LossAugmentingGroundRule implements WeightedGroundRule {
	private GroundAtom atom;
	private double groundTruth;
	private double weight;

	public LossAugmentingGroundRule(GroundAtom atom, double truthValue, double weight) {
		this.atom = atom;

		this.groundTruth = truthValue;
		if (!(groundTruth == 1.0 || groundTruth == 0.0)) {
			throw new IllegalArgumentException("Truth value must be 1.0 or 0.0.");
		}

		this.weight = weight;
	}

	@Override
	public WeightedRule getRule() {
		return null;
	}

	@Override
	public boolean isSquared() {
		return false;
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
	public double getWeight() {
		return weight;
	}

	@Override
	public void setWeight(double weight) {
		this.weight = weight;
	}

	@Override
	public FunctionTerm getFunctionDefinition() {
		FunctionSum sum = new FunctionSum();
		if (groundTruth == 1.0) {
			sum.add(new FunctionSummand(1.0, new ConstantNumber(1.0)));
			sum.add(new FunctionSummand(-1.0, atom.getVariable()));
		} else if (groundTruth == 0.0) {
			sum.add(new FunctionSummand(1.0, atom.getVariable()));
		} else {
			throw new IllegalStateException("Ground truth is not 0 or 1.");
		}

		return sum;
	}

	public GroundAtom getAtom() {
		return atom;
	}
}
