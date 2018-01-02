/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.reasoner.term;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;

import java.util.ArrayList;
import java.util.List;

/**
 * A place to store terms that are to be optimized.
 */
public interface TermStore<E extends Term> extends Iterable<E> {
	/**
	 * Add a term to the store that was generated from the given ground rule.
	 */
	public void add(GroundRule rule, E term);

	/**
	 * Remove any existing terms and prepare for a new set.
	 */
	public void clear();

	/**
	 * Close down the term store, it will not be used any more.
	 */
	public void close();

	public E get(int index);

	public int size();

	public void updateWeight(WeightedGroundRule rule);

	/**
	 * Get the indicies for all terms related to a specific rule.
	 */
	public List<Integer> getTermIndices(WeightedGroundRule rule);
}
