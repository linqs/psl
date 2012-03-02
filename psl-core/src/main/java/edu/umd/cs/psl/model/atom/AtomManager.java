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

import edu.umd.cs.psl.application.ModelApplication;
import edu.umd.cs.psl.database.Database;
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
 * Canonical source of all ground {@link Atom Atoms} used by a {@link ModelApplication}.
 * 
 * Implementations might support initialization with a {@link Database} as a source
 * of fixed Atoms and as a location to persist Atoms.
 * 
 * An AtomManager is responsible for maintaining the set of ground Atoms being used
 * by a ModelApplication and a single interpretation (assignment of truth values)
 * over those Atoms. An AtomManager also maintains confidence values representing
 * confidence in the assigned truth value of each Atom (to the degree confidence
 * values are used by the particular ModelApplication).
 * 
 * AtomManagers maintain much of this information implicitly through two conventions.
 * First, upon initialization, any ground Atoms without fixed truth values are initially
 * assigned a truth value of zero. Second, if a {@link Predicate} is closed in an
 * AtomManager, then any ground Atom of that Predicate which does not have a specified,
 * fixed truth value is fixed at zero. (The method(s) for specifying fixed truth
 * values is implementation specific.) 
 * 
 * AtomManagers are also responsible for managing the events and status changes related
 * to Atoms.
 * 
 * @see AtomEvent
 * @see AtomStatus
 */
public interface AtomManager extends DatabaseEventObserver, ModelEvent.Listener {
	
	/**
	 * Returns the specified ground Atom.
	 * 
	 * This method should be used by {@link ModelApplication ModelApplications}
	 * and their delegates to access ground Atoms. This ensures that a ground Atom
	 * instantiation for a given Predicate and arguments is unique
	 * in a ModelApplication.
	 * 
	 * An atom returned by this method that did not have a considered or active
	 * status before will become considered.
	 * 
	 * @param p  the Predicate of the Atom
	 * @param arguments  the (ground) arguments of the Atom
	 * @return the specified Atom
	 * @see AtomStatus#isActiveOrConsidered()
	 */
	public Atom getAtom(Predicate p, GroundTerm[] arguments);

	/**
	 * Persists an Atom with an underlying storage mechanism (optional operation).
	 * 
	 * Where the Atom will be persisted is implementation specific. If this
	 * AtomManager was instantiated with a {@link Database}, then the Atom should
	 * be persisted there.
	 * 
	 * @param atom  the Atom to persist
	 */
	public void persist(Atom atom);
	
	/**
	 * Returns all groundings of a Formula which do not evaluate to a truth
	 * value of zero under the current interpretation.
	 * 
	 * @param f  the Formula to ground
	 * @return a list of substitutions of {@link GroundTerm GroundTerms} for {@link Variable Variables}
	 */
	public ResultList getNonfalseGroundings(Formula f);
	
	/**
	 * Returns all groundings of a Formula which do not evaluate to a truth
	 * value of zero under the current interpretation.
	 * 
	 * The returned groundings are projected onto the specified {@link Variable Variables}.
	 * 
	 * @param f  the Formula to ground
	 * @param projectTo  the Variables onto which the groundings will be projected
	 * @return a list of substitutions of {@link GroundTerm GroundTerms} for Variables
	 */
	public ResultList getNonfalseGroundings(Formula f, List<Variable> projectTo);
	
	/**
	 * Returns all groundings of a Formula which do not evaluate to a truth
	 * value of zero under the current interpretation.
	 * 
	 * Additionally, the groundings must match the given partial grounding. The partial
	 * grounding will <em>not</em> be included in the returned substitutions.
	 * 
	 * @param f  the Formula to ground
	 * @param partialGrounding  a partial substitution of {@link Variable Variables} which
	 *                              each returned grounding must match
	 * @return a list of substitutions of {@link GroundTerm GroundTerms} for Variables
	 */
	public ResultList getNonfalseGroundings(Formula f, VariableAssignment partialGrounding);
	
