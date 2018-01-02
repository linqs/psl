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
package org.linqs.psl.database.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseQuery;
import org.linqs.psl.database.ResultList;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A PersistedAtomManager that also keeps tracks of the mapping between a random variable
 * atom in one database and an observed atom in another (as well as unmapped (latent) variables).
 *
 * This AtomManager acts on top of the random variable database and only uses the
 * observed database during construction to build the map.
 * All standard rules and assumptions with PersistedAtomManagers apply.
 */
public class TrainingMapAtomManager extends PersistedAtomManager {
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
	 * Initializes the training map of {@link RandomVariableAtom RandomVariableAtoms}
	 * to {@link ObservedAtom ObservedAtoms}. Any RandomVariableAtom that does not have
	 * a matching ObservedAtom in the second Database are stored in the set of latent
	 * variables.
	 *
	 * @param rvDB the database containing the RandomVariableAtoms (any other atom types are ignored)
	 * @param observedDB the database containing matching ObservedAtoms
	 */
	public TrainingMapAtomManager(Database rvDB, Database observedDB) {
		// We will pass the random variable database to super.
		super(rvDB);

		this.trainingMap = new HashMap<RandomVariableAtom, ObservedAtom>();
		this.latentVariables = new HashSet<RandomVariableAtom>();

		// Go through all the atoms that were already persisted and build a mapping.
		for (RandomVariableAtom rvAtom : persistedCache) {
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
