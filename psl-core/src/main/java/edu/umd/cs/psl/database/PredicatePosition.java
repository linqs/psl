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
package edu.umd.cs.psl.database;

import com.google.common.base.Preconditions;

import edu.umd.cs.psl.model.predicate.Predicate;

public class PredicatePosition {

	private final Predicate predicate;
	private final int position;
	
	public PredicatePosition(Predicate p, int pos) {
		Preconditions.checkArgument(pos>=0 && pos<p.getArity());
		position = pos;
		predicate = p;
	}
	
	public Predicate getPredicate() {
		return predicate;
	}
	
	public int getPosition() {
		return position;
	}
	
	public boolean equals(Object other) {
		if (this == other)
			return true;
		
		if (!(other instanceof PredicatePosition))
			return false;
		
		PredicatePosition o = (PredicatePosition) other;
		
		return position == o.position && 
			   predicate.getName().equals(o.predicate.getName()) &&
			   predicate.getArity() == o.predicate.getArity();	
	}
	
	public int hashCode() {
		int hash = 1;
		hash = hash * 31 + position;
		hash = hash * 31 + predicate.getName().hashCode();
		hash = hash * 31 + predicate.getArity();
		return hash;
	}
}
