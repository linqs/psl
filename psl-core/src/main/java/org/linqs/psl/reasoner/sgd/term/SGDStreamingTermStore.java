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
package org.linqs.psl.reasoner.sgd.term;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.term.streaming.StreamingIterator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

import java.util.List;

public class SGDStreamingTermStore extends StreamingTermStore<SGDObjectiveTerm> {
    public SGDStreamingTermStore(List<Rule> rules, Database database) {
        super(rules, database, new SGDTermGenerator());
    }

    @Override
    protected StreamingIterator<SGDObjectiveTerm> getGroundingIterator() {
        return new SGDStreamingGroundingIterator(
                this, rules, termCache, termPool, termBuffer, pageSize, numPages);
    }

    @Override
    protected StreamingIterator<SGDObjectiveTerm> getCacheIterator() {
        return new SGDStreamingCacheIterator(
                this, termCache, termPool,
                termBuffer, shufflePage, shuffleMap, randomizePageAccess, numPages);
    }
}
