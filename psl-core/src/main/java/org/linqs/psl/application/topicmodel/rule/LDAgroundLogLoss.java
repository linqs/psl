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
package org.linqs.psl.application.topicmodel.rule;

import java.util.List;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.WeightedRule;

/**
 * Ground log loss rules for LDA.  Keeps a pointer to an array of coefficients,
 * which may be updated.
 * 
 * @author Jimmy Foulds <jfoulds@ucsc.edu>
 *
 */
public class LDAgroundLogLoss extends GroundLogLoss {
	final double[] coefficientsArray;
	public LDAgroundLogLoss(WeightedRule k, List<GroundAtom> literals, List<Double> coefficients, double[] coefficientsArray) {
		super(k, literals, coefficients);
		this.coefficientsArray = coefficientsArray;
	}
	
	public double[] getCoefficientsArray() {
		return coefficientsArray;
	}

}
