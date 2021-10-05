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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.Rule;

import java.util.HashSet;
import java.util.Set;

public class CandidateQuery {
    public final Formula formula;
    public final double score;

    // Verified rules that this candidate can ground for.
    public final Set<Rule> coveredRules;

    // Verified rules that this candidate cannot ground for.
    public final Set<Rule> uncoveredRules;

    public CandidateQuery(Rule rule, Formula formula, double score) {
        this.formula = formula;
        this.score = score;

        coveredRules = new HashSet<Rule>();
        coveredRules.add(rule);

        uncoveredRules = new HashSet<Rule>();
    }
}
