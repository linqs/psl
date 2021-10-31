/*
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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.dcd.DCDReasoner;
import org.linqs.psl.reasoner.dcd.term.DCDMemoryTermStore;
import org.linqs.psl.reasoner.dcd.term.DCDTermGenerator;
import org.linqs.psl.reasoner.term.TermGenerator;
import org.linqs.psl.reasoner.term.TermStore;

import java.util.List;

/**
 * Use an DCD reasoner to perform MPE inference.
 */
public class DCDInference extends MPEInference {
    public DCDInference(List<Rule> rules, Database db) {
        super(rules, db, true);
    }

    @Override
    protected GroundRuleStore createGroundRuleStore() {
        return new MemoryGroundRuleStore();
    }

    @Override
    protected Reasoner createReasoner() {
        return new DCDReasoner();
    }

    @Override
    protected TermGenerator createTermGenerator() {
        return new DCDTermGenerator();
    }

    @Override
    protected TermStore createTermStore() {
        return new DCDMemoryTermStore();
    }
}
