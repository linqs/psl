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
import org.linqs.psl.reasoner.term.ReasonerLocalVariable;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.RandUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A TermStore to hold blocks.
 * See {@link ConstraintBlockerTermGenerator} for details on the constraint blocking process.
 */
public class ConstraintBlockerTermStore implements TermStore<ConstraintBlockerTerm, RandomVariableAtom> {
    private ArrayList<ConstraintBlockerTerm> blocks;
    private Map<RandomVariableAtom, Integer> rvMap;
    private GroundRuleStore groundRuleStore;

    public ConstraintBlockerTermStore() {
        blocks = new ArrayList<ConstraintBlockerTerm>();
        rvMap = new HashMap<RandomVariableAtom, Integer>();
        groundRuleStore = null;
    }

    public void init(GroundRuleStore groundRuleStore,
            RandomVariableAtom[][] rvBlocks, WeightedGroundRule[][] incidentGRs,
            boolean[] exactlyOne) {
        assert(rvBlocks.length == incidentGRs.length);
        assert(rvBlocks.length == exactlyOne.length);

        this.groundRuleStore = groundRuleStore;
        ensureCapacity(blocks.size() + rvBlocks.length);

        for (int i = 0; i < rvBlocks.length; i++) {
            Integer blockIndex = new Integer(blocks.size());
            blocks.add(new ConstraintBlockerTerm(rvBlocks[i], incidentGRs[i], exactlyOne[i]));
            for (RandomVariableAtom atom : rvBlocks[i]) {
                rvMap.put(atom, blockIndex);
            }
        }
    }

    /**
     * Extremely hacky way to allow methods that require this to get ahold of the GroundRuleStore.
     */
    public GroundRuleStore getGroundRuleStore() {
        return groundRuleStore;
    }

    /**
     * Get the index of the block (term) associated with the given atom.
     * @return the index or -1 if the atom is not in any blocks.
     */
    public int getBlockIndex(RandomVariableAtom atom) {
        Integer index = rvMap.get(atom);

        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    /**
     * Randomly initializes the RandomVariableAtoms to a feasible state.
     */
    public void randomlyInitialize() {
        for (ConstraintBlockerTerm block : blocks) {
            block.randomlyInitialize();
        }
    }

    @Override
    public void add(GroundRule rule, ConstraintBlockerTerm term) {
        throw new UnsupportedOperationException("ConstraintBlockerTermStore needs all ground rules at once, use init().");
    }

    @Override
    public void clear() {
        if (blocks != null) {
            blocks.clear();
        }

        if (rvMap != null) {
            rvMap.clear();
        }
    }

    @Override
    public void close() {
        clear();

        blocks = null;
        rvMap = null;
        groundRuleStore = null;
    }

    @Override
    public ConstraintBlockerTerm get(int index) {
        return blocks.get(index);
    }

    @Override
    public int size() {
        return blocks.size();
    }

    @Override
    public void ensureCapacity(int capacity) {
        assert(capacity >= 0);

        if (capacity == 0) {
            return;
        }

        blocks.ensureCapacity(capacity);

        // If the map is empty, then just reallocate it
        // (since we can't add capacity).
        if (rvMap.size() == 0) {
            // The default load factor for Java HashMaps is 0.75.
            // Assume 2 atoms per block.
            rvMap = new HashMap<RandomVariableAtom, Integer>((int)(capacity * 2 / 0.75));
        }
    }

    @Override
    public Iterator<ConstraintBlockerTerm> iterator() {
        return blocks.iterator();
    }

    @Override
    public Iterator<ConstraintBlockerTerm> noWriteIterator() {
        return iterator();
    }

    @Override
    public RandomVariableAtom createLocalVariable(RandomVariableAtom atom) {
        throw new UnsupportedOperationException("ConstraintBlockerTermStore does not use the concept of local variables.");
    }

    @Override
    public void ensureVariableCapacity(int capacity) {
    }
}
