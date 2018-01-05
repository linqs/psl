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
package org.linqs.psl.evaluation.statistics.filter;

import java.util.Iterator;

import org.linqs.psl.model.atom.GroundAtom;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

public class OutOfBoundsFilter implements AtomFilter {

	private final double[] bounds;
	
	public OutOfBoundsFilter(double[] bounds) {
		Preconditions.checkArgument(bounds[0]<=bounds[1],"Invalid bounds specified!");
		this.bounds = bounds;
	}
	
	
	@Override
	public Iterator<GroundAtom> filter(Iterator<GroundAtom> input) {
		return Iterators.filter(input, new Predicate<GroundAtom>() {

			@Override
			public boolean apply(GroundAtom atom) {
				for (int i=0;i<atom.getArity();i++) {
					double val = atom.getValue();
					if (val>=bounds[0] && val<=bounds[1]) return false;
				}
				return true;
			}
			
		});
	}

}
