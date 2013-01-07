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

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.evaluation.statistics.filter.AtomFilter;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.util.collection.HashList;

public class SimpleRankingResultComparator implements RankingResultComparator {

	private static final Logger log = LoggerFactory.getLogger(SimpleRankingResultComparator.class);
	
	private final Database result;
	private Database baseline;
	
	private AtomFilter resultFilter = AtomFilter.NoFilter;
	private AtomFilter baselineFilter = AtomFilter.NoFilter;
	
	private RankingComparator rankCompare = RankingComparator.Kendall;
	
	public SimpleRankingResultComparator(Database result) {
		this.result=result;
		baseline = null;
	}

	@Override
	public void setRankingComparator(RankingComparator comp) {
		rankCompare = comp;
	}

	@Override
	public void setBaseline(Database db) {
		this.baseline = db;
	}

	@Override
	public void setResultFilter(AtomFilter af) {
		resultFilter = af;
	}

	@Override
	public void setBaselineFilter(AtomFilter af) {
		baselineFilter = af;
	}
	
	@Override
	public double compare(Predicate p) {
		/* Base atoms */
		List<GroundAtom> baseAtoms = new HashList<GroundAtom>();
		Iterator<Atom> itr = baselineFilter.filter(baseline.getAtomSet(p).iterator());
		while (itr.hasNext())
			baseAtoms.add(itr.next());
		Collections.sort(baseAtoms, new AtomComparator());
		
		log.debug("Collected and sorted base atoms. Size: {}", baseAtoms.size());
		
		/* Result atoms */
		List<GroundAtom> resultAtoms = new HashList<GroundAtom>();
		itr = resultFilter.filter(result.getAtomSet(p).iterator());
		while (itr.hasNext())
			resultAtoms.add(itr.next());
		Collections.sort(resultAtoms, new AtomComparator());
		
		log.debug("Collected and sorted result atoms. Size: {}", resultAtoms.size());
		
		return rankCompare.getScore(baseAtoms, resultAtoms);
		
	}
	
	private class AtomComparator implements Comparator<GroundAtom> {
		@Override
		public int compare(GroundAtom a1, GroundAtom a2) {
			
			if (a1.getValue() > a1.getValue())
				return 1;
			else if (a1.getValue() == a2.getValue())
				return 0;
			else
				return -1;
		}
	}
	
}
