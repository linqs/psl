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
package edu.umd.cs.psl.model.predicate;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.UniqueID;
import edu.umd.cs.psl.model.argument.Entity;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.argument.type.ArgumentTypes;

/**
 * A commonly used FunctionalPredicate.
 * <p>
 * A SpecialPredicate should be preferred over a user-made FunctionalPredicate
 * or ExternalFunctionalPredicate because some PSL components can evaluate
 * SpecialPredicates more efficiently. For example, a {@link Database} backed
 * by a relational database with an SQL interface might translate some
 * SpecialPredicates directly to SQL.
 * <p>
 * The names of SpecialPredicates begin with '#'.
 */
public enum SpecialPredicate implements FunctionalPredicate {
	
	/** True if arguments are equal. */
	Equal {

		@Override
		public double computeValue(GroundTerm... args) {
			assert args.length==2;
			assert args[0] instanceof Entity && args[1] instanceof Entity;
			return (args[0].equals(args[1])) ? 1.0 : 0.0;
		}

		@Override
		public String getName() {
			return "#Equal";
		}
		
	},
	
	/** True if arguments are not equal. */
	Unequal {

		@Override
		public double computeValue(GroundTerm... args) {
			assert args.length==2;
			assert args[0] instanceof Entity && args[1] instanceof Entity;
			return (!args[0].equals(args[1])) ? 1.0 : 0.0;
		}

		@Override
		public String getName() {
			return "#NotEqual";
		}		
	},

	/**
	 * True if the first argument's {@link UniqueID} is less than the second's.
	 * 
	 * Used to ground only one of a symmetric pair of ground rules.
	 */
	NonSymmetric {
		
		@Override
		public double computeValue(GroundTerm... args) {
			assert args.length==2;
			assert args[0] instanceof Entity && args[1] instanceof Entity;
			UniqueID uid1 = ((Entity)args[0]).getID();
			UniqueID uid2 = ((Entity)args[1]).getID();
			return (uid1.compareTo(uid2) < 0) ? 1.0 : 0.0;
		}

		@Override
		public String getName() {
			return "#NonSymmetric";
		}
		
	};
	
	@Override
	public ArgumentType getArgumentType(int position) {
		assert position>=0 && position<2;
		return ArgumentTypes.Entity;
	}

	@Override
	public int getArity() {
		return 2;
	}
	
}
