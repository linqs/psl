/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

/**
 * An Atom with only {@link Constant GroundTerms} for arguments.
 * <p>
 * A GroundAtom has a truth value and a confidence value.
 */
abstract public class GroundAtom extends Atom {
	final protected Database db;
	
	protected double value;
	
	protected double confidenceValue;
	
	private static final Set<GroundRule> emptyGroundKernels = ImmutableSet.of();
	protected SetMultimap<Rule, GroundRule> registeredGroundKernels;

	protected GroundAtom(Predicate p, Constant[] args, Database db, double value,
			double confidenceValue) {
		super(p, args);
		this.db = db;
		this.value = value;
		this.confidenceValue = confidenceValue;
		
		/* Until a ground kernel is registered, the empty ground kernels set 
		 * will be used / returned to indicate an empty set. */
		this.registeredGroundKernels = null;
	}
	
	@Override
	public Constant[] getArguments() {
		return Arrays.copyOf((Constant[]) arguments, arguments.length);
	}

	/**
	 * @return The truth value of this Atom
	 */
	public double getValue() {
		return value;
	}
	
	/**
	 * @return The confidence value of this Atom
	 */
	public double getConfidenceValue() {
		return confidenceValue;
	}
	
	abstract public AtomFunctionVariable getVariable();
	
	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		/* No Variables in GroundAtoms */
		return varMap;
	}
	
	/**
	 * Registers a ground kernel to receive update events.
	 * <p>
	 * Any GroundKernel that is a function of this Atom should be registered.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was already registered 
	 */
	public boolean registerGroundKernel(GroundRule f) {
		if (registeredGroundKernels == null)
			registeredGroundKernels = HashMultimap.create();
		return registeredGroundKernels.put(f.getRule(), f);
	}
	
	/**
	 * Unregisters a ground kernel, so that it no longer receives update events.
	 * 
	 * @param f A ground kernel
	 * @return TRUE if successful; FALSE if kernel was never registered
	 */
	public boolean unregisterGroundKernel(GroundRule f) {
		if (registeredGroundKernels == null)
			return false;
		return registeredGroundKernels.remove(f.getRule(), f);
	}
	
	/**
	 * Returns a set of all registered ground kernels that match a given kernel.
	 * 
	 * @param f A kernel
	 * @return A set of all registered ground kernels that match f
	 */
	public Set<GroundRule> getRegisteredGroundKernels(Rule f) {
		if (registeredGroundKernels == null)
			return emptyGroundKernels;
		return registeredGroundKernels.get(f);
	}
	
	/**
	 * Returns a set of all registered ground kernels.
	 * 
	 * @return A collection of all registered ground kernels
	 */
	public Collection<GroundRule> getRegisteredGroundKernels() {
		if (registeredGroundKernels == null)
			return emptyGroundKernels;
		return registeredGroundKernels.values();
	}
	
	/**
	 * Returns the number of registered ground kernels.
	 * 
	 * @return The number of registered ground kernels
	 */
	public int getNumRegisteredGroundKernels() {
		if (registeredGroundKernels == null)
			return 0;
		return registeredGroundKernels.size();
	}

}
