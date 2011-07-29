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
package edu.umd.cs.psl.evaluation.resultui;

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.evaluation.debug.AtomPrinter;
import edu.umd.cs.psl.evaluation.result.FullInferenceResult;
import edu.umd.cs.psl.evaluation.resultui.printer.AtomPrintStream;
import edu.umd.cs.psl.evaluation.resultui.printer.DefaultAtomPrintStream;
import edu.umd.cs.psl.evaluation.statistics.ResultComparator;
import edu.umd.cs.psl.evaluation.statistics.SimpleResultComparator;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.predicate.Predicate;

public class UIFullInferenceResult {
	
	private final DatabaseAtomStoreQuery dbproxy;
	private final FullInferenceResult statistics;
	
	public UIFullInferenceResult(DatabaseAtomStoreQuery db, FullInferenceResult stats) {
		dbproxy = db;
		statistics = stats;
	}
	
	public void printAtoms(Predicate p) {
		printAtoms(p,new DefaultAtomPrintStream());
	}
	
	public void printAtoms(Predicate p, boolean onlyNonDefault) {
		printAtoms(p,new DefaultAtomPrintStream(),onlyNonDefault);	
	}
	
	public void printAtoms(Predicate p, AtomPrintStream printer) {
		printAtoms(p,printer,true);
	}
	
	public void printAtoms(Predicate p, AtomPrintStream printer, boolean onlyNonDefault) {
		ResultList res = dbproxy.getAtoms(p);
		for (int i=0;i<res.size();i++) {
			Atom atom = dbproxy.getConsideredAtom(p, res.get(i));
			if (atom!=null) {
				if (!onlyNonDefault || atom.hasNonDefaultValues())
					printer.printAtom(atom);
			}
		}
		printer.close();
	}
	
	public double getLogProbability() {
		return statistics.getTotalIncompatibility();
	}

	public int getNumGroundAtoms() {
		return statistics.getNumGroundAtoms();
	}

	public int getNumGroundEvidence() {
		return statistics.getNumGroundEvidence();
	}
	
	public ResultComparator compareResults() {
		return new SimpleResultComparator(dbproxy);
	}
	
}
