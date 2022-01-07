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
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;

import java.util.List;

public class WeightedGroundArithmeticRule extends AbstractGroundArithmeticRule implements WeightedGroundRule {
    protected WeightedGroundArithmeticRule(WeightedArithmeticRule rule, List<Float> coefficients,
            List<GroundAtom> atoms, FunctionComparator comparator, float constant) {
        super(rule, coefficients, atoms, comparator, constant);
        validate();
    }

    protected WeightedGroundArithmeticRule(WeightedArithmeticRule rule, float[] coefficients, GroundAtom[] atoms,
            FunctionComparator comparator, float constant) {
        super(rule, coefficients, atoms, comparator, constant);
        validate();
    }

    private void validate() {
        if (FunctionComparator.EQ.equals(comparator)) {
            throw new IllegalArgumentException("WeightedGroundArithmeticRules do not support equality comparators. "
                    + "Create two ground rules instead, one with " + FunctionComparator.LTE + " and one with "
                    + FunctionComparator.GTE + ".");
        } else if (!FunctionComparator.LTE.equals(comparator) && !FunctionComparator.GTE.equals(comparator)) {
            throw new IllegalArgumentException("Unrecognized comparator: " + comparator);
        }
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
        GeneralFunction sum = new GeneralFunction(true, isSquared(), coefficients.length, mergeConstants);

        float termSign = FunctionComparator.GTE.equals(comparator) ? -1.0f : 1.0f;
        for (int i = 0; i < coefficients.length; i++) {
            // Skip any grounding only predicates.
            if (atoms[i].getPredicate() instanceof GroundingOnlyPredicate) {
                continue;
            }

            sum.add(termSign * coefficients[i], atoms[i]);
        }
        sum.add(-1.0f * termSign * constant);

        return sum;
    }

    @Override
    public float getIncompatibility() {
        return getIncompatibility(null, 0.0f);
    }

    @Override
    public float getIncompatibility(GroundAtom replacementAtom, float replacementValue) {
        float sum = 0.0f;
        for (int i = 0; i < coefficients.length; i++) {
            // Skip any grounding only predicates.
            if (atoms[i].getPredicate() instanceof GroundingOnlyPredicate) {
                continue;
            }

            if (atoms[i] == replacementAtom) {
                sum += coefficients[i] * replacementValue;
            } else {
                sum += coefficients[i] * atoms[i].getValue();
            }
        }
        sum -= constant;

        if (FunctionComparator.GTE.equals(comparator)) {
            sum *= -1.0f;
        }

        return (float)((isSquared()) ? Math.pow(Math.max(sum, 0.0f), 2) : Math.max(sum, 0.0f));
    }

    @Override
    public String toString() {
        return "" + getWeight() + ": " + baseToString() + ((isSquared()) ? " ^2" : "");
    }
}
