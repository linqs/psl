/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.application.topicmodel.rule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.linqs.psl.application.topicmodel.reasoner.function.NegativeLogFunction;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.weight.Weight;
import org.linqs.psl.reasoner.function.FunctionSummand;
import org.linqs.psl.reasoner.function.FunctionTerm;

/**
 * Ground log loss rules, useful when PSL variables are given a probabilistic
 * interpretation, as in latent topic networks.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class GroundLogLoss implements WeightedGroundRule {
	
	private Weight weight;
	private WeightedRule kernel;
	
	protected final List<GroundAtom> literals;
	protected final List<Double> coefficients;
	
	public GroundLogLoss(WeightedRule k, List<GroundAtom> literals, List<Double> coefficients) {
		kernel = k;
		this.literals = new ArrayList<GroundAtom>(literals);
		this.coefficients = new ArrayList<Double>(coefficients);
	}

	@Override
	public WeightedRule getRule() {
		return (WeightedRule) kernel;
	}

	@Override
	public Weight getWeight() {
		if (weight == null) 
			return getRule().getWeight();
		return weight;
	}
	
	@Override
	public void setWeight(Weight w) {
		weight = w;
	}
	
	@Override
	public FunctionTerm getFunctionDefinition() {
		NegativeLogFunction returner = new NegativeLogFunction();
		Iterator<Double> cIter = coefficients.iterator();
		GroundAtom currentAtom;
		Double currentCoefficient;
		for (Iterator<GroundAtom> agIter = literals.iterator(); agIter.hasNext();) {
			currentAtom = agIter.next();
			currentCoefficient = cIter.next();
			returner.add(new FunctionSummand(currentCoefficient, currentAtom.getVariable()));
		}
		return returner;
	}

	@Override
	public double getIncompatibility() {
		double returner = 0;
		for (int i = 0; i < literals.size(); i++) {
			GroundAtom g = literals.get(i);
			double c = coefficients.get(i);
			returner = returner - c * Math.log(g.getValue());
		}
		return returner;
	}
	
	@Override
	public String toString() {
		return "{" + getWeight().toString() + "} " + "Logloss" + super.toString();
	}

	@Override
	public Set<GroundAtom> getAtoms() {
		HashSet<GroundAtom> atoms = new HashSet<GroundAtom>();
		for (GroundAtom atom : literals)
			atoms.add(atom);		
		return atoms;
	}

}
