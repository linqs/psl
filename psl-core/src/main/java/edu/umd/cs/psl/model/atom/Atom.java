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
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;

/**
 * A {@link Predicate} combined with the correct number of {@link Term Terms}
 * as arguments.
 * 
 * If all of the arguments are {@link GroundTerm GroundTerms}, then the Atom
 * is said to be ground and can be assigned a truth value. Ground Atoms
 * are statements of relationships. 
 * 
 * @author Matthias Broecheler
 */
public interface Atom extends Formula {
	
	/**
	 * Returns the predicate associated with this Atom.
	 * 
	 * @return A predicate
	 */
	public Predicate getPredicate();
	
	/**
	 * Returns the number of arguments to the associated predicate.
	 * 
	 * @return The number of arguments
	 */
	public int getArity();
	
	/**
	 * Returns the arguments associated with this atom.
	 * 
	 * @return The arguments associated with this atom
	 */
	public Term[] getArguments();
	
	/**
	 * Returns the truth value of this Atom.
	 * 
	 * @return The truth value in [0,1]
	 * @throws IllegalStateException  if this Atom is not ground
	 */
	public double getValue();
	
	/**
	 * Sets the truth value of this Atom.
	 * 
	 * @param value  a truth value in [0,1]
	 * @throws IllegalArgumentException  if value is not in [0,1]
	 * @throws IllegalStateException  if this Atom is not ground
	 */
	public void setValue(double value);
	
	/**
	 * Returns the confidence value of this Atom.
	 * 
	 * @return The confidence value in [0, +Infinity)
	 * @throws IllegalStateException  if this Atom is not ground
	 */
	public double getConfidenceValue();
	
	/**
	 * Sets the confidence value of this Atom.
	 * 
	 * @param value A confidence value in [0, +Infinity)
	 * @throws IllegalArgumentException  if value is not in [0, +Infinity)
	 * @throws IllegalStateException  if this Atom is not ground
	 */
	public void setConfidenceValue(double value);
	
	/**
	 * Registers a ground kernel to receive update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was already registered 
	 */
	public boolean registerGroundKernel(GroundKernel f);
	
	/**
	 * Unregisters a ground kernel, so that it no longer receives update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was never registered
	 */
	public boolean unregisterGroundKernel(GroundKernel f);
	
	/**
	 * Returns a set of all registered ground kernels that match a given kernel.
	 * 
	 * @param f A kernel
	 * @return A set of all registered ground kernels that match f
	 */
	public Set<GroundKernel> getRegisteredGroundKernels(Kernel f);
	
	/**
	 * Returns a set of all registered ground kernels.
	 * 
	 * @return A collection of all registered ground kernels
	 */
	public Collection<GroundKernel> getRegisteredGroundKernels();
	
	/**
	 * Returns the number of registered ground kernels.
	 * 
	 * @return The number of registered ground kernels
	 */
	public int getNumRegisteredGroundKernels();
	
	/**
	 * Returns whether this Atom is ground.
	 * 
	 * @return TRUE if ground; FALSE otherwise
	 */
	public boolean isGround();
	
	/**
	 * Returns the atom status.
	 * 
	 * @return The atom status
	 */
	public AtomStatus getStatus();
	
	/**
	 * Returns whether this atom is part of an atom group.
	 * 
	 * @return TRUE if in group; FALSE otherwise
	 */
	public boolean isAtomGroup();

	/**
	 * Returns the other atoms in this group.
	 * 
	 * @param atommanager An atom manager
	 * @param db A database query
	 * @return A collection of atoms
	 */
	public Collection<Atom> getAtomsInGroup(AtomManager atommanager);
	
	public AtomFunctionVariable getVariable();
	
}
