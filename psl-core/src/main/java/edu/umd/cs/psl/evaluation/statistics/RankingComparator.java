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

import edu.umd.cs.psl.model.atom.Atom;

public enum RankingComparator {

	Kendall {
		@Override
		public double getScore(List<Atom> baseline, List<Atom> result) {
			double score = 0.0;
			int i, j;
			Iterator<Atom> baseItrI, baseItrJ;
			Atom baseAtomI, baseAtomJ;
			
			baseItrI = baseline.iterator();
			baseItrI.next();
			i = 1;
			while (baseItrI.hasNext()) {
				baseAtomI = baseItrI.next();
				i++;
				baseItrJ = baseline.iterator();
				j = 0;
				while (j < i) {
					baseAtomJ = baseItrJ.next();
					j++;
					if (result.indexOf(baseAtomJ) > result.indexOf(baseAtomI))
						score++;
				}
			}
			return 0.5 + (1 - 4 * score / (baseline.size() * (baseline.size() - 1))) / 2;
		}
	},
	
	Spearman {
		@Override
		public double getScore(List<Atom> baseline, List<Atom> result) {
			// TODO Auto-generated method stub
			return 0;
		}
	};
	
	public abstract double getScore(List<Atom> baseline, List<Atom> result);
	
}
