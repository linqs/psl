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

import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.application.GroundingMode;
import edu.umd.cs.psl.database.DatabaseEventObserver;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * The atom manager interface contains base functionality for {@link AtomEventFramework}. 
 * @author
 *
 */
public interface AtomManager extends DatabaseEventObserver, ModelEvent.Listener {

	public Atom getAtom(Predicate p, GroundTerm[] arguments);

	public void changeCertainty(Atom atom, double[] values,	double[] confidences);
	
	public void release(Atom atom);
	
	public void checkToDeactivate(Atom atom);

	public void persist(Atom atom);
	
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
	
	public List<Atom> getConsideredAtoms(Predicate p);
	
	public List<Atom> getConsideredAtoms(Predicate p, Object...terms);
	
	/**
	 * Returns the number of atoms with the given statuses.
	 * 
	 * @param stati A set of statuses
	 * @return The number of atoms
	 */
	public int getNumAtoms(Set<AtomStatus> stati);
	
	public ResultList getNonzeroGroundings(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo);
	
	public ResultList getNonzeroGroundings(Formula f, VariableAssignment partialGrounding);
	
	public ResultList getNonzeroGroundings(Formula f, List<Variable> projectTo);
	
	public ResultList getNonzeroGroundings(Formula f);
	
	/**
	 * Determines when an atom is activated.
	 *   NonDefault: whenever it crosses the activation threshold
	 *   All: any event
	 */
	public static enum ActivationMode { NonDefault, All }
	public static final ActivationMode defaultActivationMode = ActivationMode.NonDefault;
	
	/**
	 * Unregisters an observer for a given set of atom events.
	 * 
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void unregisterAtomEventObserver(AtomEventSets event, AtomEventObserver me);
	
	/**
	 * Unregisters an observer for a given predicate and set of atom events.
	 * 
	 * @param p A predicate
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void unregisterAtomEventObserver(Predicate p, AtomEventSets event, AtomEventObserver me);
	
	/**
	 * Registers an observer for a given set of atom events.
	 * 
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void registerAtomEventObserver(AtomEventSets event, AtomEventObserver me);
	
	/**
	 * Registers an observer for a given predicate and set of atom events.
	 * 
	 * @param p A predicate
	 * @param event A set of atom events
	 * @param me An observer
	 */
	public void registerAtomEventObserver(Predicate p, AtomEventSets event, AtomEventObserver me);
	
	public void workOffJobQueue();
	
	public boolean activateAtom(Atom atom);
	
	public boolean deactivateAtom(Atom atom);
	
	public int checkToActivate();
	
	public void setGroundingMode(GroundingMode mode);
	
	public Entity getEntity(Object entity, ArgumentType type);
	
	public Set<Entity> getEntities(ArgumentType type);
	
	public int getNumEntities(ArgumentType type);
	
}
