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
package org.linqs.psl.application.inference.online;

import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.sgd.SGDReasoner;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.sgd.term.SGDOnlineTermStore;
import org.linqs.psl.reasoner.sgd.term.SGDTermGenerator;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

import java.util.List;

/**
 * Use streaming grounding and online inference with an SGD reasoner.
 */
public class SGDOnlineInference extends OnlineInference {
    public SGDOnlineInference(List<Rule> rules, Database database) {
        super(rules, database, true);
    }

    @Override
    protected Reasoner createReasoner() {
        return new SGDReasoner();
    }

    @Override
    protected StreamingTermStore<SGDObjectiveTerm> createTermStore() {
        return new SGDOnlineTermStore(rules, atomManager, (SGDTermGenerator)termGenerator);
    }

    @Override
    protected GroundRuleStore createGroundRuleStore() {
        return null;
    }

    @Override
    protected TermGenerator createTermGenerator() {
        return new SGDTermGenerator(false);
    }

    @Override
    protected void completeInitialize() {
        // Do nothing else. Specifically, do not ground.
    }
}
