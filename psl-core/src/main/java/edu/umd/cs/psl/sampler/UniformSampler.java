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
package edu.umd.cs.psl.sampler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ujmp.core.Matrix;

abstract public class UniformSampler extends AbstractHitAndRunSampler {

	private static final Logger log = LoggerFactory.getLogger(UniformSampler.class);
	
	public UniformSampler(int maxNoSteps, int significantDigits) {
		super(maxNoSteps, significantDigits);
	}
	
	@Override
	protected double sampleAlpha(Matrix direction, Matrix Aobj, Matrix objConst, double alphaLow, double alphaHigh) {
		double alpha = alphaLow + (alphaHigh - alphaLow) * Math.random();
		log.trace("Sampled alpha {}", alpha);
		return alpha;
	}
	
}
