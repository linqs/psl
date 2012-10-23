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

import java.util.HashMap;
import java.util.Map;

import com.google.common.collect.Iterables;

import edu.umd.cs.psl.model.argument.type.ArgumentType;
import edu.umd.cs.psl.model.function.ExternalFunction;

/**
 * A factory for Predicates.
 * <p>
 * PredicateFactories act as namespaces, so a Predicate's name must be unique
 * among those made by a particular factory.
 */
public class PredicateFactory {

	private final Map<String,Predicate> predicateByName;
	
	/** Sole constructor. */
	public PredicateFactory() {
		predicateByName = new HashMap<String,Predicate>();
	}
	
	/**
	 * Constructs a StandardPredicate and registers it in this factory's namespace.
	 *
	 * @param name  name for the new predicate
	 * @param types  types for each of the predicate's arguments
	 * @throws IllegalArgumentException
	 *             if this factory already constructed a predicate with the same name
	 * @throws IllegalArgumentException  if name begins with '#'
	 * @return the newly constructed predicate
	 */
	public StandardPredicate createStandardPredicate(String name, ArgumentType[] types) {
		StandardPredicate p = new StandardPredicate(name, types);
		addPredicate(p,name);
		return p;
	}
	
	/**
	 * Constructs an ExternalFunctionalPredicate and registers it in this factory's namespace.
	 *
	 * @param name  name for the new predicate
	 * @param extFun  the ExternalFunction the new predicate will use
	 * @throws IllegalArgumentException
	 *             if this factory already constructed a predicate with the same name
	 * @throws IllegalArgumentException  if name begins with '#'
	 * @return the newly constructed predicate
	 */
	public ExternalFunctionalPredicate createFunctionalPredicate(String name, ExternalFunction extFun) {
		ExternalFunctionalPredicate p = new ExternalFunctionalPredicate(name, extFun);
		addPredicate(p,name);
		return p;
	}
	
	private void addPredicate(Predicate p, String name) {
		if (predicateByName.containsKey(name))
			throw new IllegalArgumentException("Predicate '" + name + "' has already been created.");
		predicateByName.put(name, p);

	}
	
	public Predicate getPredicate(String name) {
		if (!predicateByName.containsKey(name)) throw new IllegalArgumentException("Predicate '" + name + "' is unknown.");
		return predicateByName.get(name);
	}
	
	public boolean hasPredicate(String name) {
		return predicateByName.containsKey(name);
	}
	
	public boolean hasPredicate(Predicate p) {
		return hasPredicate(p.getName());
	}
	
	public Iterable<FunctionalPredicate> getFunctionalPredicates() {
		return Iterables.filter(predicateByName.values(), FunctionalPredicate.class);
	}
	
	public Iterable<StandardPredicate> getStandardPredicates() {
		return Iterables.filter(predicateByName.values(), StandardPredicate.class);
	}
	
	public Iterable<Predicate> getPredicates() {
		return predicateByName.values();
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
