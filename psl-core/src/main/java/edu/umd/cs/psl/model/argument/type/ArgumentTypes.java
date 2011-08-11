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
package edu.umd.cs.psl.model.argument.type;

/**
 * An enum containing the various types of arguments.
 * 
 * @author 
 *
 */
public enum ArgumentTypes implements ArgumentType {

	/**
	 * A textual argument.
	 */
	Text {
		@Override
		public boolean isAttribute() { return true;	}
		@Override
		public boolean isEntity() { return false; }
		@Override
		public boolean isSubTypeOf(ArgumentType t) {
			if (t==ArgumentTypes.Text) return true;
			else return false;
		}
		@Override
		public String getName() { return "Text"; }
	},
	
	/**
	 * A numerical argument.
	 */
	Number {
		@Override
		public boolean isAttribute() { return true;	}
		@Override
		public boolean isEntity() { return false; }
		@Override
		public boolean isSubTypeOf(ArgumentType t) {
			if (t==ArgumentTypes.Number) return true;
			else return false;
		}
		@Override
		public String getName() { return "Number"; }
	},
	
	/**
	 * An entity argument.
	 */
	Entity {
		@Override
		public boolean isAttribute() { return false; }
		@Override
		public boolean isEntity() { return true; }
		@Override
		public boolean isSubTypeOf(ArgumentType t) {
			if (t==ArgumentTypes.Entity) return true;
			else return false;
		}
		@Override
		public String getName() { return "Entity"; }
	};
	
}
