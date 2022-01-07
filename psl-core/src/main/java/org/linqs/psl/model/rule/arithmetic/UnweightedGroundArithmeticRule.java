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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;

import java.util.List;

/**
 * An {@link AbstractGroundArithmeticRule} that is unweighted, i.e., it is a hard
 * constraint that must always hold.
 */
public class UnweightedGroundArithmeticRule extends AbstractGroundArithmeticRule
        implements UnweightedGroundRule {

    protected UnweightedGroundArithmeticRule(UnweightedArithmeticRule rule, List<Float> coefficients,
            List<GroundAtom> atoms, FunctionComparator comparator, float constant) {
        super(rule, coefficients, atoms, comparator, constant);
    }

    protected UnweightedGroundArithmeticRule(UnweightedArithmeticRule rule, float[] coefficients,
            GroundAtom[] atoms, FunctionComparator comparator, float constant) {
        super(rule, coefficients, atoms, comparator, constant);
    }

    @Override
    public UnweightedRule getRule() {
        return (UnweightedRule) rule;
    }

    @Override
    public float getInfeasibility() {
        float sum = 0.0f;
        for (int i = 0; i < coefficients.length; i++) {
            // Skip any grounding only predicates.
            if (atoms[i].getPredicate() instanceof GroundingOnlyPredicate) {
                continue;
            }

            sum += coefficients[i] * atoms[i].getValue();
        }

        switch (comparator) {
            case EQ:
                return Math.abs(sum - constant);
            case GTE:
                return -1.0f * Math.min(sum - constant, 0.0f);
            case LTE:
                return Math.max(sum - constant, 0.0f);
            default:
                throw new IllegalStateException("Unrecognized comparator: " + comparator);
        }
    }

    @Override
    public ConstraintTerm getConstraintDefinition(boolean mergeConstants) {
        GeneralFunction sum = new GeneralFunction(false, false, coefficients.length, mergeConstants);

        for (int i = 0; i < coefficients.length; i++) {
            // Skip any grounding only predicates.
            if (atoms[i].getPredicate() instanceof GroundingOnlyPredicate) {
                continue;
            }

            sum.add(coefficients[i], atoms[i]);
        }

        return new ConstraintTerm(sum, comparator, constant);
    }

    @Override
    public String toString() {
        return super.toString() + " .";
    }
}
