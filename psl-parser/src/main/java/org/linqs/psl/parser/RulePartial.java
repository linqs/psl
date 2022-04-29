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
package org.linqs.psl.parser;

import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.logical.UnweightedLogicalRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;

import java.util.Map;

/**
 * A container for the possible return values of loadRulePartial().
 * A RulePartial can represent an entirely complete rule or the logic of a rule without the
 * weights or squared potential.
 * isRule() can be called to see if the partial is complete.
 * toRule() can be called to get a fully formed rule from the partial.
*/
public class RulePartial {
    private Rule rule;
    private Formula formula;
    private ArithmeticRuleExpression arithmeticExpression;
    private Map<SummationVariable, Formula> filters;

    public RulePartial(Object ruleCore) {
        if (ruleCore instanceof Rule) {
            rule = (Rule)ruleCore;
        } else if (ruleCore instanceof Formula) {
            formula = (Formula)ruleCore;
        } else if (ruleCore instanceof ArithmeticRuleExpression) {
            arithmeticExpression = (ArithmeticRuleExpression)ruleCore;
        } else {
            throw new IllegalArgumentException(String.format(
                    "Expected Rule, Formula, or ArithmeticRuleExpression, got %s.",
                    ruleCore.getClass().getName()));
        }
    }

    public RulePartial(ArithmeticRuleExpression arithmeticExpression, Map<SummationVariable, Formula> filters) {
        this.arithmeticExpression = arithmeticExpression;
        this.filters = filters;
    }

    public boolean isRule() {
        return rule != null;
    }

    /**
     * Shortcut for toRule(null, null), which will create an unweighted rule.
     */
    public Rule toRule() {
        return toRule(null, null);
    }

    /**
     * Create a rule from the partial given the weight and squared.
     * If the partial is already a rule (isRule() == true), then nulls are expected.
     * Even if the partial is not a rule, then nulls are allowed if an unweighted rule is desired.
     * Weight and squared must either both be non-null, or both be null.
     */
    public Rule toRule(Float weight, Boolean squared) {
        if (weight == null && squared == null) {
            if (rule == null) {
                if (formula != null) {
                    return toUnweightedLogicalRule();
                } else {
                    return toUnweightedArithmeticRule();
                }
            }

            return rule;
        } else if (weight != null && squared != null) {
            if (rule == null) {
                if (formula != null) {
                    return toWeightedLogicalRule(weight.floatValue(), squared.booleanValue());
                } else {
                    return toWeightedArithmeticRule(weight.floatValue(), squared.booleanValue());
                }
            }

            throw new IllegalArgumentException("The partial is already a full rule, cannot specify weight/squared.");
        }

        throw new IllegalArgumentException("Either both weight and squared must be non-null, or both must be null. Found weight: " + weight + ", squared: " + squared + ".");
    }

    private Rule toUnweightedLogicalRule() {
        return new UnweightedLogicalRule(formula);
    }

    private Rule toWeightedLogicalRule(float weight, boolean squared) {
        return new WeightedLogicalRule(formula, weight, squared);
    }

    private Rule toUnweightedArithmeticRule() {
        if (filters == null) {
            return new UnweightedArithmeticRule(arithmeticExpression);
        }

        return new UnweightedArithmeticRule(arithmeticExpression, filters);
    }

    private Rule toWeightedArithmeticRule(float weight, boolean squared) {
        if (filters == null) {
            return new WeightedArithmeticRule(arithmeticExpression, weight, squared);
        }

        return new WeightedArithmeticRule(arithmeticExpression, filters, weight, squared);
    }
}
