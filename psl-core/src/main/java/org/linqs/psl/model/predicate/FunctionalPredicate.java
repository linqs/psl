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

import org.linqs.psl.database.ReadOnlyDatabase;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;

/**
 * A Predicate with {@link GroundAtom GroundAtoms} that have truth values defined
 * by a function of their arguments and the {@link ObservedAtom ObservedAtoms}
 * of the closed {@link StandardPredicate StandardPredicates} of the GroundAtoms'
 * Databases.
 * <p>
 * Before extending this class, users should consider using a
 * {@link SpecialPredicate} or an {@link ExternalFunctionalPredicate}.
 *
 * @author Matthias Broecheler
 */
public abstract class FunctionalPredicate extends Predicate {
	/**
	 * Sole constructor.
	 *
	 * @param name  name for this predicate
	 * @param types  types for each of the predicate's arguments
	 * @see PredicateFactory
	 */
	FunctionalPredicate(String name, ConstantType[] types) {
		super(name, types);
	}

	/**
	 * Computes the truth value of the {@link Atom} of this Predicate
	 * with the given arguments.
	 *
	 * @param db	the connection to the database which is running this query
	 * @param args  the arguments for which the truth value will be computed
	 * @return the computed truth value
	 * @throws IllegalArgumentException  if args is not valid
	 */
	public abstract double computeValue(ReadOnlyDatabase db, Constant... args);
}
