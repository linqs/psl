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
package org.linqs.psl.evaluation.debug;

import java.text.DecimalFormat;

import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;

public class AtomPrinter {
	
	private static final DecimalFormat valueFormatter = new DecimalFormat("#.##");

	public static final String atomDetails(Atom atom) {
		return atomDetails(atom,true,true);
	}
	
	public static final String atomDetails(Atom atom, boolean printType, boolean printConfidence) {
		StringBuilder s = new StringBuilder();
		s.append(atom.toString());
		if (atom instanceof GroundAtom) {
			GroundAtom groundAtom = (GroundAtom) atom;
			s.append(" Truth=[");
			s.append(valueFormatter.format(groundAtom.getValue()));
			s.append("]");
			if (printType) {
				String type;
				if (groundAtom instanceof ObservedAtom)
					type = "Observed";
				else if (groundAtom instanceof RandomVariableAtom)
					type = "RV";
				else
					throw new IllegalArgumentException("Cannot print type of GroundAtom: " + groundAtom);
				s.append(" Type=").append(type);
			}
			if (printConfidence) {
				s.append(" Conf.=[");
				s.append(valueFormatter.format(groundAtom.getConfidenceValue()));
				s.append("]");
			}
		}
		return s.toString();
	}
	
	
}
