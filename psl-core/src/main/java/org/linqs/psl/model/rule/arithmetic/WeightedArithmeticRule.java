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
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WeightedArithmeticRule extends AbstractArithmeticRule implements WeightedRule {
    protected float weight;
    protected boolean squared;

    public WeightedArithmeticRule(ArithmeticRuleExpression expression, float weight, boolean squared) {
        this(expression, weight, squared, expression.toString());
    }

    public WeightedArithmeticRule(ArithmeticRuleExpression expression, float weight, boolean squared, String name) {
        this(expression, new HashMap<SummationVariable, Formula>(), weight, squared, name);
    }

    public WeightedArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses,
            float weight, boolean squared) {
        this(expression, filterClauses, weight, squared, expression.toString());
    }

    public WeightedArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses,
            float weight, boolean squared, String name) {
        super(expression, filterClauses, name);

        this.weight = weight;
        this.squared = squared;
    }

    @Override
    protected AbstractGroundArithmeticRule makeGroundRule(float[] coeffs, GroundAtom[] atoms,
            FunctionComparator comparator, float constant) {
        return new WeightedGroundArithmeticRule(this, coeffs, atoms, comparator, constant);
    }

    @Override
    protected AbstractGroundArithmeticRule makeGroundRule(List<Float> coeffs, List<GroundAtom> atoms,
            FunctionComparator comparator, float constant) {
        return new WeightedGroundArithmeticRule(this, coeffs, atoms, comparator, constant);
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
        StringBuilder s = new StringBuilder();
        s.append(weight);
        s.append(": ");
        s.append(expression);
        s.append((squared) ? " ^2" : "");
        for (Map.Entry<SummationVariable, Formula> e : filters.entrySet()) {
            s.append("   {");
            // Appends the corresponding Variable, not the SummationVariable, to leave out the '+'
            s.append(e.getKey().getVariable());
            s.append(" : ");
            s.append(e.getValue());
            s.append("}");
        }
        return s.toString();
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

        WeightedArithmeticRule otherRule = (WeightedArithmeticRule)other;
        if (this.squared != otherRule.squared) {
            return false;
        }

        return super.equals(other);
    }
}
