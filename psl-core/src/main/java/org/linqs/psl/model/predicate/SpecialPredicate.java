/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.model.predicate;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;

/**
 * A commonly used FunctionalPredicate.
 *
 * All specific subclasses/instances are provided here.
 *
 * A SpecialPredicate should be preferred over a user-made FunctionalPredicate
 * or ExternalFunctionalPredicate because some PSL components can evaluate
 * SpecialPredicates more efficiently. For example, a database backed
 * by a relational database with an SQL interface might translate some
 * SpecialPredicates directly to SQL.
 *
 * The names of SpecialPredicates begin with '#'.
 */
public abstract class SpecialPredicate extends FunctionalPredicate {
	private SpecialPredicate(String name, ConstantType[] types) {
		super(name, types);
	}

	/**
	 * True if arguments are equal.
	 */
	public static final SpecialPredicate Equal
		= new SpecialPredicate("#Equal",
				new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

		@Override
		public double computeValue(ReadOnlyDatabase db, Constant... args) {
			checkArguments(getName(), args);
			return (args[0].equals(args[1])) ? 1.0 : 0.0;
		}
	};

	/**
	 * True if arguments are not equal.
	 */
	public static final SpecialPredicate NotEqual
		= new SpecialPredicate("#NotEqual",
				new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

		@Override
		public double computeValue(ReadOnlyDatabase db, Constant... args) {
			checkArguments(getName(), args);
			return (!args[0].equals(args[1])) ? 1.0 : 0.0;
		}
	};

	/**
	 * True if the first argument is less than the second.
	 * Used to ground only one of a symmetric pair of ground rules.
	 */
	public static final SpecialPredicate NonSymmetric
		= new SpecialPredicate("#NonSymmetric",
				new ConstantType[] {ConstantType.DeferredFunctionalUniqueID, ConstantType.DeferredFunctionalUniqueID}) {

		@Override
		public double computeValue(ReadOnlyDatabase db, Constant... args) {
			checkArguments(getName(), args);
			return (args[0].compareTo(args[1]) < 0) ? 1.0 : 0.0;
		}
	};

	private static final void checkArguments(String functionName, Constant[] args) {
		if (args.length != 2) {
			throw new IllegalArgumentException(functionName + " expects two arguments, got " + args.length + ".");
		}

		if (!(args[0] instanceof UniqueIntID || args[0] instanceof UniqueStringID) ||
			 !(args[1] instanceof UniqueIntID || args[1] instanceof UniqueStringID)) {
			throw new IllegalArgumentException(
					String.format("%s expects both arguments to be a Unique*ID. Instead, got: (%s, %s).",
					functionName, args[0].getClass().getName(), args[1].getClass().getName()));
		}

		if (args[0].getClass() != args[1].getClass()) {
			throw new IllegalArgumentException(
					String.format("%s expects both arguments to be Unique*IDs of the same type. Instead, got: (%s, %s).",
					functionName, args[0].getClass().getName(), args[1].getClass().getName()));
		}
	}
}
