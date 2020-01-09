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
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.UnweightedRule;
import org.linqs.psl.reasoner.function.ConstraintTerm;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.function.GeneralFunction;

import java.util.Arrays;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

/**
 * A linear constraint on the truth values of {@link GroundAtom GroundAtoms}
 */
public class GroundLinearConstraint implements UnweightedGroundRule {
    private final GroundAtom[] atoms;
    private final float[] coeffs;
    private final FunctionComparator comp;
    private final float value;

    public GroundLinearConstraint(GroundAtom[] atoms, float[] coeffs, FunctionComparator comp, float value) {
        if (atoms.length != coeffs.length) {
            throw new IllegalArgumentException("Same number of atoms and coefficients must be provided.");
        }

        this.atoms = Arrays.copyOf(atoms, atoms.length);
        this.coeffs = Arrays.copyOf(coeffs, coeffs.length);
        this.comp = comp;
        this.value = value;
    }

    @Override
    public Set<GroundAtom> getAtoms() {
        Set<GroundAtom> atoms = new HashSet<GroundAtom>();
        atoms.addAll(atoms);
        return atoms;
    }

    @Override
    public UnweightedRule getRule() {
        return null;
    }

    @Override
    public ConstraintTerm getConstraintDefinition() {
        GeneralFunction sum = new GeneralFunction(false, false, atoms.length);
        for (int i = 0; i < atoms.length; i++) {
            sum.add(coeffs[i], atoms[i]);
        }
        return new ConstraintTerm(sum, comp, value);
    }

    @Override
    public double getInfeasibility() {
        ConstraintTerm constraint = getConstraintDefinition();
        double functionValue = constraint.getFunction().getValue();
        double conValue = constraint.getValue();
        if ((constraint.getComparator().equals(FunctionComparator.LTE) && functionValue < value)
                ||
                (constraint.getComparator().equals(FunctionComparator.GTE) && functionValue > value)) {
            return 0.0;
        } else {
            return Math.abs(functionValue - conValue);
        }
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
