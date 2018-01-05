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

public class ContinuousPredictionComparator implements ResultComparator {
	private final Database result;
	private Database baseline;

	public ContinuousPredictionComparator(Database result) {
		this(result, null);
	}

	public ContinuousPredictionComparator(Database result, Database baseline) {
		this.result = result;
		this.baseline = baseline;
	}

	@Override
	public void setBaseline(Database db) {
		this.baseline = db;
	}

	@Override
	public void setResultFilter(AtomFilter filter) {
		throw new UnsupportedOperationException();
	}

	public ContinuousPredictionStatistics compare(StandardPredicate predicate) {
		int count = 0;
		double absoluteError = 0.0;
		double squaredError = 0.0;

		for (GroundAtom truthAtom : Queries.getAllAtoms(baseline, predicate)) {
			if (!(truthAtom instanceof ObservedAtom)) {
				continue;
			}

			GroundAtom resultAtom = result.getAtom(truthAtom.getPredicate(), truthAtom.getArguments());
			if (resultAtom instanceof ObservedAtom) {
				continue;
			}

			count++;
			absoluteError += Math.abs(truthAtom.getValue() - resultAtom.getValue());
			squaredError += Math.pow(truthAtom.getValue() - resultAtom.getValue(), 2);
		}

		return new ContinuousPredictionStatistics(count, absoluteError, squaredError);
	}
}
