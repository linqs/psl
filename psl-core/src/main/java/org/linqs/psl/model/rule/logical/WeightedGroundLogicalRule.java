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
package org.linqs.psl.model.rule.logical;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.GeneralFunction;
import org.linqs.psl.util.IteratorUtils;

import java.util.List;

public class WeightedGroundLogicalRule extends AbstractGroundLogicalRule implements WeightedGroundRule {
    protected WeightedGroundLogicalRule(WeightedLogicalRule rule, List<GroundAtom> posLiterals,
            List<GroundAtom> negLiterals) {
        super(rule, posLiterals, negLiterals);
        dissatisfaction.setSquared(rule.isSquared());
    }

    @Override
    public WeightedRule getRule() {
        return (WeightedRule)rule;
    }

    @Override
    public boolean isSquared() {
        return ((WeightedRule)rule).isSquared();
    }

    @Override
    public float getWeight() {
        return ((WeightedRule)rule).getWeight();
    }

    @Override
    public void setWeight(float weight) {
        ((WeightedRule)rule).setWeight(weight);
    }

    @Override
    public GeneralFunction getFunctionDefinition(boolean mergeConstants) {
        // We have already built the function for this ground rule with merged constants.
        if (mergeConstants) {
            return dissatisfaction;
        }

        GeneralFunction function = getFunction(false);
        function.setSquared(((WeightedLogicalRule)rule).isSquared());

        return function;
    }

    @Override
    public float getIncompatibility() {
        return dissatisfaction.getValue();
    }

    @Override
    public float getIncompatibility(GroundAtom replacementAtom, float replacementValue) {
        return dissatisfaction.getValue(replacementAtom, replacementValue);
    }

    @Override
    public String toString() {
        return "" + getWeight() + ": " + baseToString() + ((isSquared()) ? " ^2" : "");
    }

    @Override
    protected GroundRule instantiateNegatedGroundRule(
            Formula disjunction, List<GroundAtom> positiveAtoms,
            List<GroundAtom> negativeAtoms, String name) {
        WeightedLogicalRule newRule = new WeightedLogicalRule(rule.getFormula(), -1.0f * ((WeightedLogicalRule)rule).getWeight(), isSquared(), name);
        return new WeightedGroundLogicalRule(newRule, positiveAtoms, negativeAtoms);
    }
}
