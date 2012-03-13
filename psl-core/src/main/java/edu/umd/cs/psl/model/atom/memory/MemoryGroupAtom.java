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
package edu.umd.cs.psl.model.atom.memory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.EntitySet;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.AbstractAtom;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomManager;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

public class MemoryGroupAtom extends AbstractAtom {

	MemoryGroupAtom(Predicate p, GroundTerm[] args) {
		super(p, args, AtomStatus.UnconsideredRV);
	}

	public Collection<Atom> getAtomsInGroup(AtomManager atommanager) {
		List<Atom> result = new ArrayList<Atom>();
		expandAtom(0,new GroundTerm[getArity()],atommanager,result);
		return result;
	}
	
	private void expandAtom(int argpos,GroundTerm[] args, AtomManager atommanager, Collection<Atom> result) {
		if (argpos>=args.length) {
			//Create atom
			GroundTerm[] copy = args.clone();
			Atom atom = atommanager.getAtom(getPredicate(), copy);
			result.add(atom);
		} else {
			GroundTerm t = (GroundTerm)getArguments()[argpos];
			if (t instanceof EntitySet) {
				for (Entity e : ((EntitySet)t).getEntities(atommanager)) {
					args[argpos]=e;
					expandAtom(argpos+1,args,atommanager,result);
				}
			} else {
				args[argpos]=t;
				expandAtom(argpos+1,args,atommanager,result);
			}
		}
	}

	@Override
	public boolean isGround() {
		return true;
	}

	@Override
	public boolean isAtomGroup() {
		return true;
	}
	
	//########## Unsupported Operations ##############

	@Override
	public boolean unregisterGroundKernel(GroundKernel f) {
		throw new UnsupportedOperationException();	}

	@Override
	public Collection<GroundKernel> getRegisteredGroundKernels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getConfidenceValue() {
		throw new UnsupportedOperationException();
	}

	@Override
	public Set<GroundKernel> getRegisteredGroundKernels(Kernel et) {
		throw new UnsupportedOperationException();
	}
	
	@Override
	public int getNumRegisteredGroundKernels() {
		throw new UnsupportedOperationException();
	}

	@Override
	public double getValue() {
		throw new UnsupportedOperationException();
	}

	@Override
	public AtomFunctionVariable getVariable() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean registerGroundKernel(GroundKernel f) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setConfidenceValue(double value) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void setValue(double value) {
		throw new UnsupportedOperationException();
	}
	
}
