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

import java.util.Iterator;
import java.util.List;

import edu.umd.cs.psl.model.atom.GroundAtom;

/**
 * Methods for scoring a ranking of Atoms against a given ranking.
 */
public enum RankingScore {

	/**
	 * Returns the fraction of pairs in the result that are ordered differently
	 * in the baseline.
	 */
	Kendall {
		@Override
		public double getScore(List<GroundAtom> expected, List<GroundAtom> actual) {
			double score = 0.0;
			int i, j;
			Iterator<GroundAtom> baseItrI, baseItrJ;
			GroundAtom baseAtomI, baseAtomJ;
			
			baseItrI = expected.iterator();
			baseItrI.next();
			i = 1;
			while (baseItrI.hasNext()) {
				baseAtomI = baseItrI.next();
				i++;
				baseItrJ = expected.iterator();
				j = 0;
				while (j < i) {
					baseAtomJ = baseItrJ.next();
					j++;
					if (actual.indexOf(baseAtomJ) > actual.indexOf(baseAtomI))
						score++;
				}
			}
			return 0.5 + (1 - 4 * score / (expected.size() * (expected.size() - 1))) / 2;
		}
	};
	
	/**
	 * Scores a ranking of Atoms given an expected ranking
	 * 
	 * @param expected  the expected ranking
	 * @param actual  the actual ranking
	 * @return the actual ranking's score relative to the expected ranking
	 */
	public abstract double getScore(List<GroundAtom> expected, List<GroundAtom> actual);
	
}
