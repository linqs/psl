/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.HashCode;
import org.linqs.psl.util.Parallel;

import java.util.List;
import java.util.Map;

public class WeightedLogicalRule extends AbstractLogicalRule implements WeightedRule {
    protected Weight weight;
    protected boolean squared;

    public WeightedLogicalRule(Formula formula, Weight weight, boolean squared) {
        this(formula, weight, squared, formula.toString());
    }

    public WeightedLogicalRule(Formula formula, Weight weight, boolean squared, String name) {
        // Warning: The hashCode of a rule is dependent on the weight.
        // If the weight changes, as it does during learning, then the hashCode of the rule will change.
        // Rules are registered using the initial hashCode.
        super(formula, name, HashCode.build(formula.getDNF().hashCode(), weight));

        this.weight = weight;
        this.squared = squared;
    }

    @Override
    protected GroundRule ground(Constant[] constants, Map<Variable, Integer> variableMap, Database database) {
        // Get the grounding resources for this thread,
        if (!Parallel.hasThreadObject(groundingResourcesKey)) {
            GroundingResources groundingResources = new GroundingResources();
            groundingResources.parseNegatedDNF(negatedDNF, weight);
            Parallel.putThreadObject(groundingResourcesKey, groundingResources);
        }
        GroundingResources groundingResources = (GroundingResources)Parallel.getThreadObject(groundingResourcesKey);

        return groundInternal(constants, variableMap, database, groundingResources);
    }

    @Override
    protected WeightedGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals, Weight groundedWeight) {
        if (groundedWeight == null) {
            return new WeightedGroundLogicalRule(this, posLiterals, negLiterals);
        } else {
            WeightedLogicalRule groundedDeepWeightedRule = new WeightedLogicalRule(formula, groundedWeight, squared, groundedWeight.getAtom().toString() + ": " + name);
            groundedDeepWeightedRule.setParentHashCode(hashCode());
            addChildHashCode(groundedDeepWeightedRule.hashCode());

            return new WeightedGroundLogicalRule(groundedDeepWeightedRule, posLiterals, negLiterals);
        }
    }

    @Override
    public boolean isSquared() {
        return squared;
    }

    @Override
    public Weight getWeight() {
        return weight;
    }

    @Override
    public void setWeight(Weight weight) {
        this.weight = weight;
    }

    @Override
    public String toString() {
        String squaredSuffix = (squared) ? " ^2" : "";
        return weight + ": " + formula + squaredSuffix;
    }

    @Override
    public boolean isWeighted() {
        return true;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        WeightedLogicalRule otherRule = (WeightedLogicalRule)other;
        if (this.squared != otherRule.squared) {
            return false;
        }

        if (!this.weight.equals(otherRule.weight)) {
            return false;
        }

        return super.equals(other);
    }
}
