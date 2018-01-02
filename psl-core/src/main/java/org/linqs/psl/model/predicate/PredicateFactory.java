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

import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.term.ConstantType;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * The factory for Predicates.
 * <p>
 * Uses the Singleton pattern, i.e., only one instance can exist, so use
 * {@link #getFactory()}.
 * <p>
 * Ensures that a single Predicate object exists for each Predicate name.
 */
public class PredicateFactory {
	protected static final PredicateFactory instance = new PredicateFactory();

	protected final Map<String,Predicate> predicateByName;

	protected final Pattern predicateNamePattern;

	/**
	 * Sole constructor.
	 * <p>
	 * Adds each {@link SpecialPredicate} to the set of existing Predicates.
	 *
	 * @see #getFactory()
	 */
	protected PredicateFactory() {
		predicateByName = new HashMap<String,Predicate>();
		predicateNamePattern = Pattern.compile("\\w+");

		predicateByName.put(SpecialPredicate.Equal.getName(), SpecialPredicate.Equal);
		predicateByName.put(SpecialPredicate.NotEqual.getName(), SpecialPredicate.NotEqual);
		predicateByName.put(SpecialPredicate.NonSymmetric.getName(), SpecialPredicate.NonSymmetric);
	}

	public static PredicateFactory getFactory() {
		return instance;
	}

	/**
	 * Constructs a StandardPredicate.
	 * <p>
	 * Returns an existing StandardPredicate if one has the same name and
	 * ArgumentTypes.
	 *
	 * @param name  name for the new predicate
	 * @param types  types for each of the predicate's arguments
	 * @return the newly constructed Predicate
	 * @throws IllegalArgumentException  if name is already used with different
	 *													ArgumentTypes, is already used by
	 *													another type of Predicate, doesn't
	 *													match \w+; types has length zero;
	 *													or an element of types is NULL
	 */
	public StandardPredicate createStandardPredicate(String name, ConstantType... types) {
		name = name.toUpperCase();
		Predicate p = predicateByName.get(name);
		if (p != null) {
			boolean samePredicate = true;
			if (p instanceof StandardPredicate && types.length == p.getArity()) {
				for (int i = 0; i < types.length; i++)
					if (!p.getArgumentType(i).equals(types[i]))
						samePredicate = false;
			}
			else
				samePredicate = false;

			if (samePredicate)
				return (StandardPredicate) p;
			else
				throw new IllegalArgumentException("Name '" + name + "' already" +
						" used by another Predicate: " + p);
		}
		else {
			checkPredicateSignature(name, types);
			StandardPredicate sp = new StandardPredicate(name, types);
			addPredicate(sp,name);
			return sp;
		}
	}

	/**
	 * Constructs an ExternalFunctionalPredicate.
	 * <p>
	 * Returns an existing ExternalFunctionalPredicate if one has the same name
	 * and ExternalFunction.
	 *
	 * @param name  name for the new predicate
	 * @param extFun  the ExternalFunction the new predicate will use
	 * @return the newly constructed Predicate
	 * @throws IllegalArgumentException  if name is already used with different
	 *													ExternalFunction, is already used by
	 *													another type of Predicate, doesn't
	 *													match \w+; types has length zero;
	 *													or extFun does not provide valid ArgumentTypes
	 */
	public ExternalFunctionalPredicate createExternalFunctionalPredicate(String name, ExternalFunction extFun) {
		name = name.toUpperCase();
		Predicate predicate = predicateByName.get(name);
		if (predicate != null) {
			if (predicate instanceof ExternalFunctionalPredicate
					&& ((ExternalFunctionalPredicate)predicate).getExternalFunction().equals(extFun)) {
				return (ExternalFunctionalPredicate)predicate;
			}

			throw new IllegalArgumentException("Name '" + name + "' already" + " used by another Predicate: " + predicate);
		}

		checkPredicateSignature(name, extFun.getArgumentTypes());
		ExternalFunctionalPredicate efp = new ExternalFunctionalPredicate(name, extFun);
		addPredicate(efp,name);
		return efp;
	}

	/**
	 * @throws IllegalArgumentException  if name doesn't match \w+, types has length zero,
	 *													or an element of types is NULL
	 */
	protected void checkPredicateSignature(String name, ConstantType[] types) {
		if (!predicateNamePattern.matcher(name).matches()) {
			throw new IllegalArgumentException("Name must match \\w+");
		}

		if (types.length == 0) {
			throw new IllegalArgumentException("Predicate needs at least one ArgumentType.");
		}

		for (int i = 0; i < types.length; i++) {
			if (types[i] == null) {
				throw new IllegalArgumentException("No ArgumentType may be NULL.");
			}
		}
	}

	protected void addPredicate(Predicate p, String name) {
		predicateByName.put(name, p);
	}

	/**
	 * Gets the Predicate with the given name, if it exists
	 *
	 * @param name  the name to match
	 * @return the Predicate, or NULL if no Predicate with that name exists
	 */
	public Predicate getPredicate(String name) {
		return predicateByName.get(name.toUpperCase());
	}

	public Collection<Predicate> getPredicates() {
		return Collections.unmodifiableCollection(predicateByName.values());
	}

	@Override
	public String toString() {
		StringBuilder s = new StringBuilder();
		for (Predicate p : predicateByName.values()) {
			s.append(p.toString()).append("\n");
		}
		return s.toString();
	}
}
