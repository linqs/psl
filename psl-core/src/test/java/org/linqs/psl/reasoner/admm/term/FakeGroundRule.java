/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
package org.linqs.psl.reasoner.admm.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.reasoner.function.GeneralFunction;

import java.util.List;
import java.util.Set;

public class FakeGroundRule implements WeightedGroundRule {
    private double weight;

    public FakeGroundRule(double weight) {
        this.weight = weight;
    }

    @Override
    public Set<GroundAtom> getAtoms() {
        return null;
    }

    @Override
    public List<GroundRule> negate() {
        return null;
    }

    @Override
    public WeightedRule getRule() {
        return null;
    }

    @Override
    public boolean isSquared() {
        return false;
    }

    @Override
    public double getWeight() {
        return weight;
    }

    @Override
    public void setWeight(double weight) {
        this.weight = weight;
    }

    @Override
    public GeneralFunction getFunctionDefinition() {
        return null;
    }

    @Override
    public double getIncompatibility() {
        return 0.0;
    }

    @Override
    public double getIncompatibility(GroundAtom replacementAtom, float replacementValue) {
        return 0.0;
    }

    @Override
    public String baseToString() {
        return "fake";
    }
}
