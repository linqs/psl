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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.util.MathUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A candidate for collective grounding.
 * Note that candidates are unique and identified by their identity hash codes.
 */
public class CandidateQuery implements Comparable<CandidateQuery> {
    private final Rule baseRule;
    private final Formula formula;

    private final double score;

    // The variable mapping for verified rules that this candidate can ground for.
    private final Map<Rule, Map<Variable, Variable>> coveredVariableMappings;

    // Verified rules that this candidate cannot ground for.
    private final Set<Rule> uncoveredRules;

    /**
     * Initialize this candidate with a formula for the given rule.
     */
    public CandidateQuery(Rule baseRule, Formula formula, double score) {
        this.baseRule = baseRule;
        this.formula = formula;
        this.score = score;

        coveredVariableMappings = new HashMap<Rule, Map<Variable, Variable>>();

        // Since the formula is known to be for the rule, create an identity mapping.
        Set<Variable> variables = formula.collectVariables(new VariableTypeMap()).getVariables();
        Map<Variable, Variable> selfMapping = new HashMap<Variable, Variable>();
        for (Variable variable : variables) {
            selfMapping.put(variable, variable);
        }
        coveredVariableMappings.put(baseRule, selfMapping);

        uncoveredRules = new HashSet<Rule>();
    }

    public Formula getFormula() {
        return formula;
    }

    public double getScore() {
        return score;
    }

    public Rule getBaseRule() {
        return baseRule;
    }

    public Set<Rule> getCoveredRules() {
        return coveredVariableMappings.keySet();
    }

    public Map<Variable, Variable> getVariableMapping(Rule rule) {
        return coveredVariableMappings.get(rule);
    }

    public Map<Variable, Variable> getSelfVariableMapping() {
        return coveredVariableMappings.get(baseRule);
    }

    public Map<Rule, Map<Variable, Variable>> getCoveredVariableMappings() {
        return coveredVariableMappings;
    }

    @Override
    public int hashCode() {
        return formula.hashCode();
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof CandidateQuery)) {
            return false;
        }

        return this.formula.equals(((CandidateQuery)other).formula);
    }

    @Override
    public int compareTo(CandidateQuery other) {
        if (other == null) {
            return -1;
        }

        if (this == other) {
            return 0;
        }

        int value = MathUtils.compare(this.score, other.score);
        if (value != 0) {
            return value;
        }

        return this.formula.toString().compareTo(other.formula.toString());
    }
}
