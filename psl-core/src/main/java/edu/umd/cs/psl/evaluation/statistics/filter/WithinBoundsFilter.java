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
package edu.umd.cs.psl.evaluation.statistics.filter;

import java.util.Iterator;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

import edu.umd.cs.psl.model.atom.Atom;

public class WithinBoundsFilter implements AtomFilter {

	private final double[] bounds;
	
	public WithinBoundsFilter(double[] bounds) {
		Preconditions.checkArgument(bounds[0]<=bounds[1],"Invalid bounds specified!");
		this.bounds = bounds;
	}
	
	
	@Override
	public Iterator<Atom> filter(Iterator<Atom> input) {
		return Iterators.filter(input, new Predicate<Atom>() {

			@Override
			public boolean apply(Atom atom) {
				for (int i=0;i<atom.getArity();i++) {
					double val = atom.getSoftValue(i);
					if (val<bounds[0] || val>bounds[1]) return false;
				}
				return true;
			}
			
		});
	}

}
