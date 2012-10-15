/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
package edu.umd.cs.psl.model.atom;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.AggregatePredicate;
import edu.umd.cs.psl.model.predicate.FunctionalPredicate;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * Storage for {@link GroundAtom GroundAtoms} so that a {@link Database}
 * always returns the same object for a GroundAtom.
 * <p>
 * Also serves as the factory for GroundAtoms for a Database.
 */
public class AtomCache {
	
	private final Database db;
	
	/**
	 * Constructs a new AtomCache for a Database.
	 * 
	 * @param db  the Database for which GroundAtoms will be cached
	 */
	public AtomCache(Database db) {
		this.db = db;
	}
	
	/**
	 * Checks whether a {@link GroundAtom} matching a QueryAtom exists in the
	 * cache and returns it if so.
	 * 
	 * @param atom  QueryAtom with all {@link GroundTerm GroundTerms}
	 * @return the requested GroundAtom, or NULL if it is not cached
	 * @throws IllegalArgumentException  if atom has any Variables as arguments
	 */
	public GroundAtom getCachedAtom(QueryAtom atom) {
		return null;
	}
	
	/**
	 * @return all GroundAtoms in this AtomCache
	 */
	public Iterable<GroundAtom> getCachedAtoms() {
		return null;
	}
	
	/**
	 * @return all AggregateAtoms in this AtomCache
	 */
	public Iterable<AggregateAtom> getCachedAggregateAtoms() {
		return null;
	}
	
	/**
	 * @return all FunctionalAtoms in this AtomCache
	 */
	public Iterable<FunctionalAtom> getCachedFunctionalAtoms() {
		return null;
	}
	
	/**
	 * @return all ObservedAtoms in this AtomCache
	 */
	public Iterable<ObservedAtom> getCachedObservedAtoms() {
		return null;
	}
	
	/**
	 * @return all RandomVariableAtoms in this AtomCache
	 */
	public Iterable<RandomVariableAtom> getCachedRandomVariableAtoms() {
		return null;
	}
	
	/**
	 * Initializes an AggregateAtom and stores it in this AtomCache.
	 * <p>
	 * This method should only be called by this AtomCache's {@link Database}.
	 * To retrieve a GroundAtom, all others should use {@link Database#getAtom(Predicate, GroundTerm[])}
	 * or {@link AtomManager#getAtom(Predicate, GroundTerm[])}.
	 * <p>
	 * Further, this method should only be called after ensuring that the Atom
	 * is not already in this AtomCache using {@link #getCachedAtom(QueryAtom)}.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param args  the arguments to this Atom
	 * @param value  this Atom's truth value
	 * @return the new AggregateAtom
	 */
	public AggregateAtom initializeAggregateAtom(AggregatePredicate p,
			GroundTerm[] args, double value) {
		AggregateAtom atom = new AggregateAtom(p, args, db, value);
		// TODO
		return atom;
	}
	
	/**
	 * Initializes a FunctionalAtom and stores it in this AtomCache.
	 * <p>
	 * This method should only be called by this AtomCache's {@link Database}.
	 * To retrieve a GroundAtom, all others should use {@link Database#getAtom(Predicate, GroundTerm[])}
	 * or {@link AtomManager#getAtom(Predicate, GroundTerm[])}.
	 * <p>
	 * Further, this method should only be called after ensuring that the Atom
	 * is not already in this AtomCache using {@link #getCachedAtom(QueryAtom)}.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param args  the arguments to this Atom
	 * @param value  this Atom's truth value
	 * @return the new FunctionalAtom
	 */
	public FunctionalAtom initializeFunctionalAtom(FunctionalPredicate p,
			GroundTerm[] args, double value) {
		FunctionalAtom atom = new FunctionalAtom(p, args, db, value);
		// TODO
		return atom;
	}
	
	/**
	 * Initializes an ObservedAtom and stores it in this AtomCache.
	 * <p>
	 * This method should only be called by this AtomCache's {@link Database}.
	 * To retrieve a GroundAtom, all others should use {@link Database#getAtom(Predicate, GroundTerm[])}
	 * or {@link AtomManager#getAtom(Predicate, GroundTerm[])}.
	 * <p>
	 * Further, this method should only be called after ensuring that the Atom
	 * is not already in this AtomCache using {@link #getCachedAtom(QueryAtom)}.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param args  the arguments to this Atom
	 * @param value  this Atom's truth value
	 * @return the new ObservedAtom
	 */
	public ObservedAtom initializeObservedAtom(StandardPredicate p, GroundTerm[] args,
			double value) {
		ObservedAtom atom = new ObservedAtom(p, args, db, value);
		// TODO
		return atom;
	}
	
	/**
	 * Initializes a RandomVariableAtom and stores it in this AtomCache.
	 * <p>
	 * This method should only be called by this AtomCache's {@link Database}.
	 * To retrieve a GroundAtom, all others should use {@link Database#getAtom(Predicate, GroundTerm[])}
	 * or {@link AtomManager#getAtom(Predicate, GroundTerm[])}.
	 * <p>
	 * Further, this method should only be called after ensuring that the Atom
	 * is not already in this AtomCache using {@link #getCachedAtom(QueryAtom)}.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param args  the arguments to this Atom
	 * @param value  this Atom's truth value
	 * @return the new RandomVariableAtom
	 */
	public RandomVariableAtom initializeRandomVariableAtom(StandardPredicate p,
			GroundTerm[] args, double value, double confidence, boolean fixed) {
		RandomVariableAtom atom = new RandomVariableAtom(p, args, db, value, confidence, fixed);
		// TODO
		return atom;
	}
}
