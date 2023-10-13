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
package org.linqs.psl.application.inference.mpe.gurobi;

import org.linqs.psl.application.inference.mpe.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.Reasoner;
import org.linqs.psl.reasoner.gurobi.GurobiReasoner;
import org.linqs.psl.reasoner.gurobi.term.GurobiTermStore;
import org.linqs.psl.reasoner.term.TermStore;

import java.util.List;

/**
 * Use an GradientDescent reasoner to perform MPE inference.
 */
public class GurobiInference extends MPEInference {
    public GurobiInference(List<Rule> rules, Database db) {
        super(rules, db, false);
    }

    @Override
    protected Reasoner createReasoner() {
        return new GurobiReasoner();
    }

    @Override
    public TermStore createTermStore() {
        return new GurobiTermStore(database.getAtomStore());
    }
}
