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
package org.linqs.psl.model.rule.arithmetic;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A template for {@link UnweightedGroundArithmeticRule UnweightedGroundArithmeticRules}.
 */
public class UnweightedArithmeticRule extends AbstractArithmeticRule implements UnweightedRule {
    public UnweightedArithmeticRule(ArithmeticRuleExpression expression) {
        this(expression, expression.toString());
    }

    public UnweightedArithmeticRule(ArithmeticRuleExpression expression, String name) {
        this(expression, new HashMap<SummationVariable, Formula>(), name);
    }

    public UnweightedArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses) {
        this(expression, filterClauses, expression.toString());
    }

    public UnweightedArithmeticRule(ArithmeticRuleExpression expression, Map<SummationVariable, Formula> filterClauses, String name) {
        super(expression, filterClauses, name);
    }

    @Override
    protected UnweightedGroundArithmeticRule makeGroundRule(float[] coefficients, GroundAtom[] atoms,
            FunctionComparator comparator, float constant) {
        return new UnweightedGroundArithmeticRule(this, coefficients, atoms, comparator, constant);
    }

    @Override
    protected UnweightedGroundArithmeticRule makeGroundRule(List<Float> coefficients, List<GroundAtom> atoms,
            FunctionComparator comparator, float constant) {
        return new UnweightedGroundArithmeticRule(this, coefficients, atoms, comparator, constant);
    }

    @Override
    public WeightedRule relax(float weight, boolean squared) {
        unregister();
        return new WeightedArithmeticRule(expression, filters, weight, squared, name);
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(expression);
        s.append(" .");
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
        return false;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }

        if (other == null || this.getClass() != other.getClass()) {
            return false;
        }

        return super.equals(other);
    }
}
