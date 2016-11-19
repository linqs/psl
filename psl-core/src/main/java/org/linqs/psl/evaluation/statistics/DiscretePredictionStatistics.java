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
package org.linqs.psl.evaluation.statistics;

import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.linqs.psl.model.atom.GroundAtom;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

/**
 * Statistics computed by a {@link PredictionComparator} which discretizes
 * truth values before comparing them.
 */
public class DiscretePredictionStatistics implements PredictionStatistics {
	
	public enum BinaryClass {
		NEGATIVE,
		POSITIVE
	}
	
	private final int tp;
	private final int fp;
	private final int fn;
	private final int tn;
	private final double threshold;
	private final Map<GroundAtom, Double> errors;
	private final Set<GroundAtom> correctAtoms;
	
	public DiscretePredictionStatistics(int tp, int fp, int tn, int fn,
			double threshold, Map<GroundAtom, Double> errors, Set<GroundAtom> correctAtoms) {
		this.tp = tp;
		this.fp = fp;
		this.tn = tn;
		this.fn = fn;
		this.threshold = threshold;
		this.errors=errors;
		this.correctAtoms = correctAtoms;
	}
	
	public double getThreshold() {
		return threshold;
	}
	
	public Map<GroundAtom, Double> getErrors() {
		return Collections.unmodifiableMap(errors);
	}
	
	public Set<GroundAtom> getCorrectAtoms() {
		return Collections.unmodifiableSet(correctAtoms);
	}
	
	public Iterable<Map.Entry<GroundAtom, Double>> getFalsePositives() {
		return Iterables.filter(errors.entrySet(), new Predicate<Entry<GroundAtom,Double>>(){

			@Override
			public boolean apply(Entry<GroundAtom, Double> e) {
				if (e.getValue() > 0.0)
					return true;
				else
					return false;
			}
		});
	}
	
	public Iterable<Map.Entry<GroundAtom, Double>> getFalseNegatives() {
		return Iterables.filter(errors.entrySet(), new Predicate<Entry<GroundAtom,Double>>(){

			@Override
			public boolean apply(Entry<GroundAtom, Double> e) {
				if (e.getValue() < 0.0)
					return true;
				else
					return false;
			}
		});
	}
	
	public double getPrecision(BinaryClass c) {
		if (c == BinaryClass.NEGATIVE) {
			double n = tn + fn;
			if (n == 0.0)
				return 1.0;
			return tn/n;
		}
		else {
			double p = tp + fp;
			if (p == 0.0)
				return 1.0;
			return tp/p;
		}
	}
	
	public double getRecall(BinaryClass c) {
		if (c == BinaryClass.NEGATIVE) {
			double n = tn + fp;
			if (n == 0.0)
				return 1.0;
			return tn/(tn+fp);
		}
		else {
			double p = tp + fn;
			if (p == 0.0)
				return 1.0;
			return tp/(p);
		}
	}
	
	public double getF1(BinaryClass c) {
		double prec = this.getPrecision(c);
		double rec = this.getRecall(c);
		double sum = prec + rec;
		if (sum == 0.0)
			return 0.0;
		return 2*(prec*rec)/sum;
	}
	
	public double getAccuracy() {
		int numAtoms = getNumAtoms();
		if (numAtoms == 0)
			return 0;
		return (tp + tn) / (double)getNumAtoms();
	}

	@Override
	public double getError() {
		return fp + fn;
	}

	@Override
	public int getNumAtoms() {
		return tp + fp + tn + fn;
	}
	
}
