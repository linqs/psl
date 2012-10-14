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
package edu.umd.cs.psl.model.atom;

import java.util.Collection;
import java.util.Set;

import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.memory.MemoryAtom;
import edu.umd.cs.psl.model.atom.memory.MemoryAtom.AtomVariable;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

/**
 * An Atom with only {@link GroundTerm GroundTerms} for arguments.
 * <p>
 * A GroundAtom has a truth value and a confidence value.
 */
abstract public class GroundAtom extends Atom {

	protected GroundAtom(Predicate p, GroundTerm[] args) {
		super(p, args);
	}

	/**
	 * Returns the truth value of this Atom.
	 * 
	 * @return The truth value in [0,1]
	 */
	abstract public double getValue();
	
	/**
	 * Returns the confidence value of this Atom.
	 * 
	 * @return The confidence value in [0, +Infinity)
	 */
	abstract public double getConfidenceValue();
	
	public AtomFunctionVariable getVariable() {
		return new AtomVariable();
	}
	
	/**
	 * Registers a ground kernel to receive update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was already registered 
	 */
	public boolean registerGroundKernel(GroundKernel f) {
		// TODO
		return false;
	}
	
	/**
	 * Unregisters a ground kernel, so that it no longer receives update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was never registered
	 */
	public boolean unregisterGroundKernel(GroundKernel f) {
		// TODO
		return false;
	}
	
	/**
	 * Returns a set of all registered ground kernels that match a given kernel.
	 * 
	 * @param f A kernel
	 * @return A set of all registered ground kernels that match f
	 */
	public Set<GroundKernel> getRegisteredGroundKernels(Kernel f) {
		// TODO
		return null;
	}
	
	/**
	 * Returns a set of all registered ground kernels.
	 * 
	 * @return A collection of all registered ground kernels
	 */
	public Collection<GroundKernel> getRegisteredGroundKernels() {
		// TODO
		return null;
	}
	
	/**
	 * Returns the number of registered ground kernels.
	 * 
	 * @return The number of registered ground kernels
	 */
	public int getNumRegisteredGroundKernels() {
		// TODO
		return 0;
	}
	
private class AtomVariable extends AtomFunctionVariable {
		
		@Override
		public boolean isConstant() {
			return status.isFixed();
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
