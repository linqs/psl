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
import edu.umd.cs.psl.application.groundkernelstore.GroundKernelStore;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.ModelEvent;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Variable;
import edu.umd.cs.psl.model.formula.Conjunction;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.predicate.Predicate;

/**
 * Canonical source of a set of ground {@link Atom Atoms} used
 * by a {@link ModelApplication}.
 * 
 * <h2>Overview</h2>
 * 
 * An AtomManager is responsible for maintaining a set of ground Atoms being used
 * by a ModelApplication and a single interpretation (assignment of truth values)
 * over those Atoms. An AtomManager also maintains confidence values representing
 * confidence in the assigned truth value of each Atom (to the degree confidence
 * values are used by the particular ModelApplication).
 * 
 * <h2>Initialization</h2>
 * 
 * Implementations should be initialized with a {@link Model} and a {@link ConfigBundle}.
 * Additionally, implementations might support initialization with a {@link Database} as a
 * source of observed Atoms and/or as a location to persist Atoms.
 * <p>
 * All observed Atoms must be specified at initialization. The interpretation
 * initially (sometimes implicitly) assigns a truth value of zero to all unobserved Atoms.
 * 
 * <h2>Queries and Activation</h2>
 * 
 * AtomMangers support queries that are in the form of conjunctions of Atoms. To be returned
 * as a query result, each ground Atom in the grounding must be <em>active</em>.
 * Active Atoms are a subset of those managed which have been designated as able to be queried.
 * Atoms can be activated via {@link #activateAtom(Atom)} or {@link #runActivationStrategy()}.
 * Upon initialization only the observed Atoms are active.
 * 
 * <h2>Atom Events</h2>
 * 
 * AtomManagers are also responsible for managing the events and status changes related
 * to Atoms.
 * 
 * <h2>Ground Kernels</h2>
 * 
 * An AtomManager contains a {@link GroundKernelStore}. GroundKernels over Atoms from
 * an AtomManager should be stored in it.
 * 
 * @see AtomEvent
 * @see AtomStatus
 */
public interface AtomManager extends ModelEvent.Listener {
	
	/**
	 * Returns the specified ground Atom.
	 * <p>
	 * This method should be used by {@link ModelApplication ModelApplications}
	 * and their delegates to access ground Atoms. This ensures that a ground Atom
	 * instantiation for a given Predicate and arguments is unique for a ground model
	 * in a ModelApplication.
	 * <p>
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
	 * <p>
	 * Where the Atom will be persisted is implementation specific. If this
	 * AtomManager was instantiated with a {@link Database}, then the Atom should
	 * be persisted there.
	 * 
	 * @param atom  the Atom to persist
	 */
	public void persist(Atom atom);
	
	/**
	 * Returns all active groundings of a Formula.
	 * <p>
	 * A grounding is active if each Atom is active.
	 * 
	 * @param f  {@link Conjunction} of Atoms to ground
	 * @return a list of lists of substitutions of {@link GroundTerm GroundTerms}
	 *             for {@link Variable Variables}
	 * @throws IllegalArgumentException  if the query Formula is invalid
	 */
	public ResultList getActiveGroundings(Formula f);
	
	/**
	 * Returns all active groundings of a Formula.
	 * <p>
	 * A grounding is active if each Atom is active.
	 * <p>
	 * The returned groundings are projected onto the specified {@link Variable Variables}.
	 * 
	 * @param f  {@link Conjunction} of Atoms to ground
	 * @param projectTo  the Variables onto which the groundings will be projected
	 * @return a list of lists of substitutions of {@link GroundTerm GroundTerms}
	 *             for {@link Variable Variables}
	 * @throws IllegalArgumentException  if the query Formula is invalid
	 */
	public ResultList getActiveGroundings(Formula f, List<Variable> projectTo);
	
	/**
	 * Returns all active groundings of a Formula.
	 * <p>
	 * A grounding is active if each Atom is active.
	 * <p>
	 * Additionally, the groundings must match the given partial grounding. The partial
	 * grounding will <em>not</em> be included in the returned substitutions.
	 * 
	 * @param f  {@link Conjunction} of Atoms to ground
	 * @param partialGrounding  a partial substitution of {@link Variable Variables} which
	 *                              each returned grounding must match
	 * @return a list of lists of substitutions of {@link GroundTerm GroundTerms}
	 *             for {@link Variable Variables}
	 * @throws IllegalArgumentException  if the query Formula is invalid
	 */
	public ResultList getActiveGroundings(Formula f, VariableAssignment partialGrounding);
	
