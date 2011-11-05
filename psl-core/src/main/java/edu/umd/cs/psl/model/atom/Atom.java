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

import edu.umd.cs.psl.database.DatabaseAtomStoreQuery;
import edu.umd.cs.psl.model.argument.Term;
import edu.umd.cs.psl.model.formula.Formula;
import edu.umd.cs.psl.model.kernel.GroundKernel;
import edu.umd.cs.psl.model.kernel.Kernel;
import edu.umd.cs.psl.model.predicate.Predicate;
import edu.umd.cs.psl.reasoner.function.AtomFunctionVariable;


/**
 * The abstract Atom is the base class for all atoms with a predicate and arguments.
 * It defines most of the standard functionality of predicate atoms with some of it implemented in its
 * two child classes.
 * 
 * FormulaAtom must be constructed through the static create() functions to ensure a unique memory
 * representation.
 * 
 * @author Matthias Broecheler
 *
 */
public interface Atom extends Formula {
	
	/**
	 * Returns the predicate associated with this atom.
	 * 
	 * @return A predicate
	 */
	public Predicate getPredicate();
	
	/**
	 * Returns the cardinality of the atom values.
	 * 
	 * Atoms in PSL can be multi-valued vectors.
	 * Currently, this functionality is not yet supported, so this function should always return 1.
	 * 
	 * @return The number of values
	 */
	public int getNumberOfValues();
	
	/**
	 * Returns the number of arguments to the associated predicate.
	 * 
	 * @return The number of arguments
	 */
	public int getArity();
	
	/**
	 * Sets the soft values.
	 * 
	 * @param val An array of soft values
	 */
	public void setSoftValues(double[] val);

	/**
	 * Sets the soft value at a given index.
	 * 
	 * @param pos A zero-based index
	 * @param val A soft value
	 */
	public void setSoftValue(int pos, double val);
	
	/**
	 * Sets the confidence values.
	 * 
	 * @param val An array of confidence values
	 */
	public void setConfidenceValues(double[] val);
	
	/**
	 * Sets the confidence values at a given index.
	 * 
	 * @param pos A zero-based index
	 * @param val A confidence value
	 */
	public void setConfidenceValue(int pos, double val);
	
	/**
	 * Returns whether the atom has the {@link Predicate predicate's} default value.
	 * 
	 * For information on default value, refer to {@link Predicate}.
	 * 
	 * @return TRUE if default value; FALSE otherwise
	 */
	public boolean hasNonDefaultValues();
	
	/**
	 * Returns the soft value at a given index.
	 * 
	 * @param pos A zero-based index
	 * @return The soft value at index pos
	 */
	public double getSoftValue(int pos);
	
	/**
	 * Returns all soft values.
	 * 
	 * @return An array of soft values
	 */
	public double[] getSoftValues();
	
	/**
	 * Returns the confidence values at a given index.
	 * 
	 * @param pos A zero-based index
	 * @return The confidence value at index pos
	 */
	public double getConfidenceValue(int pos);

	/**
	 * Returns all confidence values.
	 * 
	 * @return An array of confidence values
	 */
	public double[] getConfidenceValues();
	
	/**
	 * Registers a ground kernel to receive update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was already registered 
	 */
	public boolean registerGroundKernel(GroundKernel f);
	
	/**
	 * Deregisters a ground kernel, so that it no longer receives update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was never registered
	 */
	public boolean deregisterGroundKernel(GroundKernel f);
	
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
	public Collection<GroundKernel> getAllRegisteredGroundKernels();
	
	/**
	 * Returns the number of registered ground kernels.
	 * 
	 * @return The number of registered ground kernels
	 */
	public int getNumRegisteredGroundKernels();
	
	/**
	 * Returns the arguments associated with this atom.
	 * 
	 * @return The arguments associated with this atom
	 */
	public Term[] getArguments();
	
	/**
	 * Returns whether this atom is ground.
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
	 * Returns whether this atom is a fact atom.
	 * 
	 * @return TRUE if fact; FALSE otherwise
	 */
	public boolean isFactAtom();
	
	/**
	 * Returns whether this atom is an inference atom.
	 * 
	 * @return TRUE if inference; FALSE otherwise
	 */
	public boolean isInferenceAtom();
	
	/**
	 * Returns whether this atom is a known atom.
	 * 
	 * @return TRUE if known; FALSE otherwise
	 */
	public boolean isKnownAtom();
	
	/**
	 * Returns whether this atom is a certainty.
	 * 
	 * @return TRUE if certainty; FALSE otherwise
	 */
	public boolean isCertainty();
	
	/**
	 * Returns whether this atom is a random variable.
	 * 
	 * @return TRUE if random variable; FALSE otherwise
	 */
	public boolean isRandomVariable();
	
	/**
	 * Returns whether this atom is active.
	 * 
	 * @return TRUE if active; FALSE otherwise
	 */
	public boolean isActive();
	
	/**
	 * Returns whether this atom is considered.
	 * 
	 * @return TRUE if considered; FALSE otherwise
	 */
	public boolean isConsidered();
	
	/**
	 * Returns whether this atom is unconsidered.
	 * 
	 * @return TRUE if unconsidered; FALSE otherwise
	 */
	public boolean isUnconsidered();
	
	/**
	 * Returns whether this atom is defined.
	 * 
	 * @return TRUE if defined; FALSE otherwise
	 */
	public boolean isDefined();
	
	/**
	 * Returns whether this atom is either considered or active.
	 * 
	 * @return TRUE if considered or active; FALSE otherwise
	 */
	public boolean isConsideredOrActive();
	
	/**
	 * Deletes this atom.
	 */
	void delete();

	/**
	 * Make this atom considered.
	 */
	void consider();
	
	/**
	 * Make this atom unconsidered.
	 */
	void unconsider();
	
	/**
	 * Activates this atom.
	 */
	void activate();
	
	/**
	 * Deactivates this atom.
	 */
	void deactivate();
	
	/**
	 * Makes this atom certain.
	 */
	void makeCertain();
	
	/**
	 * Revokes this atom's certainty.
	 */
	void revokeCertain();

	/**
	 * Returns the name.
	 * 
	 * @return The name
	 */
	public String getName();

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
	public Collection<Atom> getAtomsInGroup(AtomManager atommanager, DatabaseAtomStoreQuery db);
	
	/*
	 * ###### FunctionVariable Interface ##########
	 */
	
	public AtomFunctionVariable getVariable();
	
	public AtomFunctionVariable getVariable(int position);	


	
}
