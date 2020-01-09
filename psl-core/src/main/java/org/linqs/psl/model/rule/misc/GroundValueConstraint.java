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
package org.linqs.psl.model.rule.misc;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * A simple constraint that fixes the truth value of a {@link RandomVariableAtom}
 */
public class GroundValueConstraint implements UnweightedGroundRule {
    private final RandomVariableAtom atom;

    private final float value;

    public GroundValueConstraint(RandomVariableAtom atom, float value) {
        this.atom = atom;
        this.value = value;
    }

    public RandomVariableAtom getAtom() {
        return atom;
    }

    @Override
    public Set<GroundAtom> getAtoms() {
        Set<GroundAtom> atoms = new HashSet<GroundAtom>();
        atoms.add(atom);
        return atoms;
    }

    @Override
    public UnweightedRule getRule() {
        return null;
    }

    @Override
    public ConstraintTerm getConstraintDefinition() {
        GeneralFunction sum = new GeneralFunction(false, false, 1);
        sum.add(1.0f, atom);
        return new ConstraintTerm(sum, FunctionComparator.EQ, value);
    }

    @Override
    public double getInfeasibility() {
        return Math.abs(atom.getValue() - value);
    }

    @Override
    public List<GroundRule> negate() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String baseToString() {
        return getConstraintDefinition().toString();
    }
}
