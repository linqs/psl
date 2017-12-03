/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import java.util.HashMap;
import java.util.Map;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;

import com.google.common.collect.Iterables;

/**
 * Storage for {@link GroundAtom GroundAtoms} so that a {@link Database}
 * always returns the same object for a GroundAtom.
 * <p>
 * Also serves as the factory for GroundAtoms for a Database.
 */
public class AtomCache {

	protected final Database db;

	protected final Map<QueryAtom, GroundAtom> cache;
	/**
	 * Constructs a new AtomCache for a Database.
	 *
	 * @param db the Database for which GroundAtoms will be cached
	 */
	public AtomCache(Database db) {
		this.db = db;
		this.cache = new HashMap<QueryAtom, GroundAtom>();
	}

	/**
	 * Checks whether a {@link GroundAtom} matching a QueryAtom exists in the
	 * cache and returns it if so.
	 *
	 * @param atom QueryAtom with all {@link Constant GroundTerms}
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

	/**
	 * Returns all GroundAtoms in this AtomCache with a given Predicate.
	 *
	 * @param p the Predicate of Atoms to return
	 * @return the cached Atoms
	 */
	public Iterable<GroundAtom> getCachedAtoms(final Predicate p) {
		return Iterables.filter(cache.values(), new com.google.common.base.Predicate<GroundAtom>() {
			@Override
			public boolean apply(GroundAtom atom) {
				return atom.getPredicate().equals(p);
			}

		});
	}
	/**
	 * Removes an atom from the AtomCache
	 * @param qAtom the Atom to remove
	 * @return whether an atom was removed from the cache
	 */
	public boolean removeCachedAtom(QueryAtom qAtom) {
		if(cache.containsKey(qAtom)){
			cache.remove(qAtom);
			return true;
		}
		return false;
	}

	/**
	 * @return all ObservedAtoms in this AtomCache
	 */
	public Iterable<ObservedAtom> getCachedObservedAtoms() {
		return Iterables.filter(cache.values(), ObservedAtom.class);
	}

	/**
	 * @return all RandomVariableAtoms in this AtomCache
	 */
	public Iterable<RandomVariableAtom> getCachedRandomVariableAtoms() {
		return Iterables.filter(cache.values(), RandomVariableAtom.class);
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
	 * @param p the Predicate of the Atom
	 * @param args the arguments to this Atom
	 * @param value the Atom's truth value
	 * @return the new ObservedAtom
	 */
	public ObservedAtom instantiateObservedAtom(Predicate p, Constant[] args,
			double value) {
		QueryAtom key = new QueryAtom(p, args);

		// Always check the cache before making new atoms.
		if (cache.containsKey(key)) {
			if (!(cache.get(key) instanceof ObservedAtom)) {
				throw new IllegalArgumentException("Asked to instantiate an observed" +
						" atom that already exists as a random variable atom: " + key);
			}

			return (ObservedAtom)cache.get(key);
		}

		ObservedAtom atom = new ObservedAtom(p, args, db, value);
		cache.put(key, atom);

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
	 * @param p the Predicate of the Atom
	 * @param args the arguments to this Atom
	 * @param value the Atom's truth value
	 * @return the new RandomVariableAtom
	 */
	public RandomVariableAtom instantiateRandomVariableAtom(StandardPredicate p,
			Constant[] args, double value) {
		QueryAtom key = new QueryAtom(p, args);

		// Always check the cache before making new atoms.
		if (cache.containsKey(key)) {
			if (!(cache.get(key) instanceof RandomVariableAtom)) {
				throw new IllegalArgumentException("Asked to instantiate a random variable" +
						" atom that already exists as an observed atom: " + key);
			}

			return (RandomVariableAtom)cache.get(key);
		}

		RandomVariableAtom atom = new RandomVariableAtom(p, args, db, value);
		cache.put(key, atom);

		return atom;
	}
}
