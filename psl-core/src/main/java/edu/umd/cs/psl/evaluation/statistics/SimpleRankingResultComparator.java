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
import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.evaluation.statistics.filter.AtomFilter;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStore;
import edu.umd.cs.psl.model.atom.memory.MemoryAtomStore;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.util.collection.HashList;

public class SimpleRankingResultComparator implements RankingResultComparator {

	private static final Logger log = LoggerFactory.getLogger(SimpleRankingResultComparator.class);
	
	private final DatabaseAtomStoreQuery result;
	private DatabaseAtomStoreQuery baseline;
	
	private AtomFilter resultFilter = AtomFilter.NoFilter;
	private AtomFilter baselineFilter = AtomFilter.NoFilter;
	
	private RankingComparator rankCompare = RankingComparator.Kendall;
	
	public SimpleRankingResultComparator(DatabaseAtomStoreQuery outcome) {
		this.result=outcome;
		baseline = null;
	}

	@Override
	public void setRankingComparator(RankingComparator comp) {
		rankCompare = comp;
	}

	@Override
	public void setBaseline(DatabaseAtomStoreQuery baseline) {
		this.baseline = baseline;
	}
	
	@Override
	public void setBaseline(Database baseline) {
		this.baseline = DatabaseAtomStoreQuery.getIndependentInstance(baseline);
	}

	
//	@Override
//	public void setBaseline(Database baseline, boolean initializeAtomStore) {
//		AtomStore store = null;
//		if(!initializeAtomStore) {
//			try {
//				store = baseline.getAtomStore();
//			} catch (IllegalStateException e) {}
//		}
//		if (store==null) {
//			store = new MemoryAtomStore(baseline);
//			baseline.setAtomStore(store);
//		}
//		this.baseline = new DatabaseAtomStoreQuery(baseline);
//	}

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
		List<Atom> baseAtoms = new HashList<Atom>();
		Iterator<Atom> itr = baselineFilter.filter(baseline.getAtomSet(p).iterator());
		while (itr.hasNext())
			baseAtoms.add(itr.next());
		Collections.sort(baseAtoms, new AtomComparator());
		
		log.debug("Collected and sorted base atoms. Size: {}", baseAtoms.size());
		
		/* Result atoms */
		List<Atom> resultAtoms = new HashList<Atom>();
		itr = resultFilter.filter(result.getAtomSet(p).iterator());
		while (itr.hasNext())
			resultAtoms.add(itr.next());
		Collections.sort(resultAtoms, new AtomComparator());
		
		log.debug("Collected and sorted result atoms. Size: {}", resultAtoms.size());
		
		return rankCompare.getScore(baseAtoms, resultAtoms);
		
	}
	
	private class AtomComparator implements Comparator<Atom> {
		@Override
		public int compare(Atom a1, Atom a2) {
			assert a1.getNumberOfValues() == 1;
			assert a2.getNumberOfValues() == 1;
			
			if (a1.getSoftValue(0) > a1.getSoftValue(0))
				return 1;
			else if (a1.getSoftValue(0) == a2.getSoftValue(0))
				return 0;
			else
				return -1;
		}
	}
	
}
