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
package edu.umd.cs.psl.application.topicmodel.reasoner.admm;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import edu.umd.cs.psl.application.topicmodel.kernel.LDAgroundLogLoss;
import edu.umd.cs.psl.application.topicmodel.reasoner.function.NegativeLogFunction;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.admm.ADMMObjectiveTerm;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;
import edu.umd.cs.psl.reasoner.function.ConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionSum;
import edu.umd.cs.psl.reasoner.function.FunctionTerm;

/** 
 * Wrapper around the ADMM reasoner which adds a little functionality needed
 * for latent topic networks.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class LatentTopicNetworkADMMReasoner extends ADMMReasoner {

	/**
	 * Key for positive double property. Minimum value that theta and phi parameters
	 * are allowed to take, enforced by clipping the consensus variables to this.
	 */
	public static final String LOWER_BOUND_EPSILON_KEY = CONFIG_PREFIX + ".lowerboundepsilon";
	/** Default value for LOWER_BOUND_EPSILON_KEY property */
	public static final double LOWER_BOUND_EPSILON_DEFAULT = 1e-6;
	
	protected double lowerBoundEpsilon; //smallest allowable value for variables. Keeps parameters in the interior of the space.
										//This is necessary for topic modeling as zero-valued entries in theta or phi will cause EM to fail.
	public LatentTopicNetworkADMMReasoner(ConfigBundle config) {
		super(config);
		lowerBoundEpsilon = config.getDouble(LOWER_BOUND_EPSILON_KEY, LOWER_BOUND_EPSILON_DEFAULT);
	}
	
	@Override
	protected void buildGroundModel() {
		super.buildGroundModel();
		//TODO only the LDA variables!
		for (int i = 0; i < lb.size(); i++) {
			lb.set(i, new Double(lowerBoundEpsilon));
		}
		initDirichletTerms();
	}
	
	@Override
	protected ADMMObjectiveTerm createTerm(GroundKernel groundKernel) {
		FunctionTerm function;
		ADMMObjectiveTerm term;
		
		/* If it's a NegativeLogFunction, constructs the objective term (a log loss).
		 * Note that this must come before FunctionSum, since NegativeLogFunction extends FunctionSum.*/
		if (groundKernel instanceof GroundCompatibilityKernel) {
			function = ((GroundCompatibilityKernel) groundKernel).getFunctionDefinition();
			if (function instanceof NegativeLogFunction) {
				Hyperplane hp = processHyperplane((FunctionSum) function);
				if (groundKernel instanceof LDAgroundLogLoss) {
					term = new NegativeLogLossTerm(this, hp.zIndices, ((LDAgroundLogLoss) groundKernel).getCoefficientsArray(), ((GroundCompatibilityKernel) groundKernel).getWeight().getWeight());
				}
				else {
					term = new NegativeLogLossTerm(this, hp.zIndices, hp.coeffs, ((GroundCompatibilityKernel) groundKernel).getWeight().getWeight());
				}
				return term;
			}
			else {
				return super.createTerm(groundKernel);
			}
		}
		else if (groundKernel instanceof GroundConstraintKernel) {
			ConstraintTerm constraint = ((GroundConstraintKernel) groundKernel).getConstraintDefinition();
			function = constraint.getFunction();
			if (function instanceof FunctionSum) {
				Hyperplane hp = processHyperplane((FunctionSum) function);
				term = new LtnLinearConstraintTerm(this, hp.zIndices, hp.coeffs,
						constraint.getValue() + hp.constant, constraint.getComparator());
				return term;
			}
			else
				throw new IllegalArgumentException("Unrecognized constraint: " + constraint);
		}
		else
			throw new IllegalArgumentException("Unsupported ground kernel: " + groundKernel);
	}
	
	/* Initialize ADMM parameters to the optimal value for vanilla LDA.
	 */
	public void initDirichletTerms() {
		System.out.println("Init Dirichlet terms");
		Map<NegativeLogLossTerm, LtnLinearConstraintTerm> dirichletTerms = new HashMap<NegativeLogLossTerm, LtnLinearConstraintTerm>();
		//Find NegativeLogLossTerm and LinearConstraintTerm pairs, and store them in a HashMap.
		for (int i = 0; i < varLocations.size(); i++) {
			List<VariableLocation> vlList = varLocations.get(i);
			NegativeLogLossTerm NLLterm = null;
			LtnLinearConstraintTerm linearConstraintTerm = null;
			for (int j = 0; j < vlList.size(); j++) {
				VariableLocation vl = vlList.get(j);
				ADMMObjectiveTerm term = vl.getTerm();
				if (term instanceof NegativeLogLossTerm) {
					assert (NLLterm == null); //this code currently assumes only one of these per var
					NLLterm = (NegativeLogLossTerm)term;
				}
				if (term instanceof LtnLinearConstraintTerm) {
					assert (linearConstraintTerm == null); //this code currently assumes only one of these per var.
					linearConstraintTerm = (LtnLinearConstraintTerm)term;
				}
			}
			if ((NLLterm != null) && (linearConstraintTerm != null)) {
				//found a Dirichlet potential.
				dirichletTerms.put(NLLterm, linearConstraintTerm);
			}
		}
		
		//Perform the initialization
		for (NegativeLogLossTerm nll : dirichletTerms.keySet()) {
			LtnLinearConstraintTerm lct = dirichletTerms.get(nll);
			double dirichletCoefficientSum = nll.initAsDirichlet();
			lct.initDualVariablesAsDirichlet(dirichletCoefficientSum);
		}
		
	}

}