	/**
	 * Returns all active groundings of a Formula.
	 * <p>
	 * A grounding is active if each Atom is active.
	 * <p>
	 * The returned groundings are projected onto the specified {@link Variable Variables}.
	 * Additionally, the groundings must match the given partial grounding. The partial
	 * grounding for a particular Variable will only be included in the returned
	 * substitutions <em>if</em> that Variable is included in the projection Variables.
	 * 
	 * @param f  {@link Conjunction} of Atoms to ground
	 * @param partialGrounding  a partial substitution of Variables which
	 *                              each returned grounding must match
	 * @param projectTo  the Variables onto which the groundings will be projected
	 * @return a list of lists of substitutions of {@link GroundTerm GroundTerms}
	 *             for {@link Variable Variables}
	 * @throws IllegalArgumentException  if the query Formula is invalid
	 */
	public ResultList getActiveGroundings(Formula f, VariableAssignment partialGrounding, List<Variable> projectTo);
	
	/**
	 * Returns all ground Atoms with any of the given statuses.
	 * <p>
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
	 * <p>
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
	 * <p>
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
	 * <p>
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
	 * <p>
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
	 * <p>
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
	public void registerAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener);
	
	/**
	 * Registers a listener for any events in a set related to {@link Atom Atoms}
	 * with a given Predicate.
	 * 
	 * @param events  set of events for which to listen
	 * @param p  Predicate of Atoms for which to listen for events
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener);
	
	/**
	 * Unregisters a listener for any events in a set.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, AtomEvent.Listener listener);
	
	/**
	 * Unregisters a listener for any events in a set related to {@link Atom Atoms}
	 * with a given Predicate.
	 * 
	 * @param events  set of events for which to stop listening
	 * @param p  Predicate of Atoms for which to stop listening
	 * @param listener  object to unregister
	 * @throws IllegalArgumentException  if listener is not registered to listen for
	 *             a subset of AtomEvents in events for Predicate p
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent> events, Predicate p, AtomEvent.Listener listener);
	
	/**
	 * Activates all (ground) {@link Atom Atoms} which meet this manager's
	 * activation criteria.
	 * 
	 * @return number of Atoms activated
	 * @see AtomManager#workOffJobQueue()
	 */
	public int runActivationStrategy();
	
	/**
	 * Deactivates all (ground) {@link Atom Atoms} which meet this manager's
	 * deactivation criteria.
	 * 
	 * @return number of Atoms deactivated
	 * @see AtomManager#workOffJobQueue()
	 */
	public int runDeactivationStrategy();
	
	/**
	 * Activates an Atom.
	 * 
	 * @param atom  Atom to activate
	 * @see AtomManager#workOffJobQueue()
	 */
	public void activateAtom(Atom atom);
	
	/**
	 * Deactivates an Atom.
	 * 
	 * @param atom  Atom to deactivate
	 * @see AtomManager#workOffJobQueue()
	 */
	public void deactivateAtom(Atom atom);
	
	/**
	 * Releases a (ground) Atom if it meets this manager's release criteria.
	 * <p>
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
	 * <p>
	 * If a {@link Predicate} is open, then any ground {@link Atom} of that
	 * Predicate which is not observed will be treated as a random variable.
	 * 
	 * @param predicate  Predicate to open
	 * @see AtomManager#workOffJobQueue()
	 */
	public void open(Predicate predicate);
	
	/**
	 * Closes a Predicate.
	 * <p>
	 * If a {@link Predicate} is closed, then any ground Atom of that Predicate
	 * which is not observed is fixed.
	 * 
	 * @param predicate  Predicate to close
	 * @see AtomManager#workOffJobQueue()
	 */
	public void close(Predicate predicate);
	
	/**
	 * Processes all pending {@link AtomJob AtomJobs}.
	 * <p>
	 * This method should be called after changing {@link Atom} values, activating
	 * Atoms, opening or closing {@link Predicate Predicates}, or any other
	 * operation which could change the {@link AtomStatus} of an Atom.
	 */
	public void workOffJobQueue();
	
	/**
	 * Gets the associated GroundKernelStore.
	 * 
	 * @return the store for all GroundKernels over this AtomManager's Atoms.
	 */
	public GroundKernelStore getGroundKernelStore();
	
}
