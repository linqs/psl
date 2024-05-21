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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.WeightedRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A straightforward term store.
 */
public abstract class SimpleTermStore<T extends ReasonerTerm> extends TermStore<T> {
    protected ArrayList<T> allTerms;

    /**
     * A map from component ID to the terms in that component.
     * The component ID is the index of the root atom in the component.
     */
    protected Map<Integer, List<T>> connectedComponents;

    public SimpleTermStore(AtomStore atomStore, TermGenerator<T> termGenerator) {
        super(atomStore, termGenerator);
        allTerms = new ArrayList<T>();
        connectedComponents = new HashMap<Integer, List<T>>();
    }

    /**
     * Make a copy of the simple term store.
     * Note that the objects in the store are copied but may not be in the
     * same exact state as the original, i.e., inference may need to be run.
     */
    public abstract SimpleTermStore<T> copy();

    @Override
    public synchronized int add(ReasonerTerm term) {
        @SuppressWarnings("unchecked")
        T newTerm = (T) term;

        allTerms.add(newTerm);

        // Add to the connected component map.
        int termRootIndex = atomStore.findAtomRoot(atomStore.getAtom(newTerm.atomIndexes[0]));
        GroundAtom rootAtom = atomStore.getAtom(termRootIndex);

        if (connectedComponents.containsKey(termRootIndex)) {
            connectedComponents.get(termRootIndex).add(newTerm);
        } else {
            ArrayList<T> component = new ArrayList<T>();
            component.add(newTerm);
            connectedComponents.put(termRootIndex, component);
        }

        // Unify the components of the atoms in this term.
        for (int i = 1; i < newTerm.size; i++) {
            mergeAtomComponents(rootAtom, atomStore.getAtom(newTerm.atomIndexes[i]));
        }

        // If the term comes from a deep weighted rule, unify the components of the deep atoms.
        if (newTerm.getRule().isWeighted()) {
            Weight weight = ((WeightedRule) newTerm.getRule()).getWeight();
            if (weight.isDeep()) {
                if (!(weight.getAtom() instanceof GroundAtom)) {
                    throw new IllegalArgumentException("WeightedRule's weight must be grounded before creating and adding their terms.");
                }
                mergeAtomComponents(rootAtom, (GroundAtom) weight.getAtom());
            }
        }

        return 1;
    }

    private void mergeAtomComponents(GroundAtom atom1, GroundAtom atom2) {
        int atom1RootIndex = atomStore.findAtomRoot(atom1);
        int atom2RootIndex = atomStore.findAtomRoot(atom2);

        if (atom1RootIndex == atom2RootIndex) {
            // Already in the same component.
            return;
        }

        if (atom1RootIndex == -1 || atom2RootIndex == -1) {
            throw new IllegalArgumentException("Atoms must be in the atom store before they can be merged.");
        }

        GroundAtom atom1Root = atomStore.getAtom(atom1RootIndex);
        GroundAtom atom2Root = atomStore.getAtom(atom2RootIndex);

        atomStore.union(atom1Root, atom2Root);

        // Merge the components.
        if (!connectedComponents.containsKey(atom2RootIndex)) {
            // No component for the next atom.
            return;
        }

        connectedComponents.get(atom1RootIndex).addAll(connectedComponents.get(atom2RootIndex));
        connectedComponents.remove(atom2RootIndex);
    }

    /**
     * Remove any existing terms and prepare for a new set.
     */
    @Override
    public void clear() {
        allTerms.clear();
        connectedComponents.clear();
    }

    @Override
    public void close() {
        super.close();
        allTerms = null;
        connectedComponents = null;
    }

    @Override
    public void ensureCapacity(long capacity) {
        allTerms.ensureCapacity((int)capacity);
    }

    @Override
    public T get(long index) {
        assert(index <= Integer.MAX_VALUE);

        return allTerms.get((int)index);
    }

    public List<T> getAllTerms() {
    	return allTerms;
    }

    public List<T> getConnectedComponent(int componentID) {
        return connectedComponents.get(componentID);
    }

    public Map<Integer, List<T>> getConnectedComponents() {
        return connectedComponents;
    }

    public List<Integer> getConnectedComponentKeys() {
    	return new ArrayList<Integer>(connectedComponents.keySet());
    }

    @Override
    public Iterator<T> iterator() {
        return allTerms.iterator();
    }

    @Override
    public long size() {
        return (long) allTerms.size();
    }
}
