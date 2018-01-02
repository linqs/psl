/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

import org.linqs.psl.database.Database;
import org.linqs.psl.database.Queries;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;

/**
 * Discretely compares all observed atoms in a baseline (truth) database against
 * all random variable atoms in a prediction (result) database.
 * All values at or above the given threshold are considered true, the rest false.
 * If the prediction database does not have an atom corresponding to an atom in the
 * truth database, it will be skipped.
 * If the corresponding atom in the prediction database is observed, then it will be skipped.
 */
public class DiscretePredictionComparator implements PredictionComparator {
	public static final double DEFAULT_THRESHOLD = 0.5;

	private final Database result;
	private Database baseline;
	private double threshold;

	public DiscretePredictionComparator(Database result) {
		this(result, null, DEFAULT_THRESHOLD);
	}

	public DiscretePredictionComparator(Database result, Database baseline, double threshold) {
		this.result = result;
		this.baseline = baseline;
		this.threshold = threshold;
	}

	public void setThreshold(double threshold) {
		this.threshold = threshold;
	}

	@Override
	public void setBaseline(Database db) {
		this.baseline = db;
	}

	@Override
	public void setResultFilter(AtomFilter af) {
		throw new UnsupportedOperationException();
	}

	@Override
	public DiscretePredictionStatistics compare(StandardPredicate predicate) {
		int tp = 0;
		int fn = 0;
		int tn = 0;
		int fp = 0;

		for (GroundAtom truthAtom : Queries.getAllAtoms(baseline, predicate)) {
			if (!(truthAtom instanceof ObservedAtom)) {
				continue;
			}

			GroundAtom resultAtom = result.getAtom(truthAtom.getPredicate(), truthAtom.getArguments());
			if (resultAtom instanceof ObservedAtom) {
				continue;
			}

			boolean expected = (truthAtom.getValue() >= threshold);
			boolean predicated = (resultAtom.getValue() >= threshold);

			if (predicated && expected) {
				tp++;
			} else if (!predicated && expected) {
				fn++;
			} else if (predicated && !expected) {
				fp++;
			} else {
				tn++;
			}
		}

		return new DiscretePredictionStatistics(tp, fp, tn, fn, threshold);
	}
}