	/**
	 * Returns all groundings of a Formula which do not evaluate to a truth
	 * value of zero under the current interpretation.
	 * 
	 * The returned groundings are projected onto the specified {@link Variable Variables}.
	 * Additionally, the groundings must match the given partial grounding. The partial
	 * grounding for a particular Variable will only be included in the returned
	 * substitutions <em>if</em> that Variable is included in the projection Variables.
	 * 
	 * @param f  the Formula to ground
	 * @param partialGrounding  a partial substitution of Variables which
	 *                              each returned grounding must match
	 * @param projectTo  the Variables onto which the groundings will be projected
	 * @return a list of substitutions of {@link GroundTerm GroundTerms} for Variables
	 */
	public ResultList getNonfalseGroundings(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo);
	
	/**
	 * Returns all ground Atoms with any of the given statuses.
	 * 
	 * The set of statuses must be a subset of {@link AtomStatusSets#ActiveOrConsidered}.
	 * 
	 * @param stati  the set of statuses with which to select Atoms
	 * @return an iterable structure of Atoms
	 * @throws IllegalArgumentException  if stati is not a subset of
	 *                                       {@link AtomStatusSets#ActiveOrConsidered}
	 */
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati);
	
	/**
	 * Returns all ground Atoms with a given Predicate and any of the given statuses.
	 * 
	 * The set of statuses must be a subset of {@link AtomStatusSets#ActiveOrConsidered}.
	 * 
	 * @param stati  the set of statuses with which to select Atoms
	 * @param p  the Predicate of the Atoms to return
	 * @return an iterable structure of Atoms
	 * @throws IllegalArgumentException  if stati is not a subset of
	 *                                       {@link AtomStatusSets#ActiveOrConsidered}
	 */
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati, Predicate p);
	
	/**
	 * Returns all ground Atoms with a given Predicate and any of the given statuses,
	 * and which have arguments which match the specified terms.
	 * 
	 * The set of statuses must be a subset of {@link AtomStatusSets#ActiveOrConsidered}.
	 * 
	 * @param stati  the set of statuses with which to select Atoms
	 * @param p  the Predicate of the Atoms to return
	 * @param terms  arguments which Atoms must match. {@link Variable Variables} can
	 *                   be used as wildcards.
	 * @return an iterable structure of Atoms
	 * @throws IllegalArgumentException  if stati is not a subset of
	 *                                       {@link AtomStatusSets#ActiveOrConsidered}
	 */
	public Iterable<Atom> getAtoms(Set<AtomStatus> stati, Predicate p, Object...terms);
	
	/**
	 * Returns the number of ground Atoms with any of the given statuses.
	 * 
	 * The set of statuses must be a subset of {@link AtomStatusSets#ActiveOrConsidered}.
	 * 
	 * @param stati  the set of statuses with which to select Atoms
	 * @return an iterable structure of Atoms
	 * @throws IllegalArgumentException  if stati is not a subset of
	 *                                       {@link AtomStatusSets#ActiveOrConsidered}
	 */
	public int getNumAtoms(Set<AtomStatus> stati);
	
	/**
	 * Returns the number of ground Atoms with a given Predicate and any of
	 * the given statuses.
	 * 
	 * The set of statuses must be a subset of {@link AtomStatusSets#ActiveOrConsidered}.
	 * 
	 * @param stati  the set of statuses with which to select Atoms
	 * @param p  the Predicate of the Atoms to return
	 * @return an iterable structure of Atoms
	 * @throws IllegalArgumentException  if stati is not a subset of
	 *                                       {@link AtomStatusSets#ActiveOrConsidered}
	 */
	public int getNumAtoms(Set<AtomStatus> stati, Predicate p);
	
	/**
	 * Returns the number ground Atoms with a given Predicate and any of the given
	 * statuses, and which have arguments which match the specified terms.
	 * 
	 * The set of statuses must be a subset of {@link AtomStatusSets#ActiveOrConsidered}.
	 * 
	 * @param stati  the set of statuses with which to select Atoms
	 * @param p  the Predicate of the Atoms to return
	 * @param terms  arguments which Atoms must match. {@link Variable Variables} can
	 *                   be used as wildcards.
	 * @return an iterable structure of Atoms
	 * @throws IllegalArgumentException  if stati is not a subset of
	 *                                       {@link AtomStatusSets#ActiveOrConsidered}
	 */
	public int getNumAtoms(Set<AtomStatus> stati, Predicate p, Object...terms);
	
	/**
	 * Registers a listener for any events in a set.
	 * 
	 * @param events  set of events for which to listen
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventObserver(AtomEventSets events, AtomEvent.Listener listener);
	
	/**
	 * Registers a listener for any events in a set related to {@link Atom Atoms}
	 * with a given Predicate.
	 * 
	 * @param events  set of events for which to listen
	 * @param p  Predicate of Atoms for which to listen for events
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventObserver(AtomEventSets events, Predicate p, AtomEvent.Listener listener);
	
	/**
	 * Unregisters a listener for any events in a set.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventObserver(AtomEventSets events, AtomEvent.Listener listener);
	
	/**
	 * Unregisters a listener for any events in a set related to {@link Atom Atoms}
	 * with a given Predicate.
	 * 
	 * @param events  set of events for which to listen
	 * @param p  Predicate of Atoms for which to listen for events
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventObserver(AtomEventSets events, Predicate p, AtomEvent.Listener listener);
	
	/**
	 * Activates all (ground) {@link Atom Atoms} which meet this manager's
	 * activation criteria.
	 * 
	 * @return number of Atoms activated
	 * @see AtomManager#workOffJobQueue()
	 */
	public int checkToActivate();
	
	/**
	 * Deactivates all (ground) {@link Atom Atoms} which meet this manager's
	 * deactivation criteria.
	 * 
	 * @return number of Atoms deactivated
	 * @see AtomManager#workOffJobQueue()
	 */
	public int checkToDeactivate();
	
	/**
	 * Activates an Atom if it meets this manager's activation criteria.
	 * 
	 * @param atom  Atom to activate
	 * @return True if atom was activated, otherwise False
	 * @see AtomManager#workOffJobQueue()
	 */
	public boolean activateAtom(Atom atom);
	
	/**
	 * Deactivates an Atom if it meets this manager's deactivation criteria.
	 * 
	 * @param atom  Atom to deactivate
	 * @return True if atom was deactivated, otherwise False
	 * @see AtomManager#workOffJobQueue()
	 */
	public boolean deactivateAtom(Atom atom);
	
	/**
	 * Releases a (ground) Atom if it meets this manager's release criteria.
	 * 
	 * A released Atom is no longer considered. In other words, it is released
	 * from memory and will be treated as an implicit Atom with a truth value of
	 * zero, unless it is reconsidered.
	 * 
	 * @param atom  Atom to release
	 * @return True if atom was released, otherwise False
	 * @see AtomManager#workOffJobQueue()
	 */
	public boolean release(Atom atom);
	
	/**
	 * Opens a Predicate.
	 * 
	 * If a {@link Predicate} is open, then any ground {@link Atom} of that
	 * Predicate which does not have a specified, fixed truth value will be
	 * treated as a random variable. (The method(s) for specifying fixed truth
	 * values is implementation specific.)
	 * 
	 * @param predicate  Predicate to open
	 * @see AtomManager#workOffJobQueue()
	 */
	public void open(Predicate predicate);
	
	/**
	 * Closes a Predicate.
	 * 
	 * If a {@link Predicate} is closed, then any ground Atom of that Predicate
	 * which does not have a specified, fixed truth value is fixed at zero.
	 * (The method(s) for specifying fixed truth values is implementation specific.)
	 * 
	 * @param predicate  Predicate to close
	 * @see AtomManager#workOffJobQueue()
	 */
	public void close(Predicate predicate);
	
	/**
	 * Processes all pending {@link AtomJob AtomJobs}.
	 * 
	 * This method should be called after changing {@link Atom} values, activating
	 * Atoms, opening or closing {@link Predicate Predicates}, or any other
	 * operation which could change the {@link AtomStatus} of an Atom.
	 */
	public void workOffJobQueue();
	
	public Entity getEntity(Object entity, ArgumentType type);
	
	public Set<Entity> getEntities(ArgumentType type);
	
	public int getNumEntities(ArgumentType type);
	
}
