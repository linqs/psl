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
package org.linqs.psl.model.rule;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.atom.AtomEvent;
import org.linqs.psl.model.atom.AtomEventFramework;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

/**
 * Allows implementing Rules to avoid keeping track of which GroundRuleStore
 * to use when handling AtomEvents.
 *
 * @author Eric Norris <enorris@cs.umd.edu>
 */
public abstract class AbstractRule implements Rule {

	private static final Logger log = LoggerFactory.getLogger(AbstractRule.class);

	protected SetMultimap<AtomEventFramework, GroundRuleStore> frameworks;

	protected AbstractRule() {
		this.frameworks = HashMultimap.create();
	}

	@Override
	public void notifyAtomEvent(AtomEvent event) {
		for (GroundRuleStore gks : frameworks.get(event.getEventFramework()))
			notifyAtomEvent(event, gks);
	}

	@Override
	public void registerForAtomEvents(AtomEventFramework eventFramework,
			GroundRuleStore grs) {
		if (!frameworks.containsKey(eventFramework)) {
			frameworks.put(eventFramework, grs);
			registerForAtomEvents(eventFramework);
		} else if (!frameworks.put(eventFramework, grs)) {
			log.debug("Attempted to register for AtomEventFramework that has" +
					" already been registered.");
		}
	}

	@Override
	public void unregisterForAtomEvents(AtomEventFramework eventFramework,
			GroundRuleStore grs) {
		if (!frameworks.remove(eventFramework, grs))
			log.debug("Attempted to unregister with AtomEventFramework that is" +
					" not registered.");
		else if (!frameworks.containsKey(eventFramework))
			unregisterForAtomEvents(eventFramework);
	}


	@Override
	public Rule clone() throws CloneNotSupportedException {
		throw new CloneNotSupportedException();
	}

	/**
	 * Handles an AtomEvent using the specified GroundRuleStore.
	 * <p>
	 * Rules need to have registered to handle this event with an
	 * AtomEventFramework.
	 * @param event		the AtomEvent that occurred
	 * @param grs		the GroundRuleStore to use
	 */
	protected abstract void notifyAtomEvent(AtomEvent event, GroundRuleStore grs);

	/**
	 * Registers with a specific AtomEventFramework to handle atom events.
	 * <p>
	 * Subclasses are expected to register for the same AtomEvents and Predicates
	 * at all times. Rules that do not fit this behavior should not extend
	 * this class.
	 * @param eventFramework	The event framework to register with
	 */
	protected abstract void registerForAtomEvents(AtomEventFramework eventFramework);

	/**
	 * Unregisters from a specific AtomEventFrameWork to no longer handle atom events.
	 * <p>
	 * Subclasses are expected to have registered for the same AtomEvents and Predicates
	 * at all times. Rules that do not fit this behavior should not extend this class.
	 * @param eventFramework	The event framework to unregister from
	 */
	protected abstract void unregisterForAtomEvents(AtomEventFramework eventFramework);
}
