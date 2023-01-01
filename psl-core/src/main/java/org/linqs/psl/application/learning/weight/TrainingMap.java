/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A class that keeps tracks of the mapping between an RVA in one database (atom manager)
 * and an observed atom in another (as well as unmapped (latent) variables).
 * Any unobserved atoms in the truth database will be ignored.
 *
 * There are six situations that can arise when comparing the atoms from both databases.
 * Here we will enumerate all the possibilities.
 * The first item of the tuple represents the target database (atom manager),
 * while the second item represents the truth database.
 * The right hand side will say where this pair will be recorded.
 *   (unobserved, observed) - Standard Label Map
 *   (unobserved, not existent) - Latent Variables
 *   (observed, observed) - Observed Map
 *   (observed, not existent) - Missing Labels
 *   (not existent, observed) - Missing Targets
 *   (not existent, not existent) - Ignored
 * Missing targts will always log a warning.
 */
public class TrainingMap {
    private static final Logger log = Logger.getLogger(TrainingMap.class);

    /**
     * The mapping between an RVA and its observed truth atom.
     */
    private Map<RandomVariableAtom, ObservedAtom> labelMap;

    /**
     * A mapping like the label mapping, but only contains target atoms that are observed.
     */
    private Map<ObservedAtom, ObservedAtom> observedMap;

    /**
     * The set of atoms that have no associated observed truth atom.
     */
    private List<RandomVariableAtom> latentVariables;

    /**
     * Observed targets that do not have an associated observed truth atom.
     */
    private List<ObservedAtom> missingLabels;

    /**
     * Observed truth atoms that do not have an associated target atom.
     */
    private List<ObservedAtom> missingTargets;

    /**
     * Initializes the training map of RandomVariableAtoms ObservedAtoms.
     *
     * @param targetDatabase the database containing the RandomVariableAtoms (any other atom types are ignored)
     * @param truthDatabase the database containing matching ObservedAtoms
     */
    public TrainingMap(Database targetDatabase, Database truthDatabase) {
        labelMap = new HashMap<RandomVariableAtom, ObservedAtom>(targetDatabase.getAtomStore().size());
        observedMap = new HashMap<ObservedAtom, ObservedAtom>();
        latentVariables = new ArrayList<RandomVariableAtom>();
        missingLabels = new ArrayList<ObservedAtom>();
        missingTargets = new ArrayList<ObservedAtom>();

        Set<GroundAtom> seenTruthAtoms = new HashSet<GroundAtom>();

        for (GroundAtom targetAtom : targetDatabase.getAtomStore()) {
            if (targetAtom.getPredicate() instanceof FunctionalPredicate) {
                continue;
            }

            // Note that hasAtom() will not return true for an unmanaged atom (except pre-cached functional predicates).
            GroundAtom truthAtom = null;
            if (truthDatabase.getAtomStore().hasAtom(targetAtom.getPredicate(), targetAtom.getArguments())) {
                truthAtom = truthDatabase.getAtomStore().getAtom(targetAtom.getPredicate(), targetAtom.getArguments());
            }

            // Skip any truth atom that is not observed.
            if (truthAtom != null && !(truthAtom instanceof ObservedAtom)) {
                continue;
            }

            if (targetAtom instanceof RandomVariableAtom) {
                if (truthAtom == null) {
                    latentVariables.add((RandomVariableAtom)targetAtom);
                } else {
                    seenTruthAtoms.add((ObservedAtom)truthAtom);
                    labelMap.put((RandomVariableAtom)targetAtom, (ObservedAtom)truthAtom);
                }
            } else {
                if (truthAtom == null) {
                    missingLabels.add((ObservedAtom)targetAtom);
                } else {
                    seenTruthAtoms.add((ObservedAtom)truthAtom);
                    observedMap.put((ObservedAtom)targetAtom, (ObservedAtom)truthAtom);
                }
            }
        }

        for (GroundAtom truthAtom : truthDatabase.getAtomStore()) {
            if (!(truthAtom instanceof ObservedAtom) || seenTruthAtoms.contains(truthAtom)) {
                continue;
            }

            boolean hasAtom = targetDatabase.getAtomStore().hasAtom(truthAtom.getPredicate(), truthAtom.getArguments());
            if (hasAtom) {
                // This shouldn't be possible (since we already iterated through the target atoms).
                // This means that the target is not cached.
                throw new IllegalStateException("Un-persisted target atom: " + truthAtom);
            }

            missingTargets.add((ObservedAtom)truthAtom);
        }

        if (missingTargets.size() > 0) {
            log.warn("Found {} missing targets (truth atoms without a matching target). Example: {}.",
                    missingTargets.size(), missingTargets.get(0));
        }
    }

