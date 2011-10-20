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

//import static edu.umd.cs.psl.model.atom.AtomEvent.*;

/**
 * Commonly used combinations of various {@link AtomEvent AtomEvents}.
 */
public enum AtomEventSets {

	ReleasedCertainty {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.ReleasedCertainty);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},	
	
	MadeRevokedCertainty {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.MadeCertainty,AtomEvent.RevokedCertainty);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	IntroducedInferenceAtom {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.IntroducedCertainty,AtomEvent.IntroducedRV);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	ReleasedInferenceAtom {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.ReleasedCertainty,AtomEvent.ReleasedRV);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	NewCertaintyEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.IntroducedCertainty, AtomEvent.MadeCertainty);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	CertaintyEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.IntroducedCertainty, AtomEvent.ReleasedCertainty,AtomEvent.ActivatedCertainty,
				AtomEvent.DeactivatedCertainty,AtomEvent.RevokedCertainty, AtomEvent.MadeCertainty);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	IntroducedReleasedInferenceAtom {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.IntroducedCertainty,AtomEvent.IntroducedRV,AtomEvent.ReleasedCertainty,AtomEvent.ReleasedRV);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	AllFactEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.ChangedFactFromDefault,AtomEvent.ChangedFactToDefault,AtomEvent.ChangedFactNonDefault,AtomEvent.ChangedFactInDefault);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	NonDefaultFactEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.ChangedFactFromDefault,AtomEvent.ChangedFactToDefault,AtomEvent.ChangedFactNonDefault);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	ActivationEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.ActivatedRV,AtomEvent.ActivatedCertainty);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},	
	
	DeactivationEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.DeactivatedCertainty,AtomEvent.DeactivatedRV);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},
	
	DeOrActivationEvent {
		private Set<AtomEvent> entailment = ImmutableSet.of(AtomEvent.DeactivatedCertainty,AtomEvent.DeactivatedRV,AtomEvent.ActivatedRV,AtomEvent.ActivatedCertainty);
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}	
	},

	
	All {
		private Set<AtomEvent> entailment = ImmutableSet.copyOf(AtomEvent.values());
		@Override
		public Set<AtomEvent> subsumes() {
			return entailment;
		}		
	};
	
	public abstract Set<AtomEvent> subsumes();
	
	public boolean subsumes(AtomEvent e) {
		return this.subsumes().contains(e);
	}
	
}
