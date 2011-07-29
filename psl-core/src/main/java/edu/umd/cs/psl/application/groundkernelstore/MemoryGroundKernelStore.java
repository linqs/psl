/*
 * This file is part of the PSL software.
 * Copyright 2011 University of Maryland
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
import edu.umd.cs.psl.application.GroundKernelStore;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.kernel.GroundCompatibilityKernel;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.util.collection.Filters;

public class MemoryGroundKernelStore implements GroundKernelStore {

	protected final KeyedRetrievalSet<Kernel,GroundKernel> evidences;
	
	public MemoryGroundKernelStore() {
		evidences = new KeyedRetrievalSet<Kernel,GroundKernel>();
	}
	
	@Override
	public double getTotalIncompatibility() {
		double objective = 0.0;
		for (GroundKernel e : evidences) {
			objective+=e.getIncompatibility();
		}
		return objective;
	}
	
	@Override
	public Iterable<GroundCompatibilityKernel> getCompatibilityKernels() {
		return Iterables.filter(evidences.filterIterable(Filters.ProbabilisticEvidence), GroundCompatibilityKernel.class);
	}
	
	@Override
	public int size() {
		return evidences.size();
	}
	
	@Override
	public Iterable<GroundKernel> getGroundKernels() {
		return evidences;
	}
	
	@Override
	public Iterable<GroundKernel> getGroundKernels(Kernel et) {
		return evidences.keyIterable(et);
	}	
	
	@Override
	public void addGroundKernel(GroundKernel e) {
		if (!evidences.put(e.getKernel(),e)) throw new IllegalArgumentException("Evidence has already been added: "+e);
		for (Atom atom : e.getAtoms()) if (!atom.registerGroundKernel(e)) throw new AssertionError("Evidence has already been registered with atom! " + e);
	}
	
	@Override
	public void changedGroundKernel(GroundKernel e) {
		for (Atom atom : e.getAtoms()) atom.registerGroundKernel(e);
	}
	
	@Override
	public void removeGroundKernel(GroundKernel e) {
		//Deregister with atoms and remove from reasoner
		for (Atom atom : e.getAtoms()) if (!atom.deregisterGroundKernel(e)) throw new AssertionError("Evidence has never been registered with atom!");
		evidences.remove(e.getKernel(), e);
	}
	
	@Override
	public boolean containsGroundKernel(GroundKernel e) {
		return evidences.contains(e.getKernel(),e);
	}
	
	@Override
	public GroundKernel getGroundKernel(GroundKernel e) {
		return evidences.get(e.getKernel(),e);
	}
	
}
