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

import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;

public class SoftMembership<A> implements Membership<A> {
	
	private final Map<A,Double> members;
	
	public SoftMembership() {
		members = new HashMap<A,Double>();
	}
	
	@Override
	public boolean addMember(A element, double degree) {
		if (degree>1.0 || degree<0.0) throw new IllegalArgumentException("Illegal degree: " + degree);
		//Treat as bag
		Double val = members.get(element);
		if (val==null) {
			members.put(element, Double.valueOf(degree));
			return true;
		} else {
			members.put(element, Double.valueOf(degree+val));
			return false;
		}
	}

	@Override
	public double getDegree(A element) {
		if (!members.containsKey(element)) return 0.0;
		else return members.get(element);
	}

	@Override
	public boolean isMember(A element) {
		return members.containsKey(element) && members.get(element).doubleValue()>0.0;
	}

	@Override
	public Iterator<A> iterator() {
		return Iterators.filter(members.keySet().iterator(), new Predicate<A>(){
			@Override
			public boolean apply(A obj) {
				return members.get(obj)>0.0;
			}	
		});
	}

	@Override
	public double size() {
		double total = 0;
		for (Double d : members.values()) {
			total += d;
		}
		return total;
	}
	
	@Override
	public double count() {
		double total = 0;
		for (Double d : members.values()) {
			if (d>0.0) total += Math.max(1.0, d);
		}
		return total;
	}
	
	
	
}
