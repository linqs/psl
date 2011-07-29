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
package edu.umd.cs.psl.application.inference.partitioned;

import java.util.HashSet;
import java.util.Set;

import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.reasoner.Reasoner;

public class ApplicationPartition {

	private final Reasoner reasoner;
	private final Set<Atom> atoms;
	
	private int noProbabilisticEvidence;
	private int noConstraintEvidence;
	
	public ApplicationPartition(Reasoner reasoner, Set<Atom> atoms) {
		this.reasoner = reasoner;
		this.atoms = atoms;
		noProbabilisticEvidence=0;
		noConstraintEvidence=0;
	}
	
	public ApplicationPartition(Reasoner reasoner) {
		this(reasoner,new HashSet<Atom>());
	}
	
	public boolean addAtom(Atom atom) {
		return atoms.add(atom);
	}
	
	public void addEvidence(GroundKernel evidence) {
		reasoner.addGroundKernel(evidence);
		if (evidence instanceof GroundCompatibilityKernel) noProbabilisticEvidence++;
		else if (evidence instanceof GroundConstraintKernel) noConstraintEvidence++;
		else throw new IllegalArgumentException("Unrecognized type of evidence added to this partition: " + evidence);
	}

	public Reasoner getReasoner() {
		return reasoner;
	}

	public Set<Atom> getAtoms() {
		return atoms;
	}

	public int getNoProbabilisticEvidence() {
		return noProbabilisticEvidence;
	}

	public int getNoConstraintEvidence() {
		return noConstraintEvidence;
	}

	public int getNoAtoms() {
		return atoms.size();
	}
	
	
	
}
