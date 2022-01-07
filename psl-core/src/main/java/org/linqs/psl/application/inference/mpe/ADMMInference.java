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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.admm.ADMMReasoner;
import org.linqs.psl.reasoner.admm.term.ADMMTermStore;
import org.linqs.psl.reasoner.admm.term.ADMMTermGenerator;
import org.linqs.psl.reasoner.term.MemoryTermStore;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import java.util.List;

/**
 * Use an ADMM reasoner to perform MPE inference.
 */
public class ADMMInference extends MPEInference {
    public ADMMInference(List<Rule> rules, Database db) {
        super(rules, db);
    }

    @Override
    protected GroundRuleStore createGroundRuleStore() {
        return new MemoryGroundRuleStore();
    }

    @Override
    protected Reasoner createReasoner() {
        return new ADMMReasoner();
    }

    @Override
    protected TermGenerator createTermGenerator() {
        return new ADMMTermGenerator();
    }

    @Override
    protected TermStore createTermStore() {
        return new ADMMTermStore();
    }
}
