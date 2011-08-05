/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.evaluation.statistics;

import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;

import edu.umd.cs.psl.evaluation.statistics.ResultComparison.BinaryClass;
import edu.umd.cs.psl.model.atom.Atom;

public class SimpleResultComparison implements ResultComparison {

	private final Map<Atom,Double> errors;
	private final Set<Atom> correctAtoms;
	private final double tp;
	private final double fp;
	private final double fn;
	private final double tn;
	private final int noBaseAtoms;

	
	public SimpleResultComparison(Map<Atom,Double> errors, Set<Atom> correctAtoms, int tp, int fp, int tn, int fn, int noBase) {
		this.errors=errors;
		this.tp = (double) tp;
		this.fp = (double) fp;
		this.tn = (double) tn;
		this.fn = (double) fn;
		this.correctAtoms = correctAtoms;
		this.noBaseAtoms = noBase;
	}
	
	@Override
	public Iterable<Entry<Atom, Double>> getErrors() {
		return errors.entrySet();
	}
	
	public Iterable<Atom> getCorrectAtoms() {
		return correctAtoms;
	}

	public int getNoBaseAtoms() {
		return noBaseAtoms;
	}
	
	@Override
	public Iterable<Entry<Atom, Double>> getFalseNegatives() {
		return Iterables.filter(errors.entrySet(), new Predicate<Entry<Atom,Double>>(){

			@Override
			public boolean apply(Entry<Atom, Double> e) {
				if (e.getValue()<0.0) return true;
				else return false;
			}
		});
	}

	@Override
	public Iterable<Entry<Atom, Double>> getFalsePositives() {
		return Iterables.filter(errors.entrySet(), new Predicate<Entry<Atom,Double>>(){

			@Override
			public boolean apply(Entry<Atom, Double> e) {
				if (e.getValue()>0.0) return true;
				else return false;
			}
		});
	}
	
	public double getPrecision(BinaryClass c) {
		if (c == BinaryClass.NEGATIVE) {
			double n = tn + fn;
			if (n == 0.0)
				return 0.0;
			return tn/n;
		}
		else {
			double p = tp + fp;
			if (p == 0.0)
				return 0.0;
			return tp/p;
		}
	}
	
	public double getRecall(BinaryClass c) {
		if (c == BinaryClass.NEGATIVE) {
			double n = tn + fp;
			if (n == 0.0)
				return 0.0;
			return tn/(tn+fp);
		}
		else {
			double p = tp + fn;
			if (p == 0.0)
				return 0.0;
			return tp/(tp+fn);
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
		if (noBaseAtoms == 0)
			return 0;
		return (tp + tn) / (tp + fp + tn + fn);
	}
	
}
