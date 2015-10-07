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

import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;
import edu.umd.cs.psl.reasoner.admm.LinearConstraintTerm;
import edu.umd.cs.psl.reasoner.function.FunctionComparator;

/**
 * A {@link LinearConstraintTerm} that supports smart initialization
 * for latent topic networks.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 */
public class LtnLinearConstraintTerm extends LinearConstraintTerm {

	protected LtnLinearConstraintTerm(ADMMReasoner reasoner, int[] zIndices,
			double[] coeffs, double constant, FunctionComparator comparator) {
		super(reasoner, zIndices, coeffs, constant, comparator);
	}

	/* A sensible initialization for a constraint on a Dirichlet simplex variable
	 * for latent topic networks is to set the dual variables to minus the sum of the Dirichlet counts.
	 */
	protected void initDualVariablesAsDirichlet(double dirichletCoefficientSum) {
		for (int i = 0; i < y.length; i++) {
			y[i] = -dirichletCoefficientSum;
		}
	}
}
