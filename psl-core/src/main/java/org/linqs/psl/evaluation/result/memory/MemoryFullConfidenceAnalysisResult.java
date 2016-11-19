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
package org.linqs.psl.evaluation.result.memory;

import java.util.Map;

import org.linqs.psl.evaluation.result.FullConfidenceAnalysisResult;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;

import de.mathnbits.statistics.DoubleDist;

public class MemoryFullConfidenceAnalysisResult implements FullConfidenceAnalysisResult {
	
	private final Map<AtomFunctionVariable,DoubleDist> distributions;

	
	public MemoryFullConfidenceAnalysisResult(Map<AtomFunctionVariable,DoubleDist> dists) {
		distributions=dists;
	}
	
	@Override
	public Map<AtomFunctionVariable,DoubleDist> getDistribution() {
		return distributions;
	}
	
	@Override
	public double KLdivergence(AtomFunctionVariable atomvar, int noBins, FullConfidenceAnalysisResult other) {
		double kl = 0.0;
		double eps = 1e-8;
		double[] hbase = getHistogram(atomvar,noBins);
		double[] hother = other.getHistogram(atomvar, noBins);
		
		for (int i=0;i<noBins;i++) {
			if (hbase[i]<eps) continue;
			if (hother[i]>eps) kl += hbase[i]* Math.log(hbase[i]/hother[i]);
			else kl += hbase[i]* Math.log(hbase[i]/eps);
		}
		//System.out.println(kl);
		return kl;
	}
	
	@Override
	public double averageKLdivergence(Predicate p, int noBins, FullConfidenceAnalysisResult other) {
		double sumkl = 0.0;
		int count = 0;
		for (AtomFunctionVariable atomvar : distributions.keySet()) {
			Atom atom = atomvar.getAtom();
			if (!atom.getPredicate().equals(p)) continue;
			sumkl += KLdivergence(atomvar,noBins,other);
			count++;
		}
		return sumkl/count;
	}
	
	@Override
	public double[] getHistogram(AtomFunctionVariable atomvar, int noBins) {
		if (!distributions.containsKey(atomvar)) return new double[noBins];
		DoubleDist dist = distributions.get(atomvar);
		//System.out.println("Dist: " + dist.toCSV());
		//double interval = 1.0/noBins;
		double total = dist.totalCount();
		double[] histogram = new double[noBins];
		for (Double v : dist.getBins()) {
			double p = dist.getCount(v)/total;
			int index = Math.min((int)Math.floor(v*noBins),noBins-1);
			//System.out.println(v+"|"+p);
			histogram[index] += p;
		}
		
		//System.out.println(Arrays.toString(histogram));
		return histogram;
	}
	
}
