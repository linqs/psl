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

import java.util.Iterator;

import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.util.database.Queries;

public class ContinuousPredictionComparator implements ResultComparator {

	private final Database result;
	private Database baseline;
	private AtomFilter resultFilter = AtomFilter.NoFilter;
	private Metric metric;

	public ContinuousPredictionComparator(Database result) {
		this.result=result;
		baseline = null;
		resultFilter = AtomFilter.NoFilter;
		metric = Metric.MSE;
	}


	@Override
	public void setBaseline(Database db) {
		this.baseline = db;
	}

	@Override
	public void setResultFilter(AtomFilter af) {
		resultFilter = af;
	}

	public void setMetric(Metric metric) {
		this.metric = metric;
	}

	/**
	 * For now, assumes the results DB has grounded all relevant instances of predicate p. 
	 * I.e., currently this does not check for missed instances in the baseline DB
	 * @param p
	 * @return
	 */
	public double compare(Predicate p) {
		double score = 0.0;
		int total = 0;
		/* Result atoms */
		Iterator<GroundAtom> itr = resultFilter.filter(Queries.getAllAtoms(result, p).iterator());
		while (itr.hasNext()) {
			GroundAtom pred = itr.next();
			GroundAtom label = baseline.getAtom(p, pred.getArguments());
			score += accumulate(label.getValue() - pred.getValue());
			total++;
		}

		return score / total;
	}

	private double accumulate(double difference) {
		double value;
		switch (metric) {
		case MSE: value = difference * difference;
		break;
		case MAE: value = Math.abs(difference);
		break;
		default: value = 0.0;
		break;
		}
		return value;
	}

	public enum Metric {
		MSE, MAE;
	}



}
