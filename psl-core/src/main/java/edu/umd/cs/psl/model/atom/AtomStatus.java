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
 * An enum for atom status constants.
 * 
 * @author
 *
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
	
	UnconsideredFact {
		@Override
		public String toString() {
			return "UnconsideredFact";
		}
	}, 
	
	ConsideredFact {
		@Override
		public String toString() {
			return "ConsideredFact";
		}		
	},
	
	UnconsideredCertainty {
		@Override
		public String toString() {
			return "UnconsideredCertainty";
		}
	}, 
	
	ConsideredCertainty {
		@Override
		public String toString() {
			return "ConsideredCertainty";
		}		
	},
	
	ActiveCertainty {
		@Override
		public String toString() {
			return "ActiveCertainty";
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
		case UnconsideredCertainty:
			return ConsideredCertainty;
		case UnconsideredFact:
			return ConsideredFact;
		case UnconsideredRV:
			return ConsideredRV;
		default: throw new UnsupportedOperationException("Cannot consider on status: " + this);
		}		
	}
	
	public AtomStatus unconsider() {
		switch(this) {
		case ConsideredCertainty:
			return UnconsideredCertainty;
		case ConsideredFact:
			return UnconsideredFact;
		case ConsideredRV:
			return UnconsideredRV;
		default: throw new UnsupportedOperationException("Cannot unconsider on status: " + this);
		}		
	}
	
	public AtomStatus delete() {
		switch(this) {
		case UnconsideredCertainty:
		case UnconsideredFact:
		case UnconsideredRV:
			return Undefined;
		default: throw new UnsupportedOperationException("Cannot delete on status: " + this);		
		}
	}
	
	public AtomStatus activate() {
		switch(this) {
		case ConsideredRV:
			return ActiveRV;
		case ConsideredCertainty:
			return ActiveCertainty;
		default: throw new UnsupportedOperationException("Cannot activate on status: " + this);
		}
	}
	
	public AtomStatus deactivate() {
		switch(this) {
		case ActiveRV:
			return ConsideredRV;
		case ActiveCertainty:
			return ConsideredCertainty;
		default: throw new UnsupportedOperationException("Cannot deactivate on status: " + this);
		}
	}

	public AtomStatus makeCertain() {
		switch(this) {
		case UnconsideredRV:
			return UnconsideredCertainty;
		case ConsideredRV:
			return ConsideredCertainty;
		case ActiveRV:
			return ActiveCertainty;
		default: throw new UnsupportedOperationException("Cannot activate on status: " + this);
		}
	}
	
	public AtomStatus revokeCertain() {
		switch(this) {
		case UnconsideredCertainty:
			return UnconsideredRV;
		case ConsideredCertainty:
			return ConsideredRV;
		case ActiveCertainty:
			return ActiveRV;
		default: throw new UnsupportedOperationException("Cannot activate on status: " + this);
		}
	}
	
	public boolean isInference() {
		switch(this) {
		case UnconsideredRV:
		case ConsideredRV:
		case ActiveRV:
		case UnconsideredCertainty:
		case ConsideredCertainty:
		case ActiveCertainty:
			return true;
		default: return false;
		}
	}
	
	public boolean isUndefined() {
		switch(this) {
		case Undefined:
		case Template:
			return true;
		default: return false;
		}
	}
	
	public boolean isDefined() {
		return !isUndefined();
	}
	
	public boolean isKnowledge() {
		switch(this) {
		case UnconsideredFact:
		case ConsideredFact:
		case UnconsideredCertainty:
		case ConsideredCertainty:
		case ActiveCertainty:
			return true;
		default: return false;
		}
	}
	
	public boolean isFact() {
		switch(this) {
		case UnconsideredFact:
		case ConsideredFact:
			return true;
		default: return false;
		}
	}
	
	public boolean isRV() {
		switch(this) {
		case UnconsideredRV:
		case ConsideredRV:
		case ActiveRV:
			return true;
		default: return false;
		}
	}
	
	public boolean isCertainty() {
		switch(this) {
		case UnconsideredCertainty:
		case ConsideredCertainty:
		case ActiveCertainty:
			return true;
		default: return false;
		}
	}
	
	public boolean isActive() {
		switch(this) {
		case ActiveRV:
		case ActiveCertainty:
			return true;
		default: return false;
		}
	}
	
	public boolean isConsidered() {
		switch(this) {
		case ConsideredRV:
		case ConsideredFact:
		case ConsideredCertainty:
			return true;
		default: return false;
		}
	}
	
	public boolean isUnonsidered() {
		switch(this) {
		case UnconsideredRV:
		case UnconsideredFact:
		case UnconsideredCertainty:
			return true;
		default: return false;
		}
	}
	
}
