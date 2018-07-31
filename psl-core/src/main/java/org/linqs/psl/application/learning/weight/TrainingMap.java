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
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.IteratorUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A class that keeps tracks of the mapping between a random variable
 * atom in one database (atom manager) and an observed atom in another
 * (as well as unmapped (latent) variables).
 * Can also optionally (controlled by constructor parameters) store the mapping of
 * observed atoms to all the truth atoms that do not have an associated RVA.
 * This optional mapping only makes sense for partially observed target predicates.
 * Building this additional map may be costly.
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
	 * A mapping like |trainingMap|, but containing all truth atoms that do
	 * not have an associated RVA (but instead has an observed atom).
	 * This is optionally populated based on constructor parameters.
	 */
	private final Map<ObservedAtom, ObservedAtom> observedMap;

	/**
	 * Initializes the training map of RandomVariableAtoms ObservedAtoms.
	 * Any RandomVariableAtom from the atom manager that does not have a matching ObservedAtom
	 * in the Database are stored in the set of latent variables.
	 *
	 * @param rvAtomManager the atom manager containing the RandomVariableAtoms (any other atom types are ignored)
	 * @param observedDB the database containing matching ObservedAtoms
	 * @param fetchObservedPairs also fetch the observed atom counterparts for any truth atom that does not
	 *  have an associated RVA.
	 */
	public TrainingMap(PersistedAtomManager rvAtomManager, Database observedDB, boolean fetchObservedPairs) {
		Map<RandomVariableAtom, ObservedAtom> tempTrainingMap = new HashMap<RandomVariableAtom, ObservedAtom>(rvAtomManager.getPersistedRVAtoms().size());
		Map<ObservedAtom, ObservedAtom> tempObservedMap = new HashMap<ObservedAtom, ObservedAtom>();
		Set<RandomVariableAtom> tempLatentVariables = new HashSet<RandomVariableAtom>();

		Set<ObservedAtom> seenTruthAtoms = null;
		if (fetchObservedPairs) {
			seenTruthAtoms = new HashSet<ObservedAtom>(rvAtomManager.getPersistedRVAtoms().size());
		}

		// Go through all the atoms that were already persisted and build a mapping.
		for (RandomVariableAtom rvAtom : rvAtomManager.getPersistedRVAtoms()) {
			// Query the observed database to see if this is observed or latent.
			GroundAtom otherAtom = observedDB.getAtom(rvAtom.getPredicate(), rvAtom.getArguments());

			if (otherAtom instanceof ObservedAtom) {
				tempTrainingMap.put(rvAtom, (ObservedAtom)otherAtom);

				if (fetchObservedPairs) {
					seenTruthAtoms.add((ObservedAtom)otherAtom);
				}
			} else {
				tempLatentVariables.add(rvAtom);
			}
		}

		if (fetchObservedPairs) {
			for (StandardPredicate predicate : observedDB.getDataStore().getRegisteredPredicates()) {
				for (ObservedAtom truthAtom : observedDB.getAllGroundObservedAtoms(predicate)) {
					if (seenTruthAtoms.contains(truthAtom)) {
						continue;
					}

					GroundAtom otherAtom = null;
					try {
						otherAtom = rvAtomManager.getAtom(truthAtom.getPredicate(), truthAtom.getArguments());
					} catch (PersistedAtomManager.PersistedAccessException ex) {
						// There is no observed target atom associated with this truth one.
						continue;
					}

					if (otherAtom instanceof ObservedAtom) {
						tempObservedMap.put((ObservedAtom)otherAtom, truthAtom);
					} else {
						throw new IllegalStateException("Found a non-observed atom after we got all the RVA... was the data store changed under us?");
					}
				}
			}
		}

		// Finalize the structures.
		trainingMap = Collections.unmodifiableMap(tempTrainingMap);
		observedMap = Collections.unmodifiableMap(tempObservedMap);
		latentVariables = Collections.unmodifiableSet(tempLatentVariables);
	}

	/**
	 * Get the mapping of random to observed atoms.
	 */
	public Map<RandomVariableAtom, ObservedAtom> getTrainingMap() {
		return trainingMap;
	}

	/**
	 * Get the mapping of observed to observed atoms.
	 * Optinally populated based on constructor parameters.
	 */
	public Map<ObservedAtom, ObservedAtom> getObservedMap() {
		return observedMap;
	}

	/**
	 * Gets the latent variables seen by this manager.
	 */
	public Set<RandomVariableAtom> getLatentVariables() {
		return latentVariables;
	}

	public Iterable<GroundAtom> getTargetAtoms() {
		return getTargetAtoms(false);
	}

	/**
	 * Get all the target atoms (atoms for the RVA PAM) in one iterable.
	 */
	public Iterable<GroundAtom> getTargetAtoms(boolean includeLatent) {
		if (includeLatent) {
			return IteratorUtils.join(
					(Collection<? extends GroundAtom>)(trainingMap.keySet()),
					(Collection<? extends GroundAtom>)(observedMap.keySet()),
					(Collection<? extends GroundAtom>)(latentVariables)
			);
		} else {
			return IteratorUtils.join(
					(Collection<? extends GroundAtom>)(trainingMap.keySet()),
					(Collection<? extends GroundAtom>)(observedMap.keySet())
			);
		}
	}

	/**
	 * Get all the truth atoms in one iterable.
	 */
	public Iterable<GroundAtom> getTruthAtoms() {
		return IteratorUtils.join(
				(Collection<? extends GroundAtom>)(trainingMap.values()),
				(Collection<? extends GroundAtom>)(observedMap.values())
		);
	}

	/**
	 * Get the full mapping of target to truth atoms (RVAs and observed).
	 */
	// Casting non-static subclasses (ie Mep.Entry) can get iffy, so we just brute forced the case using Object.
	@SuppressWarnings("unchecked")
	public Iterable<Map.Entry<GroundAtom, GroundAtom>> getFullMap() {
		Iterable<Map.Entry<? extends GroundAtom, ? extends GroundAtom>> temp = IteratorUtils.join(
				(Collection<Map.Entry<? extends GroundAtom, ? extends GroundAtom>>)((Object)(trainingMap.entrySet())),
				(Collection<Map.Entry<? extends GroundAtom, ? extends GroundAtom>>)((Object)(observedMap.entrySet()))
		);

		return (Iterable<Map.Entry<GroundAtom, GroundAtom>>)((Object)temp);
	}
}
