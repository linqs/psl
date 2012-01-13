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
package edu.umd.cs.psl.model.atom;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class AtomStatusSets {
	
	public static final Set<AtomStatus> RandomVariable;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.add(AtomStatus.UnconsideredRV);
		set.add(AtomStatus.ConsideredRV);
		set.add(AtomStatus.ActiveRV);
		RandomVariable = Collections.unmodifiableSet(set);
	}
	
	public static final Set<AtomStatus> Fixed;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.add(AtomStatus.UnconsideredFixed);
		set.add(AtomStatus.ConsideredFixed);
		Fixed = Collections.unmodifiableSet(set);
	}
	
	public static final Set<AtomStatus> Unconsidered;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.add(AtomStatus.UnconsideredFixed);
		set.add(AtomStatus.UnconsideredRV);
		Unconsidered = Collections.unmodifiableSet(set);
	}

	public static final Set<AtomStatus> Considered;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.add(AtomStatus.ConsideredFixed);
		set.add(AtomStatus.ConsideredRV);
		Considered = Collections.unmodifiableSet(set);
	}
	
	public static final Set<AtomStatus> Active;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.add(AtomStatus.ActiveRV);
		Active = Collections.unmodifiableSet(set);
	}
	
	public static final Set<AtomStatus> ActiveOrConsidered;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.addAll(Active);
		set.addAll(Considered);
		ActiveOrConsidered = Collections.unmodifiableSet(set);
	}
	
	public static final Set<AtomStatus> DefinedAndGround;
	static {
		Set<AtomStatus> set = new HashSet<AtomStatus>();
		set.addAll(Active);
		set.addAll(Considered);
		set.addAll(Unconsidered);
		DefinedAndGround = Collections.unmodifiableSet(set);
	}
	
}
