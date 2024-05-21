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

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.FormulaAnalysis;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.term.Constant;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Allocated resources needed for grounding.
 * This will be stashed in the thread objects so each thread will have one.
 */
public class GroundingResources {
    // Remember that these are positive/negative in the CNF.
    public List<GroundAtom> positiveAtoms;
    public List<GroundAtom> negativeAtoms;

    public QueryAtom weightQueryAtom;
    public GroundAtom weightGroundAtom;
    public Constant[] weightArgumentsBuffer;


    // Atoms that cause trouble for the atom manager.
    public Set<GroundAtom> accessExceptionAtoms;

    // Allocate up-front some buffers for grounding QueryAtoms into.
    public Constant[][] positiveAtomArgs;
    public Constant[][] negativeAtomArgs;

    public GroundingResources() {
        positiveAtoms = null;
        negativeAtoms = null;
        accessExceptionAtoms = null;

        positiveAtomArgs = null;
        negativeAtomArgs = null;

        weightQueryAtom = null;
        weightGroundAtom = null;
        weightArgumentsBuffer = null;
    }

    public void parseNegatedDNF(FormulaAnalysis.DNFClause negatedDNF, Weight weight) {
        positiveAtoms = new ArrayList<GroundAtom>(4);
        negativeAtoms = new ArrayList<GroundAtom>(4);
        accessExceptionAtoms = new HashSet<GroundAtom>(4);

        positiveAtomArgs = new Constant[negatedDNF.getPosLiterals().size()][];
        for (int i = 0; i < negatedDNF.getPosLiterals().size(); i++) {
            positiveAtomArgs[i] = new Constant[negatedDNF.getPosLiterals().get(i).getArity()];
        }

        negativeAtomArgs = new Constant[negatedDNF.getNegLiterals().size()][];
        for (int i = 0; i < negatedDNF.getNegLiterals().size(); i++) {
            negativeAtomArgs[i] = new Constant[negatedDNF.getNegLiterals().get(i).getArity()];
        }

        if ((weight != null) && (weight.isDeep())) {
            assert (weight.getAtom() instanceof QueryAtom);

            weightQueryAtom = (QueryAtom) weight.getAtom();
            weightArgumentsBuffer = new Constant[weightQueryAtom.getArity()];
        }
    }
}