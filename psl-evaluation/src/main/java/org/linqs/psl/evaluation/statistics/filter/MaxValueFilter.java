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

import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.term.Term;

import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class MaxValueFilter implements AtomFilter {
	private final Predicate predicate;
	private final int argPosition;

	public MaxValueFilter(Predicate predicate, int argPosition) {
		this.predicate=predicate;
		this.argPosition = argPosition;
	}

	@Override
	public Iterator<GroundAtom> filter(Iterator<GroundAtom> input) {
		Map<ArgumentWrapper,GroundAtom> argMapper = new HashMap<ArgumentWrapper,GroundAtom>();

		while (input.hasNext()) {
			GroundAtom atom = input.next();
			Preconditions.checkArgument(atom.getPredicate().equals(predicate),"Predicate of atom does not match filter predicate!");
			ArgumentWrapper w = new ArgumentWrapper(atom.getArguments());
			GroundAtom existing = argMapper.get(w);
			if (existing==null || existing.getValue()<atom.getValue()) {
				argMapper.put(w, atom);
			}
		}
		return argMapper.values().iterator();
	}

	private class ArgumentWrapper {
		final Term[] args;
		final int hashcode;

		ArgumentWrapper(Term[] args) {
			this.args=args;
			int hash = 0;
			for (int i=0;i<args.length;i++) {
				if (i!=argPosition) {
					hash = hash * 117;
					hash += args[i].hashCode();
				}
			}
			this.hashcode = hash;
		}

		@Override
		public int hashCode() {
			return hashcode;
		}

		@Override
		public boolean equals(Object oth) {
			if (this==oth) return true;
			ArgumentWrapper other = (ArgumentWrapper)oth;
			for (int i=0;i<args.length;i++) {
				if (i!=argPosition && !other.args[i].equals(args[i]))
					return false;
			}
			return true;
		}
	}
}
