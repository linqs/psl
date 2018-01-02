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
import org.linqs.psl.evaluation.statistics.filter.AtomFilter;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class RankingComparator implements ResultComparator {
	public static final double DEFAULT_THRESHOLD = 0.5;

	private static final Logger log = LoggerFactory.getLogger(RankingComparator.class);

	private final Database result;
	private Database baseline;
	private double threshold;

	public RankingComparator(Database result, Database baseline, double threshold) {
		this.result = result;
		this.baseline = baseline;
		this.threshold = threshold;
	}

	public RankingComparator(Database result) {
		this(result, null, DEFAULT_THRESHOLD);
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

	public RankingScore compare(StandardPredicate predicate) {
		// Get all the result atoms.
		// Note that we can't have duplicate ground atoms.
		List<GroundAtom> resultAtoms = Queries.getAllAtoms(result, predicate);
		Collections.sort(resultAtoms, new AtomComparator());
		log.trace("Collected and sorted result atoms. Size: {}", resultAtoms.size());

		// Get all the baseline atoms.
		List<GroundAtom> baselineAtoms = Queries.getAllAtoms(baseline, predicate);
		Collections.sort(baselineAtoms, new AtomComparator());
		log.trace("Collected and sorted base atoms. Size: {}", baselineAtoms.size());

		return new RankingScore(baselineAtoms, resultAtoms, threshold);
	}

	private class AtomComparator implements Comparator<GroundAtom> {
		@Override
		public int compare(GroundAtom a1, GroundAtom a2) {
			if (a1.getValue() < a2.getValue()) {
				return 1;
			} else if (a1.getValue() == a2.getValue()) {
				return a1.toString().compareTo(a2.toString());
			} else {
				return -1;
			}
		}
	}
}
