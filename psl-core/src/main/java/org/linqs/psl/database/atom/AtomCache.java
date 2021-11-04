/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.database.atom;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.util.IteratorUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Storage for {@link GroundAtom GroundAtoms} so that a {@link Database}
 * always returns the same object for a GroundAtom.
 *
 * Also serves as the factory for GroundAtoms for a Database.
 *
 * This class is not thread-safe, but does have some guarentees.
 * All write operations (remove and instantiation) are safe.
 * Read operations do not attempt any thread safety because of their heavy use.
 * However, any call to getCachedAtom() returning a null should be follwed up with an instantiation call.
 * These calls are thread-safe and will check the cache before creation.
 */
public class AtomCache {
    protected final Database db;

    protected final Map<QueryAtom, GroundAtom> cache;

    // The number of random variable and observed atoms that have been instantiated.
    private int rvaCount;
    private int obsCount;

    /**
     * Constructs a new AtomCache for a Database.
     *
     * @param db the Database for which GroundAtoms will be cached
     */
    public AtomCache(Database db) {
        this.db = db;
        this.cache = new HashMap<QueryAtom, GroundAtom>();
        this.rvaCount = 0;
        this.obsCount = 0;
    }

    /**
     * Checks whether a {@link GroundAtom} matching a GetAtom exists in the
     * cache and returns it if so.
     *
     * @param atom GetAtom with all {@link Constant GroundTerms}
     * @return the requested GroundAtom, or NULL if it is not cached
     */
    public GroundAtom getCachedAtom(QueryAtom atom) {
        return cache.get(atom);
    }

    /**
     * @return all GroundAtoms in this AtomCache
     */
    public Iterable<GroundAtom> getCachedAtoms() {
        return cache.values();
    }

    public int getRVACount() {
        return rvaCount;
    }

    public int getObsCount() {
        return obsCount;
    }

    /**
     * Returns all GroundAtoms in this AtomCache with a given Predicate.
     *
     * @param predicate the Predicate of Atoms to return
     * @return the cached Atoms
     */
    public Iterable<GroundAtom> getCachedAtoms(final Predicate predicate) {
        return IteratorUtils.filter(cache.values(), new IteratorUtils.FilterFunction<GroundAtom>() {
            @Override
            public boolean keep(GroundAtom atom) {
                return atom.getPredicate().equals(predicate);
            }
        });
    }

    /**
     * Removes an atom from the AtomCache
     * @param qAtom the Atom to remove
     * @return whether an atom was removed from the cache
     */
    public synchronized boolean removeCachedAtom(QueryAtom qAtom) {
        if (cache.containsKey(qAtom)) {
            GroundAtom atom = cache.remove(qAtom);

            if (atom instanceof RandomVariableAtom) {
                rvaCount--;
            } else if (atom instanceof ObservedAtom) {
                obsCount--;
            }

            return true;
        }

        return false;
    }

    /**
     * @return all ObservedAtoms in this AtomCache
     */
    public Iterable<ObservedAtom> getCachedObservedAtoms() {
        return IteratorUtils.filterClass(cache.values(), ObservedAtom.class);
    }

    /**
     * @return all RandomVariableAtoms in this AtomCache
     */
    public Iterable<RandomVariableAtom> getCachedRandomVariableAtoms() {
        return IteratorUtils.filterClass(cache.values(), RandomVariableAtom.class);
    }

    /**
     * Instantiates an ObservedAtom and stores it in this AtomCache.
     *
     * This method should only be called by this AtomCache's {@link Database}.
     * To retrieve a GroundAtom, all others should use Database.getAtom()
     * or AtomManager.getAtom().
     *
     * Further, this method should only be called after ensuring that the Atom
     * is not already in this AtomCache using {@link #getCachedAtom(QueryAtom)}.
     *
     * @param predicate the Predicate of the Atom
     * @param args the arguments to this Atom
     * @param value the Atom's truth value
     * @return the new ObservedAtom
     */
    public synchronized ObservedAtom instantiateObservedAtom(Predicate predicate, Constant[] args, float value) {
        QueryAtom key = new QueryAtom(predicate, args);

        // Always check the cache before making new atoms.
        if (cache.containsKey(key)) {
            if (!(cache.get(key) instanceof ObservedAtom)) {
                throw new IllegalStateException("Asked to instantiate an observed" +
                        " atom that already exists as a random variable atom (target): " + key);
            }

            return (ObservedAtom)cache.get(key);
        }

        ObservedAtom atom = new ObservedAtom(predicate, args, value);
        cache.put(key, atom);
        obsCount++;

        return atom;
    }

    /**
     * Instantiates a RandomVariableAtom and stores it in this AtomCache.
     *
     * This method should only be called by this AtomCache's {@link Database}.
     * To retrieve a GroundAtom, all others should use Database.getAtom()
     * or AtomManager.getAtom().
     *
     * Further, this method should only be called after ensuring that the Atom
     * is not already in this AtomCache using {@link #getCachedAtom(QueryAtom)}.
     *
     * @param predicate the Predicate of the Atom
     * @param args the arguments to this Atom
     * @param value the Atom's truth value
     * @return the new RandomVariableAtom
     */
    public synchronized RandomVariableAtom instantiateRandomVariableAtom(StandardPredicate predicate, Constant[] args, float value) {
        QueryAtom key = new QueryAtom(predicate, args);

        // Always check the cache before making new atoms.
        if (cache.containsKey(key)) {
            if (!(cache.get(key) instanceof RandomVariableAtom)) {
                throw new IllegalStateException("Asked to instantiate a random variable" +
                        " atom (target) that already exists as an observed atom: " + key);
            }

            return (RandomVariableAtom)cache.get(key);
        }

        RandomVariableAtom atom = new RandomVariableAtom(predicate, args, value);
        cache.put(key, atom);
        rvaCount++;

        return atom;
    }
}
