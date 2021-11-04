/**
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.reasoner.term.online;

import org.linqs.psl.database.Partition;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.OnlineAtomManager;
import org.linqs.psl.grounding.PartialGrounding;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.HyperplaneTermGenerator;
import org.linqs.psl.reasoner.term.streaming.StreamingGroundingIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTerm;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Iterate over all the new terms that are instantiated by partial grounding.
 * This iterator will not attempt to write to partially filled term pages, and will always start on a new page.
 *
 * Note that not all rules can be grounded in an online fashion, and they will not participate in the online process.
 * Specifically, arithmetic rules with summations.
 */
public abstract class OnlineGroundingIterator<T extends StreamingTerm> extends StreamingGroundingIterator<T> {
    // Predicates that are currently being used for grounding.
    private Set<StandardPredicate> onlinePredicates;

    public OnlineGroundingIterator(
            OnlineTermStore<T> parentStore, List<Rule> rules,
            AtomManager atomManager, HyperplaneTermGenerator<T, GroundAtom> termGenerator,
            List<T> termCache, List<T> termPool,
            ByteBuffer termBuffer, ByteBuffer volatileBuffer,
            int pageSize, int nextPage) {
        super(parentStore, rules, atomManager, termGenerator, termCache, termPool, termBuffer, volatileBuffer, pageSize, nextPage);

        // The initial iteration will not have any online components.
        if (parentStore.isInitialRound()) {
            onlinePredicates = null;
            return;
        }

        // Ready the new atoms in the atom manager for partial grounding.
        atomManager.getDatabase().commit(((OnlineAtomManager)atomManager).flushObservedAtoms(),
                ((OnlineAtomManager)atomManager).getOnlineReadPartition());
        Set<GroundAtom> obsAtomCache = ((OnlineAtomManager)atomManager).flushNewObservedAtoms();
        Set<GroundAtom> rvAtomCache = ((OnlineAtomManager)atomManager).flushNewRandomVariableAtoms();

        onlinePredicates = new HashSet<StandardPredicate>();
        onlinePredicates.addAll(PartialGrounding.getPartialPredicates(obsAtomCache));
        onlinePredicates.addAll(PartialGrounding.getPartialPredicates(rvAtomCache));

        // Move all the new atoms over to special partitions that we can use for partial grounding.
        atomManager.getDatabase().commit(obsAtomCache, Partition.SPECIAL_READ_ID);
        atomManager.getDatabase().commit(rvAtomCache, Partition.SPECIAL_WRITE_ID);

        // Only ground the rules for which there is a partial target.
        this.rules = new ArrayList<Rule>(PartialGrounding.getPartialRules(this.rules, onlinePredicates));
    }

    @Override
    protected boolean primeNextRuleIndex() {
        // Flush remaining terms from current rule.
        // This flush ensures all terms found in the same page where instantiated by the same rule.
        if (termCache.size() > 0) {
            // Add the page to the rule mapping.
            ((OnlineTermStore<T>)parentStore).addRuleMapping(rules.get(currentRule), nextPage);

            flushCache();
        }

        return super.primeNextRuleIndex();
    }

    @Override
    protected void startGroundingQuery() {
        // If the term store has not been initially loaded (initial round), then just do normal grounding.
        if (parentStore.isInitialRound()) {
            super.startGroundingQuery();
            return;
        }

        queryIterable = null;
        queryResults = null;

        if (!rules.get(currentRule).supportsGroundingQueryRewriting()) {
            return;
        }

        queryIterable = PartialGrounding.getPartialGroundingResults(rules.get(currentRule), onlinePredicates, atomManager.getDatabase());
        if (queryIterable == null) {
            return;
        }

        queryResults = queryIterable.iterator();
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }

        if (!parentStore.isInitialRound()) {
            // Move all the new atoms out of the special partition and into the read/write partitions.
            for (StandardPredicate onlinePredicate : onlinePredicates) {
                atomManager.getDatabase().moveToPartition(onlinePredicate, Partition.SPECIAL_READ_ID,
                        ((OnlineAtomManager)atomManager).getOnlineReadPartition());
                atomManager.getDatabase().moveToPartition(onlinePredicate, Partition.SPECIAL_WRITE_ID,
                        atomManager.getDatabase().getWritePartition().getID());
            }
        }

        super.close();
    }
}
