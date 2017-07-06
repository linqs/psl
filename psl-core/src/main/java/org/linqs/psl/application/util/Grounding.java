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
package org.linqs.psl.application.util;

import org.linqs.psl.application.groundrulestore.GroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.AtomManager;
import org.linqs.psl.model.rule.Rule;

/**
 * Static utilities for common {@link Model}-grounding tasks.
 */
public class Grounding {

	private final static com.google.common.base.Predicate<Rule> all = new com.google.common.base.Predicate<Rule>(){
		@Override
		public boolean apply(Rule el) {	return true; }
	};

	/**
	 * Calls {@link Rule#groundAll(AtomManager, GroundRuleStore)} on
	 * each Rule in a Model.
	 *
	 * @param m  the Model with the Rules to ground
	 * @param atomManager  AtomManager to use for grounding
	 * @param grs  GroundRuleStore to use for grounding
	 */
	public static void groundAll(Model m, AtomManager atomManager, GroundRuleStore grs) {
		groundAll(m, atomManager, grs, all);
	}

	/**
	 * Calls {@link Rule#groundAll(AtomManager, GroundRuleStore)} on
	 * each Rule in a Model which passes a filter.
	 *
	 * @param m  the Model with the Rules to ground
	 * @param atomManager  AtomManager to use for grounding
	 * @param grs  GroundRuleStore to use for grounding
	 * @param filter  filter for Rules to ground
	 */
	public static void groundAll(Model m, AtomManager atomManager, GroundRuleStore grs,
			com.google.common.base.Predicate<Rule> filter) {
		for (Rule rule : m.getRules()) {
			if (filter.apply(rule)) {
				rule.groundAll(atomManager, grs);
			}
		}
	}

}
