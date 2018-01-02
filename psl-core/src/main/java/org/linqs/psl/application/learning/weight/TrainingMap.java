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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A class that keeps tracks of the mapping between a random variable
 * atom in one database (atom manager) and an observed atom in another
 * (as well as unmapped (latent) variables).
 */
public class TrainingMap {
	/**
	 * The mapping between an atom and its observed truth value.
	 * We are actually trading away some memory in favor of maintainability here
	 * since we have a map with persisted atoms as the key as well as a set of persisted atoms
	 * in the parent class.
	 */
	private final Map<RandomVariableAtom, ObservedAtom> trainingMap;

	/**
	 * The set of atoms that have no backing of an observed truth value.
	 */
	private final Set<RandomVariableAtom> latentVariables;

	/**
	 * Initializes the training map of RandomVariableAtoms ObservedAtoms.
	 * Any RandomVariableAtom from the atom manager that does not have a matching ObservedAtom
	 * in the Database are stored in the set of latent variables.
	 *
	 * @param rvAtomManager the atom manager containing the RandomVariableAtoms (any other atom types are ignored)
	 * @param observedDB the database containing matching ObservedAtoms
	 */
	public TrainingMap(PersistedAtomManager rvAtomManager, Database observedDB) {
		this.trainingMap = new HashMap<RandomVariableAtom, ObservedAtom>();
		this.latentVariables = new HashSet<RandomVariableAtom>();

		// Go through all the atoms that were already persisted and build a mapping.
		for (RandomVariableAtom rvAtom : rvAtomManager.getPersistedRVAtoms()) {
			// Query the observed database to see if this is observed or latent.
			GroundAtom otherAtom = observedDB.getAtom(rvAtom.getPredicate(), rvAtom.getArguments());

			if (otherAtom instanceof ObservedAtom) {
				trainingMap.put(rvAtom, (ObservedAtom)otherAtom);
			} else {
				latentVariables.add(rvAtom);
			}
		}
	}

	/**
	 * Get the mapping of random to observed atoms.
	 */
	public Map<RandomVariableAtom, ObservedAtom> getTrainingMap() {
		return Collections.unmodifiableMap(trainingMap);
	}

	/**
	 * Gets the latent variables seen by this manager.
	 */
	public Set<RandomVariableAtom> getLatentVariables() {
		return Collections.unmodifiableSet(latentVariables);
	}
}
