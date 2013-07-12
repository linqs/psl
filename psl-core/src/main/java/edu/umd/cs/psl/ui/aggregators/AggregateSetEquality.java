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
package edu.umd.cs.psl.ui.aggregators;

import java.util.*;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.set.aggregator.EntityAggregatorFunction;
import edu.umd.cs.psl.model.set.membership.TermMembership;
import edu.umd.cs.psl.reasoner.function.ConstantNumber;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionSummand;

public class AggregateSetEquality implements EntityAggregatorFunction {

	private final static double defaultThreshold = 0.01;	
	private final static double defaultEmptySetSim = 0.0;
	private final static double defaultMultiplier = 1.0;
	
	
	private final double emptySetSim;
	private final double supportThreshold;
	private final double multiplier;
	
	public AggregateSetEquality(double multiplier, double emptySet, double threshold) {
		supportThreshold = threshold;
		emptySetSim = emptySet;
		this.multiplier = multiplier;
	}
	
	public AggregateSetEquality(double multiplier) {
		this(multiplier, defaultEmptySetSim, defaultThreshold);
	}
	
	public AggregateSetEquality() {
		this(defaultMultiplier,defaultEmptySetSim, defaultThreshold);
	}
	
	public AggregateSetEquality(String[] args) {
		this(Double.parseDouble(args[0]));
	}
	
	@Override
	public String getName() {
		return "=={}";
	}
	
	@Override
	public double getSizeMultiplier(TermMembership set1, TermMembership set2) {
		return multiplier*Math.max(set1.size(),set2.size());
	}
	
	protected double constantFactor(TermMembership set1, TermMembership set2) {
		return 1.0/(multiplier*Math.max(set1.size(),set2.size()));
	}
	
	FunctionComparator getConstraintType() {
		return FunctionComparator.Equality;
	}
	
	double getDefaultSimilarityforEmptySets() {
		return emptySetSim;
	}
	
	private static final double getAtomFactor(Atom atom, TermMembership set1, TermMembership set2) {
		Term[] terms = atom.getArguments();
		assert terms.length==2;
		return Math.min(set1.getDegree((GroundTerm)terms[0]),set2.getDegree((GroundTerm)terms[1]));
	}
	
	@Override
	public double aggregateValue(TermMembership set1, TermMembership set2,
			Set<GroundAtom> comparisonAtoms) {
		double truth = 0.0;
		for (GroundAtom atom : comparisonAtoms) {
			truth+=getAtomFactor(atom,set1,set2)*atom.getValue();
		}
		double sim = constantFactor(set1,set2)*truth;
		if (comparisonAtoms.isEmpty()) sim = getDefaultSimilarityforEmptySets();

		switch(getConstraintType()) {
			case Equality: return sim;
			case SmallerThan: return Math.max(0.0, sim);
			case LargerThan: return Math.min(1.0, sim);
			default: throw new AssertionError("Unrecognized linear constraint type!");
		}
		 
	}
	
	@Override
	public ConstraintTerm defineConstraint(GroundAtom setAtom, TermMembership set1,
			TermMembership set2, Set<GroundAtom> comparisonAtoms) {
		double coeff = constantFactor(set1,set2);
		FunctionSum sum = new FunctionSum();		
		if (comparisonAtoms.isEmpty()) {
			sum.add(new FunctionSummand(1.0,new ConstantNumber(getDefaultSimilarityforEmptySets())));
		} else {
			for (GroundAtom atom : comparisonAtoms) {
				sum.add(new FunctionSummand(coeff*getAtomFactor(atom,set1,set2),atom.getVariable()));
			}
		}
		sum.add(new FunctionSummand(-1.0,setAtom.getVariable()));
		return new ConstraintTerm(sum, getConstraintType(), 0);
	}
	
	@Override
	public boolean enoughSupport(TermMembership set1,
			TermMembership set2, Set<GroundAtom> comparisonAtoms) {
		if (set1.size()<=0.0 || set2.size()<=0.0) return false;
		return comparisonAtoms.size()*constantFactor(set1,set2)>=supportThreshold;
	}
	
	@Override
	public String toString() {
		return getName();
	}
}
