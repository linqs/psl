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

import java.util.Set;

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

public class EvidSetMin implements EntityAggregatorFunction {

	private final static double defaultThreshold = 0.01;	
	private final static double defaultEmptySetSim = 0.0;
	private final static double defaultMultiplier = 1.0;
	
	private final double emptySetSim;
	private final double supportThreshold;
	private final double multiplier;
	
	public EvidSetMin(double multiplier, double emptySet, double threshold) {
		supportThreshold = threshold;
		emptySetSim = emptySet;
		this.multiplier = multiplier;
	}
	
	public EvidSetMin(double multiplier) {
		this(multiplier, defaultEmptySetSim, defaultThreshold);
	}
	
	public EvidSetMin() {
		this(defaultMultiplier,defaultEmptySetSim, defaultThreshold);
	}
	
	public EvidSetMin(String[] args) {
		this(Double.parseDouble(args[0]));
	}
	
	@Override
	public String getName() {
		return "=={}";
	}
	
	@Override
	public double getSizeMultiplier(TermMembership set1, TermMembership set2) {
		//return multiplier*Math.max(set1.size(),set2.size());
    return multiplier;
	}
	
	protected double constantFactor(TermMembership set1, TermMembership set2) {
		//return 2.0/(set1.size()+set2.size());
		//return 1.0/(multiplier*Math.max(set1.size(),set2.size()));
		return 1.0/multiplier;
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

    /*System.out.println("AAAAA " + atom.getName());
    System.out.println("set1.getDegree((GroundTerm)terms[0]) " + set1.getDegree((GroundTerm)terms[0]));
    System.out.println("set2.getDegree((GroundTerm)terms[1]) " + set2.getDegree((GroundTerm)terms[1]));
    double tt = Math.min(set1.getDegree((GroundTerm)terms[0]),set2.getDegree((GroundTerm)terms[1]));
    if (tt != 1.0) { System.out.println("!1.0"); System.exit(-1); }*/

		//return Math.min(set1.getDegree((GroundTerm)terms[0]),set2.getDegree((GroundTerm)terms[1]));
    return 1.0;
	}
	
	@Override
	public double aggregateValue(TermMembership set1, TermMembership set2,
			Set<GroundAtom> comparisonAtoms) {
    //System.out.println("------aggregateValue");
		double truth = 0.0;
		for (GroundAtom atom : comparisonAtoms) {
			truth+=getAtomFactor(atom,set1,set2)*atom.getValue();     
      //System.out.println("\tatom: " + atom.toString() + " = " + atom.getSoftValue(0));
		}

    //System.out.println("\tconstantFactor = "+ constantFactor(set1,set2));
    //System.out.println("\ttruth = " + truth);

		double sim = constantFactor(set1,set2)*truth;
    if (sim > 1.0) sim = 1.0;

    //System.out.println("\tsim   = " + sim);

		if (comparisonAtoms.isEmpty()) sim = getDefaultSimilarityforEmptySets();

    if (getConstraintType() != FunctionComparator.Equality) { System.out.println("!Eq"); System.exit(-1); }

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
    //System.out.println("------defineConstraint");
		double coeff = constantFactor(set1,set2);
		FunctionSum sum = new FunctionSum();		
		if (comparisonAtoms.isEmpty()) {
			sum.add(new FunctionSummand(1.0,new ConstantNumber(getDefaultSimilarityforEmptySets())));
		} else {


      double totalTruth = 0.0;
			for (GroundAtom atom : comparisonAtoms)
        totalTruth += atom.getValue();

      //System.out.println("\tTotalTruth = " + totalTruth);
      if ( totalTruth > coeff) coeff = 1/totalTruth; 
      //System.out.println("\tcoeff = " + coeff);

      double tmpVal = coeff;

			for (GroundAtom atom : comparisonAtoms) {

        //double tmpVal = coeff*getAtomFactor(atom,set1,set2);
        //System.out.println("\t " + tmpVal);
        //if (tmpVal > 1.0) tmpVal = 1.0;

        //System.out.println("\tconstraintAtom: " + atom.toString() + " = " + atom.getSoftValue(0));

				sum.add(new FunctionSummand(tmpVal,atom.getVariable()));
			}
		}
		sum.add(new FunctionSummand(-1.0,setAtom.getVariable()));

    //ConstraintTerm ct = new ConstraintTerm(sum, getConstraintType(), 0);
    //System.out.println("\tConstraint : " + ct.toString());

		return new ConstraintTerm(sum, getConstraintType(), 0);
	}
	
	@Override
	public boolean enoughSupport(TermMembership set1,
			TermMembership set2, Set<GroundAtom> comparisonAtoms) {
    System.out.println("-------enoughSupport");
    System.exit(-1);
    if (set1.size()<=0.0 || set2.size()<=0.0) return false;
    return true;
    //return comparisonAtoms.size()*constantFactor(set1,set2)>=supportThreshold;
	}
	
	@Override
	public String toString() {
		return getName();
	}
	
}
