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
package org.linqs.psl.evaluation.resultui;

import java.text.DecimalFormat;
import java.util.Map;

import org.linqs.psl.evaluation.result.FullConfidenceAnalysisResult;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;

import de.mathnbits.statistics.DoubleDist;

public class UIFullConfidenceAnalysisResult implements FullConfidenceAnalysisResult {
	
	private final static double defaultMeanThreshold = 0.1;
	
	private final FullConfidenceAnalysisResult result;	
	
	public UIFullConfidenceAnalysisResult(FullConfidenceAnalysisResult stats) {
		result = stats;
	}
	
	@Override
	public double KLdivergence(AtomFunctionVariable atomvar, int noBins,
			FullConfidenceAnalysisResult other) {
		return result.KLdivergence(atomvar, noBins, other);
	}


	@Override
	public double averageKLdivergence(Predicate p, int noBins,
			FullConfidenceAnalysisResult other) {
		return result.averageKLdivergence(p, noBins, other);
	}


	@Override
	public Map<AtomFunctionVariable, DoubleDist> getDistribution() {
		return result.getDistribution();
	}


	@Override
	public double[] getHistogram(AtomFunctionVariable atomvar, int noBins) {
		return result.getHistogram(atomvar, noBins);
	}
	
	public String toString(Predicate p, double meanThreshold) {
		StringBuilder s = new StringBuilder();
		Map<AtomFunctionVariable,DoubleDist> marginalDistribution = result.getDistribution();
		for (Map.Entry<AtomFunctionVariable, DoubleDist> entry : marginalDistribution.entrySet()) {
			if (!entry.getKey().getAtom().getPredicate().equals(p)) continue;
			DoubleDist d = entry.getValue();
			if (d.mean()>=meanThreshold) {
				s.append(entry.getKey().toString()).append(": ");
				s.append("[").append(d.mean()).append(" | ").append(d.stdDev()).append("]");
				s.append("\n");
			}
		}
		return s.toString();
	}
	
	public String drawDistribution(AtomFunctionVariable atomvar, int noBins, int height) {
		StringBuilder ret = new StringBuilder();
		double[] histogram = getHistogram(atomvar,noBins);
		for (int h=(height-1); h>=0; h--) {
			for (int b=0; b<noBins; b++) {
				double offset = (histogram[b]-((double)h/height))*height;
				if (offset>0.5) ret.append('*');
				else if (offset>0.01) ret.append('+');
//				else if (offset>0.25) ret.append('=');
//				else if (offset>0.01) ret.append('-');
				else if (height==0 && offset>0.0) ret.append('.');
				else ret.append(' ');
			}
			ret.append("\n");
		}
		for (int b=0; b<noBins; b++) ret.append('-');
		ret.append("\n");
		return ret.toString();
	}
	
	public String drawDistributions(Predicate p, int noBins, int height) {
		StringBuilder ret = new StringBuilder();
		for (AtomFunctionVariable atomvar : result.getDistribution().keySet()) {
			if (!atomvar.getAtom().getPredicate().equals(p)) continue;
			ret.append(atomvar).append("\n");
			ret.append(drawDistribution(atomvar,noBins,height));
		}
		return ret.toString();
	}
	
	public void printDistributions(Predicate p) {
		System.out.println(drawDistributions(p,10,5));
	}
	
	public String histogramString(AtomFunctionVariable atomvar, int noBins, int noDigits) {
		double[] histogram = getHistogram(atomvar,noBins);
		StringBuilder ret = new StringBuilder();
		String format ="0.";
		String empty = "  ";
		for (int i=0;i<noDigits;i++) { format += "0"; empty+= " ";}
        DecimalFormat df = new DecimalFormat(format);
        ret.append("[");
		for (int i=0;i<noBins;i++) {
			if (histogram[i]>0.0) ret.append(df.format(histogram[i]));
			else ret.append(empty);
			if (i<noBins-1) ret.append("  ");
		}
        ret.append(" ]\n");
		return ret.toString();
	}
	
	public String histogramStrings(Predicate p, int noBins) {
		StringBuilder ret = new StringBuilder();
		for (AtomFunctionVariable atomvar : result.getDistribution().keySet()) {
			if (!atomvar.getAtom().getPredicate().equals(p)) continue;
			ret.append(atomvar).append("\t:");
			ret.append(histogramString(atomvar,noBins,2));
		}
		return ret.toString();
	}

	public void printHistograms(Predicate p) {
		System.out.println(histogramStrings(p,10));
	}
	
	public String toString(Predicate p) {
		return toString(p,defaultMeanThreshold);
	}

	public void printAtoms(Predicate p) {
		System.out.println(toString(p));
	}
	
	public void printAtoms(Predicate p, double threshold) {
		System.out.println(toString(p,threshold));
	}
	
}
