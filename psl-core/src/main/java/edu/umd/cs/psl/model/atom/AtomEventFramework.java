/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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

import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.SetMultimap;
import com.google.common.collect.HashMultimap;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.DatabaseQuery;
import edu.umd.cs.psl.database.ResultList;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

/**
 * AtomManager with support for {@link AtomEvent AtomEvents}. 
 * <p>
 * An AtomEventFramework handles two types of events: <em>consideration</em> and
 * <em>activation</em>.
 * <p>
 * A RandomVariableAtom is considered when it is instantiated in memory
 * and added to its {@link Database}'s {@link AtomCache} via
 * {@link #getAtom(Predicate, GroundTerm...)}. Warning: if a RandomVariableAtom
 * is instantiated in memory other than via a call to {@link #getAtom(Predicate, GroundTerm...)},
 * then no consideration event will be generated during the life of the Database.
 * <p>
 * A RandomVariableAtom is activated if it has not already been activated
 * since being instantiated in memory and one of two things occur:
 * <ol>
 *   <li>{@link #checkToActivate()} is called and its truth value is at or
 *   above a threshold</li>
 *   <li>{@link #activateAtom(RandomVariableAtom)} is called on that Atom</li>
 * </ol>
 * <p>
 * {@link AtomEvent.Listener} implementations can register to be notified of these events.
 * <p>
 * For each event, an {@link AtomEvent} is added to the job queue. Calling
 * {@link #workOffJobQueue()} will cause the appropriate Listeners to be
 * notified of events in the queue until the queue is empty. Before any Listeners
 * are notified, all activated RandomVariableAtoms in the queue will be committed
 * to the Database.
 */
public class AtomEventFramework implements AtomManager {
	
	/**
	 * Prefix of property keys used by this class.
	 * 
	 * @see ConfigManager
	 */
	public static final String CONFIG_PREFIX = "atomeventframework";
	
	private static final Logger log = LoggerFactory.getLogger(AtomEventFramework.class);
	
	/**;
	 * Key for double property in (0,1]. Activation events will be generated
	 * for RandomVariableAtoms when they meet or exceed this threshold.
	 */
	public static final String ACTIVATION_THRESHOLD_KEY = CONFIG_PREFIX + ".activation";
	/** Default value for ACTIVATION_THRESHOLD_KEY property */
	public static final double ACTIVATION_THRESHOLD_DEFAULT = 0.01;
	
	public static final StandardPredicate AllPredicates = null;
	
	private final Database db;
	private final double activationThreshold;
	private Queue<AtomEvent> jobQueue;
	private EnumMap<AtomEvent.Type,SetMultimap<StandardPredicate,AtomEvent.Listener>> atomListeners;
	private Set<Atom> activeAtoms;
	
	public AtomEventFramework(Database db, ConfigBundle config) {
		this.db = db;
		activationThreshold = config.getDouble(ACTIVATION_THRESHOLD_KEY, ACTIVATION_THRESHOLD_DEFAULT);
		if (activationThreshold <= 0 || activationThreshold > 1)
			throw new IllegalArgumentException("Activation threshold must be in (0,1].");
		jobQueue = new LinkedList<AtomEvent>();
		atomListeners = new EnumMap<AtomEvent.Type,SetMultimap<StandardPredicate,AtomEvent.Listener>>(AtomEvent.Type.class);
		for (AtomEvent.Type type : AtomEvent.Type.values()) {
			SetMultimap<StandardPredicate,AtomEvent.Listener> map = HashMultimap.create();
			atomListeners.put(type, map);
		}
		activeAtoms = new HashSet<Atom>();
	}
	
	/**
	 * Calls {@link Database#getAtom(Predicate, GroundTerm...)} and adds
	 * a {@link AtomEvent#ConsideredRVAtom} event to the job queue if the
	 * GroundAtom is a RandomVariableAtom and not already in the Database's AtomCache. 
	 * 
	 * @see #workOffJobQueue()
	 */
	@Override
	public GroundAtom getAtom(Predicate p, GroundTerm... arguments) {
		Atom check = db.getAtomCache().getCachedAtom(new QueryAtom(p, arguments));
		GroundAtom atom = db.getAtom(p,  arguments);
		if (atom instanceof RandomVariableAtom && check == null) {
			AtomEvent event = new AtomEvent(AtomEvent.Type.ConsideredRVAtom,
					(RandomVariableAtom) atom, this);
			addAtomJob(event);
		}
		return atom;
	}
	
