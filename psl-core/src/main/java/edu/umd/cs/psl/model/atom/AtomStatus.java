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

/**
 * A status of an {@link Atom}.
 */
public enum AtomStatus {
	
	Template {
		@Override
		public String toString() {
			return "Template";
		}
	}, 
	
	Undefined {
		@Override
		public String toString() {
			return "Undefined";
		}
	}, 
	
	UnconsideredFixed {
		@Override
		public String toString() {
			return "UnconsideredFixed";
		}
	}, 
	
	ConsideredFixed {
		@Override
		public String toString() {
			return "ConsideredFixed";
		}		
	},
	
	UnconsideredRV {
		@Override
		public String toString() {
			return "UnconsideredRV";
		}
	}, 
	
	ConsideredRV {
		@Override
		public String toString() {
			return "ConsideredRV";
		}
	}, 
	
	ActiveRV {
		@Override
		public String toString() {
			return "ActiveRV";
		}		
	};
	
	public AtomStatus consider() {
		switch(this) {
		case UnconsideredFixed:
			return ConsideredFixed;
		case UnconsideredRV:
			return ConsideredRV;
		default: throw new UnsupportedOperationException("Cannot consider on status: " + this);
		}		
	}
	
	public AtomStatus unconsider() {
		switch(this) {
		case ConsideredFixed:
			return UnconsideredFixed;
		case ConsideredRV:
			return UnconsideredRV;
		default:
			throw new UnsupportedOperationException("Cannot unconsider on status: " + this);
		}		
	}
	
	public AtomStatus release() {
		switch(this) {
		case UnconsideredFixed:
		case UnconsideredRV:
			return Undefined;
		default:
			throw new UnsupportedOperationException("Cannot release on status: " + this);		
		}
	}
	
	public AtomStatus activate() {
		switch(this) {
		case ConsideredRV:
			return ActiveRV;
		default:
			throw new UnsupportedOperationException("Cannot activate on status: " + this);
		}
	}
	
	public AtomStatus deactivate() {
		switch(this) {
		case ActiveRV:
			return ConsideredRV;
		default:
			throw new UnsupportedOperationException("Cannot deactivate on status: " + this);
		}
	}

	public boolean isRandomVariable() {
		return AtomStatusSets.RandomVariable.contains(this);
	}
	
	public boolean isFixed() {
		return AtomStatusSets.Fixed.contains(this);
	}
	
	public boolean isUnconsidered() {
		return AtomStatusSets.Unconsidered.contains(this);
	}
	
	public boolean isConsidered() {
		return AtomStatusSets.Considered.contains(this);
	}
	
	public boolean isActive() {
		return AtomStatusSets.Active.contains(this);
	}
	
	public boolean isActiveOrConsidered() {
		return AtomStatusSets.ActiveOrConsidered.contains(this);
	}
	
	public boolean isDefinedAndGround() {
		return AtomStatusSets.DefinedAndGround.contains(this);
	}
	
}
