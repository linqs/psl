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

import java.util.Collection;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.atom.Atom;
import edu.umd.cs.psl.model.atom.AtomStatus;
import edu.umd.cs.psl.model.atom.StatusAtom;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

/**
 * The abstract FormulaPredicateAtom is the base class for all atoms with a predicate and arguments.
 * It defines most of the standard functionality of predicate atoms with some of it implemented in its
 * two child classes.
 * 
 * FormulaAtom must be constructed through the static create() functions to ensure a unique memory
 * representation.
 * 
 * @author Matthias Broecheler
 *
 */
public class MemoryAtom extends StatusAtom {
	
	private static final Set<GroundKernel> emptyGroundKernels = ImmutableSet.of();
	
	/**
	 * A set of evidence which depend on this atom. A dependent formula is one which truth value could
	 * change when the truth value of the atom changes.
	 */
	private SetMultimap<Kernel,GroundKernel> dependentKernels;
	
	MemoryAtom(Predicate p, GroundTerm[] args, AtomStatus status) {
		super(p,args,status);
		
		dependentKernels=null;
		
	}
	
	void checkAccess() {
		if (!isDefined()) throw new IllegalStateException("Cannot modify this atom since it is undefined");
	}
	

	
	@Override
	public boolean registerGroundKernel(GroundKernel f) {
		checkAccess();
		if (dependentKernels==null) dependentKernels = HashMultimap.create();
		return dependentKernels.put(f.getKernel(), f);
	}
	
	@Override
	public boolean deregisterGroundKernel(GroundKernel f) {
		checkAccess();
		if (dependentKernels==null) return false;
		return dependentKernels.remove(f.getKernel(), f);
	}
	
	@Override
	public Set<GroundKernel> getRegisteredGroundKernels(Kernel et) {
		if (dependentKernels==null) return emptyGroundKernels;
		return dependentKernels.get(et);
	}
	
	@Override
	public Collection<GroundKernel> getAllRegisteredGroundKernels() {
		if (dependentKernels==null) return emptyGroundKernels;
		return dependentKernels.values();
	}
	
	@Override
	public int getNumRegisteredGroundKernels() {
		if (dependentKernels==null) return 0; 
		return dependentKernels.size();
	}
	
	/*
	 * ###### FunctionVariable Interface ##########
	 */
	
	@Override
	public AtomFunctionVariable getVariable() {
		if (getPredicate().getNumberOfValues()!=1) throw new IllegalStateException();
		return getVariable(0);
	}
	
	@Override
	public AtomFunctionVariable getVariable(int position) {
		checkAccess();
		Preconditions.checkArgument(position>=0 && position<getPredicate().getNumberOfValues());
		return new AtomVariable(position);
	}
	
	private class AtomVariable extends AtomFunctionVariable {
		
		private final int position;
		
		private AtomVariable(int pos) {
			position = pos;
		}
		
		@Override
		public boolean isConstant() {
			return isKnownAtom();
		}

		@Override
		public void setValue(double val) {
			setSoftValue(position,val);
		}

		@Override
		public double getValue() {
			return getSoftValue(position);
		}
		

		@Override
		public double getConfidence() {
			return getConfidenceValue(position);
		}

		@Override
		public void setConfidence(double val) {
			setConfidenceValue(position,val);
		}
		
		@Override
		public int hashCode() {
			return MemoryAtom.this.hashCode() + position + 97;
		}
		
		@Override
		public Atom getAtom() {
			return MemoryAtom.this;
		}
		
		@Override
		public boolean equals(Object oth) {
			if (oth==this) return true;
			if (oth==null || !(getClass().isInstance(oth)) ) return false;
			AtomVariable other = (AtomVariable)oth;
			return position==other.position && getAtom().equals(other.getAtom());
		}
		
		@Override
		public String toString() {
			if (MemoryAtom.this.getNumberOfValues()==1) return MemoryAtom.this.toString();
			else return MemoryAtom.this.toString()+"#"+position;
		}

		
	}
	
	


	
}
