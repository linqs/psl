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
package org.linqs.psl.reasoner.term.blocker;

import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.arithmetic.UnweightedGroundArithmeticRule;
import org.linqs.psl.model.rule.misc.GroundValueConstraint;
import org.linqs.psl.reasoner.term.ReasonerTerm;
import org.linqs.psl.util.RandUtils;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A Term to hold blocks.
 * See {@link ConstraintBlockerTermGenerator} for details on the constraint blocking process.
 */
public class ConstraintBlockerTerm implements ReasonerTerm {
    private RandomVariableAtom[] atoms;
    private WeightedGroundRule[] incidentGRs;
    private boolean exactlyOne;

    /**
     * Takes ownership of all the passed in arrays.
     */
    public ConstraintBlockerTerm(RandomVariableAtom[] atoms, WeightedGroundRule[] incidentGRs, boolean exactlyOne) {
        this.atoms = atoms;
        this.incidentGRs = incidentGRs;
        this.exactlyOne = exactlyOne;
    }

    public RandomVariableAtom[] getAtoms() {
        return atoms;
    }

    public WeightedGroundRule[] getIncidentGRs() {
        return incidentGRs;
    }

    public boolean getExactlyOne() {
        return exactlyOne;
    }

    @Override
    public int size() {
        return atoms.length;
    }

    /**
     * Randomly initializes the RandomVariableAtoms to a feasible state.
     */
    public void randomlyInitialize() {
        for (RandomVariableAtom atom : atoms) {
            atom.setValue(0.0f);
        }

        if (atoms.length > 0 && exactlyOne) {
            atoms[RandUtils.nextInt(atoms.length)].setValue(1.0f);
        }
    }
}
