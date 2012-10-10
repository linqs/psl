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

import java.util.Set;

import com.google.common.collect.ImmutableSet;

/**
 * Commonly used combinations of {@link AtomEvent AtomEvents}.
 */
public class AtomEventSets {

	public static final Set<AtomEvent> ConsideredGroundAtom;
	static {
		ConsideredGroundAtom = ImmutableSet.of(
				AtomEvent.ConsideredFixedAtom,
				AtomEvent.ConsideredRVAtom);
	}
	
	public static final Set<AtomEvent> ConsideredRVAtom;
	static {
		ConsideredRVAtom = ImmutableSet.of(
				AtomEvent.ConsideredRVAtom);
	}
	
	public static final Set<AtomEvent> DeOrActivationEvent;
	static {
		DeOrActivationEvent = ImmutableSet.of(
				AtomEvent.ActivatedFixedAtom,
				AtomEvent.DeactivatedFixedAtom,
				AtomEvent.ActivatedRVAtom,
				AtomEvent.DeactivatedRVAtom);
	}
	
	public static final Set<AtomEvent> ModifiedFixedAtom;
	static {
		ModifiedFixedAtom = ImmutableSet.of(
				AtomEvent.FixedAtom,
				AtomEvent.UnfixedAtom,
				AtomEvent.ChangedTruthValueOfFixedAtom,
				AtomEvent.ChangedConfidenceValueOfFixedAtom);
	}
	
	public static final Set<AtomEvent> All;
	static {
		All = ImmutableSet.copyOf(AtomEvent.values());	
	}
	
}
