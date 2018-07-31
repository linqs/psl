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
package org.linqs.psl.application.groundrulestore;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

import org.apache.commons.collections4.SetValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import com.google.common.collect.Iterables;

/**
 * A simple {@link GroundRuleStore} that just stores each {@link GroundRule}
 * in memory.
 * addGroundRule() is thread-safe and will silently ignore already added rules.
 * Other methods are not guaranteed safe.
 */
public class MemoryGroundRuleStore implements GroundRuleStore {
	protected SetValuedMap<Rule, GroundRule> groundRules;

	public MemoryGroundRuleStore() {
		groundRules = new HashSetValuedHashMap<Rule, GroundRule>();
	}

	@Override
	public synchronized void addGroundRule(GroundRule groundRule) {
		groundRules.put(groundRule.getRule(), groundRule);
	}

	@Override
	public boolean containsGroundRule(GroundRule groundRule) {
		return groundRules.containsMapping(groundRule.getRule(), groundRule);
	}

	@Override
	public Iterable<WeightedGroundRule> getCompatibilityRules() {
		return Iterables.filter(groundRules.values(), WeightedGroundRule.class);
	}

	@Override
	public Iterable<UnweightedGroundRule> getConstraintRules() {
		return Iterables.filter(groundRules.values(), UnweightedGroundRule.class);
	}

	@Override
	public Iterable<GroundRule> getGroundRules() {
		return groundRules.values();
	}

	@Override
	public Iterable<GroundRule> getGroundRules(Rule rule) {
		return groundRules.get(rule);
	}

	@Override
	public void removeGroundRule(GroundRule groundRule) {
		groundRules.removeMapping(groundRule.getRule(), groundRule);
	}

	@Override
	public void removeGroundRules(Rule rule) {
		groundRules.remove(rule);
	}

	@Override
	public int size() {
		return groundRules.size();
	}

	@Override
	public int count(Rule rule) {
		return groundRules.get(rule).size();
	}

	@Override
	public void close() {
		if (groundRules != null) {
			groundRules.clear();
			groundRules = null;
		}
	}
}