    /**
     * Get the mapping of unobserved targets to truth atoms.
     */
    public Map<RandomVariableAtom, ObservedAtom> getLabelMap() {
        return Collections.unmodifiableMap(labelMap);
    }

    /**
     * Get the mapping of observed targets to truth atoms.
     */
    public Map<ObservedAtom, ObservedAtom> getObservedMap() {
        return Collections.unmodifiableMap(observedMap);
    }

    /**
     * Gets the latent variables (unobserved targets without a truth atom).
     */
    public List<RandomVariableAtom> getLatentVariables() {
        return Collections.unmodifiableList(latentVariables);
    }

    /**
     * Gets observed targets that do not have an associated observed truth atom.
     */
    public List<ObservedAtom> getMissingLabels() {
        return Collections.unmodifiableList(missingLabels);
    }

    /**
     * Gets observed truth atoms that do not have an associated target atom.
     */
    public List<ObservedAtom> getMissingTargets() {
        return Collections.unmodifiableList(missingTargets);
    }

    /**
     * Get all the predictions (unobserved targets).
     * This combines atoms from the label map and latent variables.
     */
    public Iterable<RandomVariableAtom> getAllPredictions() {
        return IteratorUtils.join(labelMap.keySet(), latentVariables);
    }

    /**
     * Get all atoms that appeared in the target database.
     * Note that this will also include observed atoms from the target database.
     */
    public Iterable<GroundAtom> getAllTargets() {
        return IteratorUtils.join(labelMap.keySet(), observedMap.keySet(), latentVariables, missingLabels);
    }

    /**
     * Get all atoms that appeared in the truth database.
     * Note that this will also include atoms that map to missing or observed targets.
     */
    public Iterable<GroundAtom> getAllTruths() {
        return IteratorUtils.join(labelMap.values(), observedMap.values(), missingTargets);
    }

    /**
     * Add a random variable target atom to the trainingMap.
     */
    public void addRandomVariableTargetAtom(RandomVariableAtom atom) {
        int missingTargetIndex = missingTargets.indexOf(atom);
        if (missingTargetIndex != -1) {
            ObservedAtom observedAtom = missingTargets.remove(missingTargetIndex);
            labelMap.put(atom, observedAtom);
        } else {
            int latentVariableIndex = latentVariables.indexOf(atom);
            if (latentVariableIndex == -1) {
                latentVariables.add(atom);
            } else {
                latentVariables.set(latentVariableIndex, atom);
            }
        }
    }

    /**
     * Delete a random variable atom from trainingMap.
     */
    public void deleteAtom(GroundAtom atom) {
        if (atom instanceof RandomVariableAtom) {
            labelMap.remove((RandomVariableAtom)atom);
            latentVariables.remove((RandomVariableAtom)atom);
        } else {
            observedMap.remove((ObservedAtom)atom);
            missingLabels.remove((ObservedAtom)atom);
            missingTargets.remove((ObservedAtom)atom);
        }
    }

    /**
     * Get the full mapping of target to truth atoms (unobserved and observed).
     */
    // Casting non-static subclasses (ie Mep.Entry) can get iffy, so we just brute forced the cast using Object.
    @SuppressWarnings("unchecked")
    public Iterable<Map.Entry<GroundAtom, GroundAtom>> getFullMap() {
        Iterable<Map.Entry<? extends GroundAtom, ? extends GroundAtom>> temp = IteratorUtils.join(
                (Collection<Map.Entry<? extends GroundAtom, ? extends GroundAtom>>)((Object)(labelMap.entrySet())),
                (Collection<Map.Entry<? extends GroundAtom, ? extends GroundAtom>>)((Object)(observedMap.entrySet()))
        );

        return (Iterable<Map.Entry<GroundAtom, GroundAtom>>)((Object)temp);
    }

    @Override
    public String toString() {
        return String.format(
                "Training Map -- Label Map: %d, Observed Map: %d, Latent Variables: %d, Missing Labels: %d, Missing Targets: %d",
                labelMap.size(),
                observedMap.size(),
                latentVariables.size(),
                missingLabels.size(),
                missingTargets.size());
    }
}
