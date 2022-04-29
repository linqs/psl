/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.PartialGrounding;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * A persisted atom manager that will keep track of atoms that it returns, but that
 * don't actually exist (lazy atoms).
 * If activateAtoms() is called, then all lazy atoms above the activation threshold
 * (Options.LAM_ACTIVATION_THRESHOLD) will be instantiated as real atoms.
 */
public class LazyAtomManager extends PersistedAtomManager {
    /**
     * All the ground atoms that have been seen, but not instantiated.
     */
    private final Set<RandomVariableAtom> lazyAtoms;
    private final double activation;

    public LazyAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("LazyAtomManagers require RDBMSDatabase.");
        }

        lazyAtoms = new HashSet<RandomVariableAtom>();
        activation = Options.LAM_ACTIVATION_THRESHOLD.getDouble();
    }

    @Override
    public synchronized GroundAtom getAtom(Predicate predicate, Constant... arguments) {
        GroundAtom atom = db.getAtom(predicate, arguments);
        if (!(atom instanceof RandomVariableAtom)) {
            return atom;
        }
        RandomVariableAtom rvAtom = (RandomVariableAtom)atom;

        // If this atom has not been persisted, it is lazy.
        if (!rvAtom.getPersisted()) {
            lazyAtoms.add(rvAtom);
        }

        return rvAtom;
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // LazyAtomManger does not have access exceptions.
    }

    public Set<RandomVariableAtom> getLazyAtoms() {
        return Collections.unmodifiableSet(lazyAtoms);
    }

    /**
     * Compute the number of lazy atoms that can be activated at this moment.
     */
    public int countActivatableAtoms() {
        int count = 0;
        for (RandomVariableAtom atom : lazyAtoms) {
            if (atom.getValue() >= activation) {
                count++;
            }
        }
        return count;
    }

    /**
     * Activate any lazy atoms above the threshold.
     * @param rules the potential rules to look for lazy atoms in.
     * @return the number of lazy atoms instantiated.
     */
    public int activateAtoms(List<Rule> rules, GroundRuleStore groundRuleStore) {
        if (lazyAtoms.size() == 0) {
            return 0;
        }

        Set<RandomVariableAtom> toActivate = new HashSet<RandomVariableAtom>();

        Iterator<RandomVariableAtom> lazyAtomIterator = lazyAtoms.iterator();
        while (lazyAtomIterator.hasNext()) {
            RandomVariableAtom atom = lazyAtomIterator.next();

            if (atom.getValue() >= activation) {
                toActivate.add(atom);
                lazyAtomIterator.remove();
            }
        }

        activate(toActivate, rules, groundRuleStore);
        return toActivate.size();
    }

    /**
     * Activate a specific set of lazy atoms.
     * Any passed in atom that is not part of this manager's lazy atom set will be ignored.
     * The caller must be sure that these given atoms came from the same database managed by this AtomManager.
     * @return the number of lazy atoms instantiated.
     */
    public int activateAtoms(Set<RandomVariableAtom> atoms, List<Rule> rules, GroundRuleStore groundRuleStore) {
        // Ensure that all the atoms are valid.
        Iterator<RandomVariableAtom> atomIterator = atoms.iterator();
        while (atomIterator.hasNext()) {
            RandomVariableAtom atom = atomIterator.next();

            // Remove atoms that are not lazy.
            if (!lazyAtoms.contains(atom)) {
                atomIterator.remove();
            }
        }

        activate(atoms, rules, groundRuleStore);
        return atoms.size();
    }

    private void activate(Set<RandomVariableAtom> toActivate, List<Rule> rules, GroundRuleStore groundRuleStore) {
        // First commit the atoms to the database.
        db.commit(toActivate, Partition.SPECIAL_WRITE_ID);

        // Also ensure that the activated atoms are now considered "persisted" by the atom manager.
        addToPersistedCache(toActivate);

        // Now, we need to do a partial regrounding with the activated atoms.

        // Collect the specific predicates that are targets in this lazy batch
        // and the rules associated with those predicates.
        Set<StandardPredicate> lazyPredicates = PartialGrounding.getPartialPredicates(toActivate);
        Set<Rule> lazyRules = PartialGrounding.getPartialRules(rules, lazyPredicates);

        for (Rule lazyRule : lazyRules) {
            // We will deal with these rules after we move the lazy atoms to the write partition.
            if (lazyRule.supportsGroundingQueryRewriting()) {
                PartialGrounding.partialSimpleGround(lazyRule, lazyPredicates, groundRuleStore, this);
            }
        }

        // Move all the new atoms out of the lazy partition and into the write partition.
        for (StandardPredicate lazyPredicate : lazyPredicates) {
            db.moveToWritePartition(lazyPredicate, Partition.SPECIAL_WRITE_ID);
        }

        // Since complex aritmetic rules require a full regound, we need to do them
        // after we move the atoms to the write partition.
        for (Rule lazyRule : lazyRules) {
            if (!lazyRule.supportsGroundingQueryRewriting()) {
                PartialGrounding.partialComplexGround((AbstractArithmeticRule)lazyRule, groundRuleStore, this);
            }
        }
    }
}
