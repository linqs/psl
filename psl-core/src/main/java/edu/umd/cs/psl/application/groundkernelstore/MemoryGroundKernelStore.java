/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundConstraintKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.util.collection.Filters;

/**
 * A simple {@link GroundKernelStore} that just stores each {@link GroundKernel}
 * in memory.
 * <p>
 * No action is taken by {@link #changedGroundKernel(GroundKernel)}.
 */
public class MemoryGroundKernelStore implements GroundKernelStore {

	protected final KeyedRetrievalSet<Kernel,GroundKernel> groundKernels;
	
	public MemoryGroundKernelStore() {
		groundKernels = new KeyedRetrievalSet<Kernel,GroundKernel>();
	}
	
	@Override
	public boolean containsGroundKernel(GroundKernel gk) {
		return groundKernels.contains(gk.getKernel(),gk);
	}
	
	@Override
	public GroundKernel getGroundKernel(GroundKernel gk) {
		return groundKernels.get(gk.getKernel(),gk);
	}
	
	@Override
	public void addGroundKernel(GroundKernel gk) {
		if (!groundKernels.put(gk.getKernel(), gk))
			throw new IllegalArgumentException("GroundKernel has already been added: " + gk);
	}
	
	@Override
	public void changedGroundKernel(GroundKernel gk) {
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
	public void removeGroundKernel(GroundKernel gk) {
		groundKernels.remove(gk.getKernel(), gk);
	}
	
	public Iterable<GroundKernel> getGroundKernels() {
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
	public Iterable<GroundKernel> getGroundKernels(Kernel k) {
		return groundKernels.keyIterable(k);
	}
	
	@Override
	public int size() {
		return groundKernels.size();
	}
	
}
