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

import java.util.List;
import java.util.Map;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.Parallel;

public class UnweightedLogicalRule extends AbstractLogicalRule implements UnweightedRule {
    public UnweightedLogicalRule(Formula formula) {
        this(formula, formula.toString());
    }

    public UnweightedLogicalRule(Formula formula, String name) {
        super(formula, name, formula.getDNF().hashCode());
    }

    @Override
    protected GroundRule ground(Constant[] constants, Map<Variable, Integer> variableMap, Database database) {
        // Get the grounding resources for this thread,
        if (!Parallel.hasThreadObject(groundingResourcesKey)) {
            GroundingResources groundingResources = new GroundingResources();
            groundingResources.parseNegatedDNF(negatedDNF, null);
            Parallel.putThreadObject(groundingResourcesKey, groundingResources);
        }
        GroundingResources groundingResources = (GroundingResources)Parallel.getThreadObject(groundingResourcesKey);

        return groundInternal(constants, variableMap, database, groundingResources);
    }

    @Override
    public WeightedRule relax(Weight weight, boolean squared) {
        unregister();
        return new WeightedLogicalRule(formula, weight, squared, name);
    }

    @Override
    protected AbstractGroundLogicalRule groundFormulaInstance(List<GroundAtom> posLiterals, List<GroundAtom> negLiterals, Weight groundedWeight) {
        assert groundedWeight == null;
        return new UnweightedGroundLogicalRule(this, posLiterals, negLiterals);
    }

    @Override
    public String toString() {
        return formula.toString() + " .";
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
