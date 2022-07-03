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
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.util.IteratorUtils;

import java.util.List;

public class UnweightedGroundLogicalRule extends AbstractGroundLogicalRule
        implements UnweightedGroundRule {

    protected UnweightedGroundLogicalRule(UnweightedLogicalRule rule, List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
        super(rule, posLiterals, negLiterals);
    }

    @Override
    public UnweightedRule getRule() {
        return (UnweightedRule)rule;
    }

    @Override
    public float getInfeasibility() {
        return dissatisfaction.getValue();
    }

    @Override
    public ConstraintTerm getConstraintDefinition(boolean mergeConstants) {
        // We have already built the function for this ground rule by merging constants.
        // If we want that one, return it right away.
        if (mergeConstants) {
            return new ConstraintTerm(dissatisfaction, FunctionComparator.LTE, 0.0f);
        }

        return new ConstraintTerm(getFunction(false), FunctionComparator.LTE, 0.0f);
    }

    @Override
    public String toString() {
        return super.toString() + " .";
    }

    @Override
    protected GroundRule instantiateNegatedGroundRule(
            Formula disjunction, List<GroundAtom> positiveAtoms,
            List<GroundAtom> negativeAtoms, String name) {
        UnweightedLogicalRule newRule = new UnweightedLogicalRule(rule.getFormula(), name);
        return new UnweightedGroundLogicalRule(newRule, positiveAtoms, negativeAtoms);
    }
}
