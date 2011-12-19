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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

import edu.umd.cs.psl.model.ConfidenceValues;
import edu.umd.cs.psl.model.TruthValues;
import edu.umd.cs.psl.model.argument.GroundTerm;
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

	private double softValue;
	private double confidenceValue;
	
	/**
	 * A set of evidence which depend on this atom. A dependent formula is one which truth value could
	 * change when the truth value of the atom changes.
	 */
	private SetMultimap<Kernel,GroundKernel> dependentKernels;
	
	public MemoryAtom(Predicate p, GroundTerm[] args, AtomStatus status) {
		this(p, args, status, TruthValues.getDefault(), ConfidenceValues.getDefault());
	}
	
	MemoryAtom(Predicate p, GroundTerm[] args, AtomStatus status, double softval, double confidence) {
		super(p,args,status);
		dependentKernels=null;
		setValue(softval);
		setConfidenceValue(confidence);
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
	public boolean unregisterGroundKernel(GroundKernel f) {
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
	public Collection<GroundKernel> getRegisteredGroundKernels() {
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
		checkAccess();
		return new AtomVariable();
	}
	
	@Override
	public void setValue(double value) {
		checkAccess();
		if (!TruthValues.isValid(value))
			throw new IllegalArgumentException("Illegal truth value: "+ value);
		softValue = value;
	}

	@Override
	public void setConfidenceValue(double val) {
		checkAccess();
		if (!ConfidenceValues.isValid(val))
			throw new IllegalArgumentException("Illegal confidence value: "+ val);
		confidenceValue = val;
	}

	@Override
	public double getValue() {
		return softValue;
	}

	@Override
	public double getConfidenceValue() {
		return confidenceValue;
	}

	private class AtomVariable extends AtomFunctionVariable {
		
		@Override
		public boolean isConstant() {
			return isKnowledge();
		}

		@Override
		public void setValue(double value) {
			setValue(value);
		}

		@Override
		public double getValue() {
			return getValue();
		}

		@Override
		public double getConfidence() {
			return getConfidenceValue();
		}

		@Override
		public void setConfidence(double val) {
			setConfidenceValue(val);
		}
		
		@Override
		public int hashCode() {
			return MemoryAtom.this.hashCode() + 97;
		}
		
		@Override
		public Atom getAtom() {
			return MemoryAtom.this;
		}
		
		@Override
		public boolean equals(Object oth) {
			if (oth==this)
				return true;
			if (oth==null || !(getClass().isInstance(oth)) )
				return false;
			AtomVariable other = (AtomVariable) oth;
			return getAtom().equals(other.getAtom());
		}
		
		@Override
		public String toString() {
			return MemoryAtom.this.toString();
		}

	}

}
