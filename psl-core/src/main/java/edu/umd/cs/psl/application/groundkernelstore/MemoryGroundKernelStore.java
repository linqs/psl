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
package edu.umd.cs.psl.application.groundkernelstore;

import com.google.common.collect.Iterables;

import de.mathnbits.util.KeyedRetrievalSet;
import edu.umd.cs.psl.model.rule.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.rule.GroundConstraintKernel;
import edu.umd.cs.psl.model.rule.GroundRule;
import edu.umd.cs.psl.model.rule.Rule;
import edu.umd.cs.psl.util.collection.Filters;

/**
 * A simple {@link GroundKernelStore} that just stores each {@link GroundRule}
 * in memory.
 * <p>
 * No action is taken by {@link #changedGroundKernel(GroundRule)}.
 */
public class MemoryGroundKernelStore implements GroundKernelStore {

	protected final KeyedRetrievalSet<Rule,GroundRule> groundKernels;
	
	public MemoryGroundKernelStore() {
		groundKernels = new KeyedRetrievalSet<Rule,GroundRule>();
	}
	
	@Override
	public boolean containsGroundKernel(GroundRule gk) {
		return groundKernels.contains(gk.getKernel(),gk);
	}
	
	@Override
	public GroundRule getGroundKernel(GroundRule gk) {
		return groundKernels.get(gk.getKernel(),gk);
	}
	
	@Override
	public void addGroundKernel(GroundRule gk) {
		if (!groundKernels.put(gk.getKernel(), gk))
			throw new IllegalArgumentException("GroundKernel has already been added: " + gk);
	}
	
	@Override
	public void changedGroundKernel(GroundRule gk) {
		/* Intentionally blank */
	}

	@Override
	public void changedGroundKernelWeight(GroundCompatibilityKernel k) {
		/* Intentionally blank */
	}

	@Override
	public void changedGroundKernelWeights() {
		/* Intentionally blank */
	}
	
	@Override
	public void removeGroundKernel(GroundRule gk) {
		groundKernels.remove(gk.getKernel(), gk);
	}
	
	public Iterable<GroundRule> getGroundKernels() {
		return groundKernels;
	}
	
	@Override
	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels() {
		return Iterables.filter(groundKernels.filterIterable(Filters.CompatibilityKernel), GroundCompatibilityKernel.class);
	}
	
	public Iterable<GroundConstraintKernel> getConstraintKernels() {
		return Iterables.filter(groundKernels.filterIterable(Filters.ConstraintKernel), GroundConstraintKernel.class);
	}
	
	@Override
	public Iterable<GroundRule> getGroundKernels(Rule k) {
		return groundKernels.keyIterable(k);
	}
	
	@Override
	public int size() {
		return groundKernels.size();
	}
	
}
