/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.grounding.collective;

import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.util.MathUtils;

/**
 * A container for grounding query candidate search nodes.
 */
public class CandidateSearchNode implements Comparable<CandidateSearchNode> {
    public final long atomsBitSet;
    public final Formula formula;
    public final int numAtoms;

    public boolean approximateCost;
    public double optimisticCost;
    public double pessimisticCost;

    public CandidateSearchNode(long atomsBitSet, Formula formula, int numAtoms, double optimisticCost, double pessimisticCost) {
        this.atomsBitSet = atomsBitSet;
        this.formula = formula;
        this.numAtoms = numAtoms;

        approximateCost = true;
        this.optimisticCost = optimisticCost;
        this.pessimisticCost = pessimisticCost;
    }

    @Override
    public String toString() {
        return String.format("{Atom Bits: %d, Optimistic: %f, Pessimistic: %f, Approximate: %s, Formula: %s}",
                atomsBitSet, optimisticCost, pessimisticCost, approximateCost, formula);
    }

    @Override
    public int compareTo(CandidateSearchNode other) {
        if (other == null) {
            return 1;
        }

        return MathUtils.compare(this.optimisticCost, other.optimisticCost);
    }

    @Override
    public int hashCode() {
        return (int)atomsBitSet;
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof CandidateSearchNode) || (hashCode() != other.hashCode())) {
            return false;
        }

        if (other == this) {
            return true;
        }

        CandidateSearchNode otherNode = (CandidateSearchNode)other;
        return (otherNode.atomsBitSet == this.atomsBitSet) && (otherNode.formula.equals(this.formula));
    }
}
