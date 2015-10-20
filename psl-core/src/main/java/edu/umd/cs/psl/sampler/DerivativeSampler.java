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
package edu.umd.cs.psl.sampler;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import edu.umd.cs.psl.model.rule.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.rule.GroundRule;
import edu.umd.cs.psl.model.rule.Rule;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

public class DerivativeSampler extends UniformSampler {

	private transient Map<Rule, Double> totals;
	
	public DerivativeSampler(Collection<Rule> k) {
		this(k, defaultMaxNoSteps,defaultSignificantDigits);
	}
	
	public DerivativeSampler(Collection<Rule> k, int maxNoSteps) {
		this(k, maxNoSteps,defaultSignificantDigits);
	}
 	
	public DerivativeSampler(Collection<Rule> k, int maxNoSteps, int significantDigits) {
		super(maxNoSteps, significantDigits);
		totals = new HashMap<Rule, Double>();
		for (Rule kernel : k) {
			totals.put(kernel, 0.0);
		}
	}
	
	public double getAverage(Rule k) {
		return totals.get(k) / getNoSamples();
	}

	@Override
	protected void processNewDimension(AtomFunctionVariable var, int index) {
		/* Intentionally blank */
	}

	@Override
	protected void processSampledPoint(Iterable<GroundRule> groundKernels) {
		Map<Rule, Double> sampleTotals = new HashMap<Rule, Double>();
		for (Rule k : totals.keySet())
			sampleTotals.put(k, 0.0);
		
		double total = 0.0;
		double incompatibility;
		for (GroundRule gk : groundKernels) {
			if (gk instanceof GroundCompatibilityKernel) {
				incompatibility = ((GroundCompatibilityKernel) gk).getIncompatibility();
				total -= incompatibility;
				
	    		Rule k = gk.getKernel();
	    		if (sampleTotals.containsKey(k)) {
	    			sampleTotals.put(k, sampleTotals.get(k) + incompatibility);
	    		}
			}
    	}
		
		double density = Math.exp(total);
		for (Rule k : sampleTotals.keySet())
			totals.put(k, totals.get(k) + sampleTotals.get(k) * density);
	}
	
}
