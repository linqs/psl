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
package edu.umd.cs.psl.model.set.membership;

import java.util.*;

public class ConstantMembership<A> implements Membership<A> {

	private final Set<A> members;
	
	public ConstantMembership() {
		members = new HashSet<A>();
	}
	
	@Override
	public boolean addMember(A element, double degree) {
		if (degree!=1.0) throw new IllegalArgumentException("Constant membership does not support fuzzy degrees");
		return members.add(element);
		
	}

	@Override
	public double getDegree(A element) {
		if (members.contains(element)) return 1.0;
		else return 0.0;
	}

	@Override
	public boolean isMember(A element) {
		return members.contains(element);
	}

	@Override
	public Iterator<A> iterator() {
		return members.iterator();
	}

	@Override
	public double size() {
		return members.size();
	}
	
	@Override
	public double count() {
		return members.size();
	}

}