	/**
	 * Registers a listener for any event types in a set.
	 * 
	 * @param eventTypes  set of event types for which to listen
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent.Type> eventTypes, AtomEvent.Listener listener) {
		this.registerAtomEventListener(eventTypes, AllPredicates, listener);
	}
	
	/**
	 * Registers a listener for any event types in a set related to 
	 * {@link RandomVariableAtom RandomVariableAtoms} with a given StandardPredicate.
	 * 
	 * @param eventTypes  set of event types for which to listen
	 * @param p  Predicate of Atoms for which to listen for events
	 * @param listener  object to register
	 * @see AtomEvent
	 */
	public void registerAtomEventListener(Set<AtomEvent.Type> eventTypes, StandardPredicate p, AtomEvent.Listener listener) {
		for (AtomEvent.Type type : eventTypes)
			atomListeners.get(type).put(p, listener);
	}
	
	/**
	 * Unregisters a listener for any event types in a set.
	 * 
	 * @param eventTypes  set of event types for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent.Type> eventTypes, AtomEvent.Listener listener) {
		this.unregisterAtomEventListener(eventTypes, AllPredicates, listener);
	}
	
	/**
	 * Unregisters a listener for any event types in a set related to
	 * {@link RandomVariableAtom RandomVariableAtoms} with a given StandardPredicate.
	 * 
	 * @param eventTypes  set of event types for which to stop listening
	 * @param p  Predicate of Atoms for which to stop listening
	 * @param listener  object to unregister
	 * @see AtomEvent
	 */
	public void unregisterAtomEventListener(Set<AtomEvent.Type> eventTypes, StandardPredicate p, AtomEvent.Listener listener) {
		for (AtomEvent.Type type : eventTypes) 
			if (atomListeners.containsKey(type))
				atomListeners.get(type).remove(p, listener);		
			else
				log.debug("Attempted to unregister listener that was not registered: ", listener);
	}
	
	/**
	 * Adds a {@link AtomEvent#ActivatedRVAtom} event to the job queue for each
	 * RandomVariableAtom in the Database's AtomCache which is at or above
	 * the activation threshold and has not already been activated by this
	 * AtomEventFramework since being loaded into memory.
	 * 
	 * @return number of Atoms activated
	 * @see #workOffJobQueue()
	 */
	public int checkToActivate() {
		int activated = 0;
		for (RandomVariableAtom atom : db.getAtomCache().getCachedRandomVariableAtoms()) {
			if (!activeAtoms.contains(atom) && atom.getValue() >= activationThreshold) {
				activated++;
				doActivateAtom(atom);
			}
		}
		return activated;
	}
	
	/**
	 * Adds a {@link AtomEvent#ActivatedRVAtom} event to the job queue for a
	 * RandomVariableAtom, regardless of its truth value.
	 * <p>
	 * No event will be generated if the Atom has already been activated by
	 * this AtomEventFramework since being loaded into memory.
	 * 
	 * @param atom  Atom to activate
	 * @see #workOffJobQueue()
	 * @throws IllegalArgumentException  if atom does not belong to this
	 *                                       framework's Database
	 */
	public void activateAtom(RandomVariableAtom atom) {
		if (!db.equals(atom.db))
			throw new IllegalArgumentException("Atom did not come from Database" +
					" managed by this AtomEventFramework.");
		if (!activeAtoms.contains(atom)) {
			doActivateAtom(atom);
		}
	}
	
	private void doActivateAtom(RandomVariableAtom atom) {
		AtomEvent event = new AtomEvent(AtomEvent.Type.ActivatedRVAtom,
				(RandomVariableAtom) atom, this);
		addAtomJob(event);
		activeAtoms.add(atom);
	}

	/**
	 * Processes all pending {@link AtomJob AtomJobs}.
	 * <p>
	 * This method should be called after considering or activating Atoms
	 * so that listeners will be notified.
	 */
	public void workOffJobQueue() {
		// TODO: Lock queue so no activation events can be added while working off, so no commits are missed (or move commit to doActivateAtom()?)
		// First make all necessary commits
		for (AtomEvent event : jobQueue)
			if (event.getType().equals(AtomEvent.Type.ActivatedRVAtom))
				event.getAtom().commitToDB();
		
		while (!jobQueue.isEmpty()) {
			AtomEvent event = jobQueue.poll();
			notifyListeners(event);
		}
	}

	@Override
	public ResultList executeQuery(DatabaseQuery query) {
		return db.executeQuery(query);
	}
	
	@Override
	public boolean isClosed(StandardPredicate predicate) {
		return db.isClosed(predicate);
	}
	
	private void addAtomJob(AtomEvent event) {
		jobQueue.add(event);
	}
	
	private void notifyListeners(AtomEvent event) {
		Atom atom = event.getAtom();
	
		/* notify all listeners registered by predicate */
		for (AtomEvent.Listener listener: atomListeners.get(event.getType()).get((StandardPredicate) atom.getPredicate())) 
			listener.notifyAtomEvent(event);
				
		/* notify all listeners registered for all predicates */		
		for (AtomEvent.Listener listener: atomListeners.get(event.getType()).get(AllPredicates)) 
			listener.notifyAtomEvent(event);
	}
}
