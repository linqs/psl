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
package edu.umd.cs.psl.model.argument;

/**
 * A type of {@link GroundTerm}.
 * <p>
 * The enumerated types of ArgumentType are used to identify different subtypes
 * of GroundTerm.
 */
public enum ArgumentType {

	/**
	 * A {@link StringAttribute} argument.
	 */
	String {
		@Override
		public String getName() { return "String"; }
		
		@Override
		public boolean isInstance(GroundTerm term) {
			return (term instanceof StringAttribute);
		}
	},
	
	/**
	 * An {@link IntegerAttribute} argument.
	 */
	Integer {
		
		@Override
		public String getName() { return "Integer"; }
		
		@Override
		public boolean isInstance(GroundTerm term) {
			return (term instanceof IntegerAttribute);
		}
	},
	
	/**
	 * A {@link DoubleAttribute} argument.
	 */
	Double {
		
		@Override
		public String getName() { return "Double"; }
		
		@Override
		public boolean isInstance(GroundTerm term) {
			return (term instanceof DoubleAttribute);
		}
	},
	
	/**
	 * A {@link edu.umd.cs.psl.database.UniqueID} argument.
	 */
	UniqueID {
		
		@Override
		public String getName() { return "UniqueID"; }
		
		@Override
		public boolean isInstance(GroundTerm term) {
			return (term instanceof edu.umd.cs.psl.model.argument.UniqueID);
		}
	};
	
	/**
	 * @return a human-friendly String identifier for this ArgumentType
	 */
	abstract public String getName();
	
	/**
	 * Returns whether a GroundTerm is of the type identified by this ArgumentType
	 * 
	 * @param term  the term to check
	 * @return TRUE if term is an instance of the corresponding type
	 */
	abstract public boolean isInstance(GroundTerm term);
	
	public static ArgumentType getType(GroundTerm term) {
		for (ArgumentType type : ArgumentType.values())
			if (type.isInstance(term))
				return type;
		
		throw new IllegalArgumentException("Term is of unknown type : " + term);
	}
	
}
