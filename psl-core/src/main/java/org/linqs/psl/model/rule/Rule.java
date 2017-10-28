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
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.NumericUtilities;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;

/**
 * A template for functions that either constrain or measure the compatibility
 * of the values of {@link GroundAtom GroundAtoms}.
 * <p>
 * A Rule is responsible for instantiating {@link GroundRule GroundRules}.
 * A Rule must instantiate only {@link WeightedGroundRule}s or only
 * {@link UnweightedGroundRule}s.
 * 
 * @author Matthias Broecheler <mail@knowledgefrominformation.com>
 * @author Eric Norris <enorris@cs.umd.edu>
 * @author Stephen Bach <bach@cs.umd.edu>
 */
public interface Rule extends Cloneable {

	/**
	 * Adds all missing, potentially unsatisfied {@link GroundRule GroundRules}
	 * to a {@link GroundRuleStore} based on an {@link AtomManager}.
	 * <p>
	 * Specifically, will add any GroundRule templated by this Rule
	 * that satisfies all the following conditions:
	 * <ul>
	 *   <li>The GroundRule has incompatibility or infeasibility
	 *   greater than {@link NumericUtilities#strictEpsilon}
	 *   for some assignment of truth values to the {@link RandomVariableAtom}s
	 *   <em>currently persisted</em> in the AtomManager's Database given the truth
	 *   values of the {@link ObservedAtom}s and assuming that any RandomVariableAtom
	 *   not persisted has a truth value of 0.0.</li>
	 *   <li>The GroundRule is not already in the GroundRuleStore.</li>
	 *   <li>If the GroundRule is a {@link WeightedGroundRule}, its
	 *       incompatibility is not constant with respect to the truth values
	 *       of RandomVariableAtoms (including those not persisted in the
	 *       AtomManager's Database).
	 *   </li>
	 * </ul>
	 * <p>
	 * Only GroundRules which satisfy these conditions should be added.
	 * 
	 * @param atomManager  AtomManager on which to base the grounding
	 * @param grs          store for new GroundRules
	 * @see WeightedGroundRule#getIncompatibility()
	 * @see UnweightedGroundRule#getInfeasibility()
	 */
	public void groundAll(AtomManager atomManager, GroundRuleStore grs);
	
	public Rule clone() throws CloneNotSupportedException;
}
