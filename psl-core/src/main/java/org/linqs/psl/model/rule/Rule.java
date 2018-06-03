/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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

/**
 * A template for functions that either constrain or measure the compatibility
 * of the values of GroundAtom.
 * A Rule is responsible for instantiating GroundRules.
 * A Rule must instantiate only WeightedGroundRules or only UnweightedGroundRules.
 */
public interface Rule {
	/**
	 * Adds all GroundRules to a GroundRuleStore using the AtomManager
	 * to instantiate ground atoms.
	 *
	 * @param atomManager AtomManager on which to base the grounding
	 * @param groundRuleStore store for new GroundRules
	 * @return the number of ground rules generated.
	 */
	public int groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore);

	public boolean isWeighted();

	public String getName();
}
