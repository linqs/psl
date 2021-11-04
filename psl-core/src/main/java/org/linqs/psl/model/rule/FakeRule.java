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
package org.linqs.psl.model.rule;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.RawQuery;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import java.util.List;
import java.util.Map;

/**
 * All instances of fake rules have the same default hashcode: 0.
 * Two fake rules are equal if they have the same squared variable value.
 */
public class FakeRule extends AbstractRule implements WeightedRule {
    protected float weight;
    protected boolean squared;

    public FakeRule(float weight, boolean squared) {
        super("fake", 0);

        this.weight = weight;
        this.squared = squared;
    }

    @Override
    public boolean isSquared() {
        return squared;
    }

    @Override
    public float getWeight() {
        return weight;
    }

    @Override
    public void setWeight(float weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        return "fake";
    }

    @Override
    public long groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore) {
        return 0;
    }

    @Override
    public boolean isWeighted() {
        return true;
    }

    @Override
    public boolean supportsGroundingQueryRewriting() {
        return false;
    }

    @Override
    public Formula getRewritableGroundingFormula() {
        return null;
    }

    @Override
    public boolean supportsIndividualGrounding() {
        return false;
    }

    @Override
    public RawQuery getGroundingQuery(AtomManager atomManager) {
        return null;
    }

    @Override
    public void ground(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager, List<GroundRule> results) {
        // Pass.
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        FakeRule otherRule = (FakeRule)other;
        if (this.squared != otherRule.squared) {
            return false;
        }

        return true;
    }
}
