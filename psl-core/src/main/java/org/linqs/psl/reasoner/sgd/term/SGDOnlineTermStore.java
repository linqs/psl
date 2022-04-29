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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.online.OnlineTermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;

import java.util.List;

public class SGDOnlineTermStore extends OnlineTermStore<SGDObjectiveTerm> {
    public SGDOnlineTermStore(List<Rule> rules, AtomManager atomManager, SGDTermGenerator termGenerator) {
        super(rules, atomManager, termGenerator);
    }

    @Override
    public StreamingIterator<SGDObjectiveTerm> getGroundingIterator(List<Rule> rules) {
        return new SGDOnlineGroundingIterator(
                this, rules, atomManager, termGenerator,
                termCache, termPool, termBuffer, volatileBuffer, pageSize, numPages);
    }

    @Override
    public StreamingIterator<SGDObjectiveTerm> getGroundingIterator() {
        return getGroundingIterator(this.rules);
    }

    @Override
    public StreamingIterator<SGDObjectiveTerm> getCacheIterator() {
        return new SGDStreamingCacheIterator(
                this, false, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    public StreamingIterator<SGDObjectiveTerm> getNoWriteIterator() {
        return new SGDStreamingCacheIterator(
                this, true, termCache, termPool,
                termBuffer, volatileBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }

    @Override
    public boolean rejectCacheTerm(SGDObjectiveTerm term) {
        boolean allObservedAtoms = true;
        int[] variableIndexes = term.getVariableIndexes();

        for (int i=0; i < term.size(); i++) {
            if (variableAtoms[variableIndexes[i]] == null) {
                return true;
            }

            // If a random variable atom is present in the term,
            // then the term contributes to optimization and should not be rejected.
            if (variableAtoms[variableIndexes[i]] instanceof RandomVariableAtom) {
                allObservedAtoms = false;
            }
        }

        return allObservedAtoms;
    }
}
