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
package org.linqs.psl.application.util;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.rule.Rule;

import java.util.List;

/**
 * Static utilities for common {@link Model}-grounding tasks.
 */
public class Grounding {
	/**
	 * Calls {@link Rule#groundAll(AtomManager, GroundRuleStore)} on
	 * each Rule in a Model.
	 *
	 * @param model the Model with the Rules to ground
	 * @param atomManager AtomManager to use for grounding
	 * @param groundRuleStore GroundRuleStore to use for grounding
	 */
	public static int groundAll(Model model, AtomManager atomManager, GroundRuleStore groundRuleStore) {
		return groundAll(model.getRules(), atomManager, groundRuleStore);
	}

	public static int groundAll(List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
		int groundCount = 0;
		for (Rule rule : rules) {
			groundCount += rule.groundAll(atomManager, groundRuleStore);
		}

		return groundCount;
	}
}
