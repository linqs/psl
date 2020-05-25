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
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.PredicateInfo;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.RDBMSInserter;
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

import java.util.*;

/**
 * A persisted atom manager that will add new atoms in an online setting.
 * If activateAtoms() is called, then all online atoms above the activation threshold
 * (Options.LAM_ACTIVATION_THRESHOLD) will be instantiated as real atoms.
 */
public class OnlineAtomManager extends PersistedAtomManager {
    private static final Logger log = LoggerFactory.getLogger(OnlineAtomManager.class);

    private static final float DEFAULT_UNOBSERVED_VALUE = 0.0f;

    /**
     * All the ground atoms that have been seen, but not instantiated.
     */
    private final Set<GroundAtom> onlineAtoms;
    private final int readPartition;

    public OnlineAtomManager(Database db) {
        super(db);

        if (!(db instanceof RDBMSDatabase)) {
            throw new IllegalArgumentException("OnlineAtomManagers require RDBMSDatabase.");
        }

        onlineAtoms = new HashSet<GroundAtom>();
        readPartition = Options.ONLINE_READ_PARTITION.getInt();
    }

    //TODO(connor) Check to see if atom exists in the database, throw error if it does
    //TODO(connor) Should there be an activation like lazyatommanager?
    //TODO(connor) Why do atoms need to be added to a LazyPartition first?
    public synchronized void addObservedAtom(Predicate predicate, Float value, Constant... arguments) {
        ObservedAtom atom = new ObservedAtom(predicate, arguments, value);
        onlineAtoms.add(atom);
    }

    public synchronized void addRandomVariableAtom(StandardPredicate predicate, Constant... arguments) {
        RandomVariableAtom atom = new RandomVariableAtom(predicate, arguments, DEFAULT_UNOBSERVED_VALUE);
        onlineAtoms.add(atom);
    }

    public void insertNewAtoms() {
        RDBMSDataStore dataStore = (RDBMSDataStore)db.getDataStore();

        for (GroundAtom atom : onlineAtoms) {
            Predicate predicate = atom.getPredicate();
            PredicateInfo predicateInfo = new PredicateInfo(predicate);
            Constant[] arguments = atom.getArguments();
            Float value = atom.getValue();

            if (atom instanceof ObservedAtom) {
                Partition partition = db.getReadPartitions().get(readPartition);

                RDBMSInserter inserter = new RDBMSInserter(dataStore, predicateInfo, partition);
                inserter.insertValue(value, arguments);
            } else if (atom instanceof RandomVariableAtom) {
                Partition partition = db.getWritePartition();

                RDBMSInserter inserter = new RDBMSInserter(dataStore, predicateInfo, partition);
                inserter.insert(arguments);

                addToPersistedCache((RandomVariableAtom)atom);
            } else {
                throw new IllegalStateException(String.format(
                        "Found a ground atom (%s) that is neither observed or a target.",  atom));
            }
        }
    }

    @Override
    public void reportAccessException(RuntimeException ex, GroundAtom offendingAtom) {
        // OnlineAtomManger does not have access exceptions.
    }

    public Set<GroundAtom> getOnlineAtoms() {
        return Collections.unmodifiableSet(onlineAtoms);
    }

    public ArrayList<GroundRule> activateAtoms(List<Rule> rules, OnlineTermStore termStore) {
        if (onlineAtoms.size() == 0) {
            return new ArrayList<GroundRule>();
        }

        //TODO(connor) Flush Lazy Partition.
        db.commitGroundAtoms(onlineAtoms, Partition.LAZY_PARTITION_ID);

        Set<Predicate> onlinePredicates = PartialGrounding.getOnlinePredicates(onlineAtoms);
        Set<Rule> onlineRules = PartialGrounding.getOnlineRules(rules, onlinePredicates);
        //TODO(connor) This could run into memeory issues.
        ArrayList<GroundRule> totalGroundRules = new ArrayList<GroundRule>();

        //TODO(connor) Currently ignoring arithmetic rules. Why do these need a full regrounding?
        for (Rule onlineRule : onlineRules) {
            if (onlineRule.supportsGroundingQueryRewriting()) {
                ArrayList onlineRuleGroundings = PartialGrounding.onlineSimpleGround(onlineRule, onlinePredicates, this);
                totalGroundRules.addAll(onlineRuleGroundings);
            }
        }

        insertNewAtoms();
        onlineAtoms.clear();

        return totalGroundRules;
    }
}
