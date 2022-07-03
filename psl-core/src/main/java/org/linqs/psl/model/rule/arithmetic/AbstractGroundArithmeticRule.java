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
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.util.ListUtils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Base class for all ground arithmetic rules.
 */
public abstract class AbstractGroundArithmeticRule implements GroundRule {
    protected final AbstractArithmeticRule rule;
    protected final float[] coefficients;
    protected final GroundAtom[] atoms;
    protected final FunctionComparator comparator;
    protected final float constant;

    protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
            List<Float> coefficients, List<GroundAtom> atoms, FunctionComparator comparator, float constant) {
        this(rule, ListUtils.toPrimitiveFloatArray(coefficients),
                atoms.toArray(new GroundAtom[0]), comparator, constant, false);
    }

    protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
            float[] coefficients, GroundAtom[] atoms, FunctionComparator comparator, float constant) {
        this(rule, coefficients, atoms, comparator, constant, true);
    }

    protected AbstractGroundArithmeticRule(AbstractArithmeticRule rule,
            float[] coefficients, GroundAtom[] atoms, FunctionComparator comparator, float constant,
            boolean copy) {
        this.rule = rule;
        this.comparator = comparator;
        this.constant = constant;

        // Because of the semantics of arithmetic rules, it is possible to get to this point with a null atom.
        // In that case, remove the null atoms and re-allocate the atoms,
        int missingAtoms = 0;
        for (int i = 0; i < atoms.length; i++) {
            if (atoms[i] == null) {
                missingAtoms++;
            }
        }

        if (missingAtoms > 0) {
            this.coefficients = new float[coefficients.length - missingAtoms];
            this.atoms = new GroundAtom[atoms.length - missingAtoms];

            int tempIndex = 0;
            for (int i = 0; i < atoms.length; i++) {
                if (atoms[i] != null) {
                    this.coefficients[tempIndex] = coefficients[i];
                    this.atoms[tempIndex] = atoms[i];
                    tempIndex++;
                }
            }
        } else if (copy) {
            // No atoms are missing and we want to copy over the data.
            this.coefficients = Arrays.copyOf(coefficients, coefficients.length);
            this.atoms = Arrays.copyOf(atoms, atoms.length);
        } else {
            // No missing atoms, and no copying.
            this.coefficients = coefficients;
            this.atoms = atoms;
        }
    }

    @Override
    public Rule getRule() {
        return rule;
    }

    @Override
    public Set<GroundAtom> getAtoms() {
        Set<GroundAtom> atomSet = new HashSet<GroundAtom>();
        for (GroundAtom atom : atoms) {
            atomSet.add(atom);
        }
        return atomSet;
    }

    @Override
    public List<GroundRule> negate() {
        // TODO(eriq)
        throw new UnsupportedOperationException("Negating arithmetic rules not yet supported.");
    }

    @Override
    public String baseToString() {
        StringBuilder sb = new StringBuilder();

        // If there are coefficients, print each one.
        if (coefficients.length > 0) {
            for (int i = 0; i < coefficients.length; i++) {
                if (i != 0) {
                    sb.append(" + ");
                }

                sb.append(coefficients[i]);
                sb.append(" * ");
                sb.append(atoms[i]);
            }
        } else {
            // Otherwise, just put in a zero.
            sb.append("0.0");
        }

        sb.append(" ");
        sb.append(comparator.toString());

        sb.append(" ");
        sb.append(constant);

        return sb.toString();
    }

    @Override
    public String toString() {
        return baseToString();
    }

    public float[] getCoefficients() {
        return coefficients;
    }

    public GroundAtom[] getOrderedAtoms() {
        return atoms;
    }

    public FunctionComparator getComparator() {
        return comparator;
    }

    public float getConstant() {
        return constant;
    }
}
