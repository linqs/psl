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

import edu.umd.cs.psl.reasoner.admm.ADMMObjectiveTerm;
import edu.umd.cs.psl.reasoner.admm.ADMMReasoner;
import edu.umd.cs.psl.reasoner.admm.WeightedObjectiveTerm;

/**
 * {@link ADMMReasoner} objective term of the form <br />
 * weight * coeffs^T * -log(x)
 * 
 * @author Jimmy Foulds <jrfoulds@gmail.com>
 */
public class NegativeLogLossTerm extends ADMMObjectiveTerm implements WeightedObjectiveTerm {
	
	private final double[] coeffs;
	private double weight;
	
	NegativeLogLossTerm(ADMMReasoner reasoner, int[] zIndices, double[] coeffs, double weight) {
		super(reasoner, zIndices);
		this.coeffs = coeffs;
		setWeight(weight);
	}

	@Override
	public void setWeight(double weight) {
		this.weight = weight;
	}
	
	@Override
	protected void minimize() {
		double a, b, c, sol1, sol2;
		for (int i = 0; i < x.length; i++) {
			//the updated value is the positive value of the two solutions to a quadratic equation
			a = reasoner.stepSize;
			b = (y[i] - a * reasoner.getConsensusVariableValue(zIndices[i]));
			c = -coeffs[i] * weight;
			sol1 = (-b + Math.sqrt(b*b - 4 * a * c)) / 2 * a;
			sol2 = (-b - Math.sqrt(b*b - 4 * a * c)) / 2 * a;
			/*if (sol1 >= 0) {
				x[i] = sol1;
			}
			else {
				x[i] = sol2;
			}*/
			x[i] = Math.max(sol1, sol2); //This should be equivalent but hopefully faster
		}
	}
	
	public double initAsDirichlet() {
		double coefficientSum = 0;
		assert (x.length == coeffs.length);
		for (int i = 0; i < coeffs.length; i++) {
			coefficientSum += coeffs[i];
		}
		for (int i = 0; i < coeffs.length; i++) {
			x[i] = coeffs[i] / coefficientSum;
			y[i] = coefficientSum;
		}
		
		return coefficientSum;
	}
}
