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
package org.linqs.psl.application.groundrulestore;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.model.rule.WeightedRule;

/**
 * Container for a set of {@link GroundRule GroundRules}.
 * <p>
 * Since this container uses set semantics, no two GroundRules that are equal
 * can be stored in it. If a {@link Rule} wants to add another GroundRule
 * that does the same thing over the same GroundAtoms, then it should retrieve
 * the original GroundRule, modify it, and call {@link #changedGroundRule(GroundRule)}.
 */
public interface GroundRuleStore {

	/**
	 * Adds a GroundRule to this store.
	 * 
	 * @param gr  the GroundRule to add
	 * @throws IllegalArgumentException  if gr is already in this store
	 */
	public void addGroundRule(GroundRule gr);
	
	/**
	 * Notifies this store that a GroundRule was changed.
	 * <p>
	 * Any component that modifies a GroundRule in this store should call
	 * this method.
	 * 
	 * @param gr  the changed GroundRule
	 * @throws IllegalArgumentException  if gr is not in this store
	 */
	public void changedGroundRule(GroundRule gr);
	
	/**
	 * Notifies this store that a {@link WeightedGroundRule}'s weight
	 * was changed.
	 * <p>
	 * This method should be called whenever the weight of a GroundCompatibilityKernel
	 * in this store is changed, or the weight of its parent {@link WeightedRule}
	 * is changed (and the GroundCompatibilityKernel's weight is still tied to it).
	 * <p>
	 * It is not necessary to also call {@link #changedGroundRule(GroundRule)}
	 * if only the weight was changed.
	 * 
	 * @param gk  the ground kernel with a changed weight
	 */
	public void changedGroundKernelWeight(WeightedGroundRule gk);
	
	/**
	 * Equivalent to calling {@link #changedGroundKernelWeight(WeightedGroundRule)}
	 * for all GroundCompatibilityKernels.
	 */
	public void changedGroundKernelWeights();
	
	/**
	 * Removes a GroundKernel from this store.
	 * 
	 * @param gk  the GroundKernel to remove
	 * @throws IllegalArgumentException  if gk is not in this store
	 */
	public void removeGroundKernel(GroundRule gk);
	
	/**
	 * Checks whether a GroundKernel is in this store.
	 * 
	 * @param gk  the GroundKernel to check
	 * @return TRUE if gk is in this store
	 */
	public boolean containsGroundKernel(GroundRule gk);
	
	/**
	 * @return every GroundKernel in this store
	 */
	public Iterable<GroundRule> getGroundKernels();
	
	/**
	 * @return every {@link WeightedGroundRule} in this store
	 */
	public Iterable<WeightedGroundRule> getCompatibilityKernels();
	
	/**
	 * @return every {@link UnweightedGroundRule} in this store
	 */
	public Iterable<UnweightedGroundRule> getConstraintKernels();
	
	/**
	 * Returns every GroundKernel that was instantiated by a given Kernel.
	 * 
	 * @param k  the Kernel of the GroundKernels to return
	 * @return the Kernel's GroundKernels
	 */
	public Iterable<GroundRule> getGroundKernels(Rule k);
	
	/**
	 * @return the number of GroundKernels in this store
	 */
	public int size();
	
}
