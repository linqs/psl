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
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.RDBMSInserter;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.PartialGrounding;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.AbstractArithmeticRule;
import org.linqs.psl.model.term.Constant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * A persisted atom manager that will add new atoms in an online setting.
 * If activateAtoms() is called, then all online atoms above the activation threshold
 * (Options.LAM_ACTIVATION_THRESHOLD) will be instantiated as real atoms.
 */
public class OnlineAtomManager extends PersistedAtomManager {
    private static final Logger log = LoggerFactory.getLogger(OnlineAtomManager.class);

    /**
     * All the ground atoms that have been seen, but not instantiated.
     */
    private final Set<GroundAtom> onlineAtoms;
    private final Set<RandomVariableAtom> rvAtoms;
    private final int readPartition;

    public OnlineAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        onlineAtoms = new HashSet<GroundAtom>();
        rvAtoms = new HashSet<RandomVariableAtom>();
        readPartition = Options.ONLINE_READ_PARTITION.getInt();
    }

    //TODO(connor) Check to see if atom exists in the database, throw error if it does
    //TODO(connor) Should there be an activation like lazyatommanager?
    //TODO(connor) Why do atoms need to be added to a LazyPartition first?
    public synchronized void addObservedAtom(Predicate predicate, Float value, Constant... arguments) {
        ObservedAtom atom = new ObservedAtom(predicate, arguments, value);
        PredicateInfo predicateInfo = new PredicateInfo(predicate);
        Partition partition = db.getReadPartitions().get(readPartition);
        RDBMSDataStore dataStore = (RDBMSDataStore)db.getDataStore();

        RDBMSInserter inserter = new RDBMSInserter(dataStore, predicateInfo, partition);
        inserter.insertValue(value, arguments);

        onlineAtoms.add(atom);
    }

    public synchronized void addRandomVariableAtom(StandardPredicate predicate, Float value, Constant... arguments) {
        RandomVariableAtom atom = new RandomVariableAtom(predicate, arguments, value);
        PredicateInfo predicateInfo = new PredicateInfo(predicate);
        Partition partition = db.getWritePartition();
        RDBMSDataStore dataStore = (RDBMSDataStore)db.getDataStore();

        RDBMSInserter inserter = new RDBMSInserter(dataStore, predicateInfo, partition);
        inserter.insertValue(value, arguments);

        onlineAtoms.add(atom);
        rvAtoms.add((RandomVariableAtom)atom);
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
    }

    public Set<GroundAtom> getOnlineAtoms() {
        return Collections.unmodifiableSet(onlineAtoms);
    }

    public int activateAtoms(List<Rule> rules) {
        if (onlineAtoms.size() == 0) {
            return 0;
        }

        // Also ensure that the activated atoms are now considered "persisted" by the atom manager.
        addToPersistedCache(rvAtoms);

        // Now, we need to do a partial regrounding with the activated atoms.

        // Collect the specific predicates that are targets in this online batch
        // and the rules associated with those predicates.
        Set<Predicate> onlinePredicates = PartialGrounding.getOnlinePredicates(onlineAtoms);
        Set<Rule> onlineRules = PartialGrounding.getOnlineRules(rules, onlinePredicates);

        for (Rule lazyRule : lazyRules) {
            // We will deal with these rules after we move the lazy atoms to the write partition.
            if (lazyRule.supportsGroundingQueryRewriting()) {
                PartialGrounding.lazySimpleGround(lazyRule, lazyPredicates, groundRuleStore, this);
            }
        }

        // Move all the new atoms out of the lazy partition and into the write partition.
        for (StandardPredicate lazyPredicate : lazyPredicates) {
            db.moveToWritePartition(lazyPredicate, Partition.LAZY_PARTITION_ID);
        }

        // Since complex aritmetic rules require a full regound, we need to do them
        // after we move the atoms to the write partition.
        for (Rule lazyRule : lazyRules) {
            if (!lazyRule.supportsGroundingQueryRewriting()) {
                PartialGrounding.lazyComplexGround((AbstractArithmeticRule)lazyRule, groundRuleStore, this);
            }
        }
    }
}
