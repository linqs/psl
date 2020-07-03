/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.grounding.PartialGrounding;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.reasoner.term.OnlineTermStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A persisted atom manager that will add new atoms in an online setting.
 * If activateAtoms() is called, then all online atoms above the activation threshold
 * (Options.LAM_ACTIVATION_THRESHOLD) will be instantiated as real atoms.
 */
public class OnlineAtomManager extends PersistedAtomManager {
    private static final Logger log = LoggerFactory.getLogger(OnlineAtomManager.class);

    private static final float DEFAULT_UNOBSERVED_VALUE = 1.0f;

    /**
     * All the ground atoms that have been seen, but not instantiated.
     */
    private final Set<GroundAtom> obAtoms;
    private final Set<GroundAtom> rvAtoms;
    public final Set<GroundAtom> newAtoms;
    private final int readPartition;

    public OnlineAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        obAtoms = new HashSet<GroundAtom>();
        rvAtoms = new HashSet<GroundAtom>();
        newAtoms = new HashSet<GroundAtom>();
        readPartition = Options.ONLINE_READ_PARTITION.getInt();
    }

    //TODO(connor) Check to see if atom exists in the database, throw error if it does
    //TODO(connor) Should there be an activation like lazyatommanager?
    public synchronized void addObservedAtom(Predicate predicate, Float value, Constant... arguments) {
        AtomCache cache = db.getCache();
        ObservedAtom atom = cache.instantiateObservedAtom(predicate, arguments, value);

        obAtoms.add(atom);
        newAtoms.add(atom);
    }

    public synchronized void addRandomVariableAtom(StandardPredicate predicate, Constant... arguments) {
        AtomCache cache = db.getCache();
        RandomVariableAtom atom = cache.instantiateRandomVariableAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
        atom.setPersisted(true);

        rvAtoms.add(atom);
        newAtoms.add(atom);
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
    }

    public ArrayList<GroundRule> activateAtoms(List<Rule> rules, OnlineTermStore termStore) {
        log.trace("Activating " + newAtoms.size() + " new atoms");

        if (newAtoms.size() == 0) {
            return new ArrayList<GroundRule>();
        }

        // TODO(connor): This could run into memory issues.
        // HACK(connor): Generalize commit for groundAtoms.
        db.commitGroundAtoms(obAtoms, db.getReadPartitions().get(readPartition).getID());
        db.commitGroundAtoms(rvAtoms, db.getWritePartition().getID());

        Set<Predicate> onlinePredicates = PartialGrounding.getOnlinePredicates(newAtoms);
        Set<Rule> onlineRules = PartialGrounding.getOnlineRules(rules, onlinePredicates);
        ArrayList<GroundRule> totalGroundRules = new ArrayList<GroundRule>();

        // TODO(connor): Currently ignoring arithmetic rules. Why do these need a full regrounding?
        for (Rule onlineRule : onlineRules) {
            if (onlineRule.supportsGroundingQueryRewriting()) {
                ArrayList onlineRuleGroundings = PartialGrounding.onlineSimpleGround(onlineRule, onlinePredicates, this);
                totalGroundRules.addAll(onlineRuleGroundings);
            }
        }

        obAtoms.clear();
        rvAtoms.clear();
        newAtoms.clear();

        return totalGroundRules;
    }
}
