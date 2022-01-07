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
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.WeightedRule;

import java.util.List;

public class WeightedLogicalRule extends AbstractLogicalRule implements WeightedRule {
    protected float weight;
    protected boolean squared;

    public WeightedLogicalRule(Formula formula, float weight, boolean squared) {
        this(formula, weight, squared, formula.toString());
    }

    public WeightedLogicalRule(Formula formula, float weight, boolean squared, String name) {
        super(formula, name);

        this.weight = weight;
        this.squared = squared;
    }

    @Override
    protected WeightedGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals) {
        return new WeightedGroundLogicalRule(this, posLiterals, negLiterals);
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
        String squaredSuffix = (squared) ? " ^2" : "";
        return "" + weight + ": " + formula + squaredSuffix;
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

        return super.equals(other);
    }
}
