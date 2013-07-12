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
package edu.umd.cs.psl.model.predicate;

import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.ReadOnlyDatabase;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.argument.UniqueID;

/**
 * A commonly used FunctionalPredicate.
 * <p>
 * All specific subclasses/instances are provided here.
 * <p>
 * A SpecialPredicate should be preferred over a user-made FunctionalPredicate
 * or ExternalFunctionalPredicate because some PSL components can evaluate
 * SpecialPredicates more efficiently. For example, a {@link Database} backed
 * by a relational database with an SQL interface might translate some
 * SpecialPredicates directly to SQL.
 * <p>
 * The names of SpecialPredicates begin with '#'.
 */
abstract public class SpecialPredicate extends FunctionalPredicate {
	
	private SpecialPredicate(String name, ArgumentType[] types) {
		super(name, types);
	}
	
	/** True if arguments are equal. */
	public static final SpecialPredicate Equal
		= new SpecialPredicate("#Equal", new ArgumentType[] {ArgumentType.UniqueID, ArgumentType.UniqueID}) {
		
		@Override
		public double computeValue(ReadOnlyDatabase db, GroundTerm... args) {
			if (args.length==2 && ArgumentType.UniqueID.isInstance(args[0]) && ArgumentType.UniqueID.isInstance(args[1]))
				return (args[0].equals(args[1])) ? 1.0 : 0.0;
			else
				throw new IllegalArgumentException(getName() + " acts on two Entities.");
		}
	};
	
	/** True if arguments are not equal. */
	public static final SpecialPredicate NotEqual
		= new SpecialPredicate("#NotEqual", new ArgumentType[] {ArgumentType.UniqueID, ArgumentType.UniqueID}) {

		@Override
		public double computeValue(ReadOnlyDatabase db, GroundTerm... args) {
			if (args.length==2 && ArgumentType.UniqueID.isInstance(args[0]) && ArgumentType.UniqueID.isInstance(args[1]))
				return (!args[0].equals(args[1])) ? 1.0 : 0.0;
			else
				throw new IllegalArgumentException(getName() + " acts on two Entities.");
		}
	};

	/**
	 * True if the first argument is less than the second.
	 * <p>
	 * Used to ground only one of a symmetric pair of ground rules.
	 */
	public static final SpecialPredicate NonSymmetric
		= new SpecialPredicate("#NonSymmetric", new ArgumentType[] {ArgumentType.UniqueID, ArgumentType.UniqueID}) {
		
		@Override
		public double computeValue(ReadOnlyDatabase db, GroundTerm... args) {
			if (args.length==2 && ArgumentType.UniqueID.isInstance(args[0]) && ArgumentType.UniqueID.isInstance(args[1])) {
				UniqueID uid1 = ((UniqueID)args[0]);
				UniqueID uid2 = ((UniqueID)args[1]);
				return (uid1.compareTo(uid2) < 0) ? 1.0 : 0.0;
			}
			else
				throw new IllegalArgumentException(getName() + " acts on two Entities.");
		}
	};
	
}
