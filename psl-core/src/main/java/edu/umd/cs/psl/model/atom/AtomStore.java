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

import java.util.Set;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.*;

/**
 * An interface for atom storage classes.
 * 
 * @author
 *
 */
public interface AtomStore {

	/**
	 * Adds an atom to the store.
	 * 
	 * @param atom An atom
	 */
	public void store(Atom atom);
	
	/**
	 * Frees an atom (removes it from the store).
	 * 
	 * @param atom Atom to be freed
	 */
	public void free(Atom atom);
	
	/**
	 * Returns the atom pertaining to the given predicate and ground terms.
	 * 
	 * @param p A predicate
	 * @param arguments An array of ground terms.
	 * @return An atom
	 */
	public Atom getAtom(Predicate p, GroundTerm[] arguments);
	
	/**
	 * Returns the considered atoms pertaining to the given predicate and ground terms.
	 * 
	 * @param p A predicate
	 * @param arguments An array of ground terms
	 * @return An atom
	 */
	public Atom getConsideredAtom(Predicate p, GroundTerm[] arguments);
	
	/**
	 * Returns all atoms with the given status.
	 * 
	 * @param status A status
	 * @return An iterable structure containing atoms
	 */
	public Iterable<Atom> getAtoms(AtomStatus status);

	/**
	 * Returns all atoms with any of the given statuses.
	 * 
	 * @param stati A set of statuses
	 * @return An iterable structure containing atoms
	 */
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati);
	
	/**
	 * Returns the number of atoms with the given statuses.
	 * 
	 * @param stati A set of statuses
	 * @return The number of atoms
	 */
	public int getNumAtoms(Set<AtomStatus> stati);
	
	/**
	 * Returns the database associated with this atom store.
	 * 
	 * @return A database
	 */
	public Database getDatabase();
	
//	public Atom initializeFactAtom(Predicate p, GroundTerm[] terms, double[] values, double[] confidences);
//	
//	public Atom initializeCertaintyAtom(Predicate p, GroundTerm[] terms, double[] values, double[] confidences);
//	
//	public Atom initializeRVAtom(Predicate p, GroundTerm[] terms, double[] values, double[] confidences);
//	
//	public Atom initializeRVAtom(Predicate p, GroundTerm[] terms);

	
}
