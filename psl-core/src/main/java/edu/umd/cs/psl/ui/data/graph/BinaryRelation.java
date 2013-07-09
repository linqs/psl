/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.ui.data.graph;

import org.apache.commons.lang.builder.HashCodeBuilder;

public class BinaryRelation<ET extends EntityType, RT extends RelationType> extends Relation<ET,RT> {
	
	private final Entity<ET,RT> first;
	private final Entity<ET,RT> second;
	
	
	public BinaryRelation(RT type, Entity<ET,RT> _first, Entity<ET,RT> _second) {
		super(type);
		if (type.arity()!=2) throw new AssertionError("Expected binary relation type!");
		first = _first;
		second = _second;
	}


	public Entity<ET,RT> getFirst() {
		return first;
	}


	public Entity<ET,RT> getSecond() {
		return second;
	}
	
	@Override
	public int getArity() {
		return 2;
	}
	
	@Override
	public Entity<ET, RT> get(int pos) {
		switch(pos) {
		case 0: return first;
		case 1: return second;
		default: throw new ArrayIndexOutOfBoundsException();
		}
	}

	@Override
	public int hashCode() {
		return new HashCodeBuilder().appendSuper(super.hashCode()).append(first.hashCode() + second.hashCode()).toHashCode();
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public boolean equals(Object oth) {
		if (!super.equals(oth)) return false;
		BinaryRelation<ET,RT> r = (BinaryRelation<ET,RT>)oth;
		return (first.equals(r.first) && second.equals(r.second)) ||
				(first.equals(r.second) && second.equals(r.first));
	}



	
}
