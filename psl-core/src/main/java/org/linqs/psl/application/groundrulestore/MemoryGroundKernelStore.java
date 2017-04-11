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
 * <p>
 * No action is taken by {@link #changedGroundRule(GroundRule)}.
 */
public class MemoryGroundKernelStore implements GroundRuleStore {

	protected final SetValuedMap<Rule, GroundRule> groundKernels;
	
	public MemoryGroundKernelStore() {
		groundKernels = new HashSetValuedHashMap<Rule, GroundRule>();
	}
	
	@Override
	public boolean containsGroundKernel(GroundRule gk) {
		return groundKernels.containsMapping(gk.getRule(),gk);
	}
	
	@Override
	public void addGroundRule(GroundRule gk) {
		if (!groundKernels.put(gk.getRule(), gk))
			throw new IllegalArgumentException("GroundKernel has already been added: " + gk);
	}
	
	@Override
	public void changedGroundRule(GroundRule gk) {
		/* Intentionally blank */
	}

	@Override
	public void changedGroundKernelWeight(WeightedGroundRule k) {
		/* Intentionally blank */
	}

	@Override
	public void changedGroundKernelWeights() {
		/* Intentionally blank */
	}
	
	@Override
	public void removeGroundKernel(GroundRule gk) {
		groundKernels.removeMapping(gk.getRule(), gk);
	}
	
	public Iterable<GroundRule> getGroundKernels() {
		return groundKernels.values();
	}
	
	@Override
	public Iterable<WeightedGroundRule> getCompatibilityKernels() {
		return Iterables.filter(groundKernels.values(), WeightedGroundRule.class);
	}
	
	public Iterable<UnweightedGroundRule> getConstraintKernels() {
		return Iterables.filter(groundKernels.values(), UnweightedGroundRule.class);
	}
	
	@Override
	public Iterable<GroundRule> getGroundKernels(Rule k) {
		return groundKernels.get(k);
	}
	
	@Override
	public int size() {
		return groundKernels.size();
	}
	
}
