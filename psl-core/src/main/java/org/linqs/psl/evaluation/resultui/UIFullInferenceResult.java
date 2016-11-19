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
package org.linqs.psl.evaluation.resultui;

import java.util.Iterator;

import org.linqs.psl.database.Database;
import org.linqs.psl.evaluation.result.FullInferenceResult;
import org.linqs.psl.evaluation.resultui.printer.AtomPrintStream;
import org.linqs.psl.evaluation.resultui.printer.DefaultAtomPrintStream;
import org.linqs.psl.evaluation.statistics.DiscretePredictionComparator;
import org.linqs.psl.evaluation.statistics.ResultComparator;
import org.linqs.psl.model.atom.AtomCache;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;

public class UIFullInferenceResult {
	
	private final Database db;
	private final FullInferenceResult statistics;
	
	public UIFullInferenceResult(Database db, FullInferenceResult stats) {
		this.db = db;
		statistics = stats;
	}
	
	public void printAtoms(Predicate p) {
		printAtoms(p,new DefaultAtomPrintStream());
	}
	
	public void printAtoms(Predicate p, AtomPrintStream printer) {
		AtomCache cache = db.getAtomCache();
		for (Iterator<GroundAtom> itr = cache.getCachedAtoms(p).iterator(); itr.hasNext();) {
			GroundAtom atom = itr.next();
			printer.printAtom(atom);
		}
		printer.close();
	}
	
	public double getLogProbability() {
		return statistics.getTotalWeightedIncompatibility();
	}
	
	public double getInfeasibilityNorm() {
		return statistics.getInfeasibilityNorm();
	}

	public int getNumGroundAtoms() {
		return statistics.getNumGroundAtoms();
	}

	public int getNumGroundEvidence() {
		return statistics.getNumGroundEvidence();
	}
	
	public ResultComparator compareResults() {
		return new DiscretePredictionComparator(db);
	}
	
}
