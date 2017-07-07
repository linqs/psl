/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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
package org.linqs.psl.model.atom;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.VariableTypeMap;
import org.linqs.psl.reasoner.function.AtomFunctionVariable;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;

/**
 * An Atom with only {@link Constant GroundTerms} for arguments.
 * <p>
 * A GroundAtom has a truth value.
 */
abstract public class GroundAtom extends Atom {
	private static final Set<GroundRule> emptyGroundRules = ImmutableSet.of();

	final protected Database db;

	protected double value;
	
	protected SetMultimap<Rule, GroundRule> registeredGroundRules;

	protected GroundAtom(Predicate p, Constant[] args, Database db, double value) {
		super(p, args);
		this.db = db;
		this.value = value;
		
		/* Until a ground rule is registered, the empty ground rules set 
		 * will be used / returned to indicate an empty set. */
		this.registeredGroundRules = null;
	}

	@Override
	public Constant[] getArguments() {
		return Arrays.copyOf((Constant[]) arguments, arguments.length);
	}

	/**
	 * @return The truth value of this Atom
	 */
	public double getValue() {
		return value;
	}

	abstract public AtomFunctionVariable getVariable();

	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		/* No Variables in GroundAtoms */
		return varMap;
	}

	/**
	 * Registers a ground rule to receive update events.
	 * <p>
	 * Any GroundRule that is a function of this Atom should be registered.
	 * 
	 * @param rule A ground rule
	 * @return TRUE if successful; FALSE if rule was already registered 
	 */
	public boolean registerGroundRule(GroundRule rule) {
		if (registeredGroundRules == null)
			registeredGroundRules = HashMultimap.create();
		return registeredGroundRules.put(rule.getRule(), rule);
	}

	/**
	 * Unregisters a ground rule, so that it no longer receives update events.
	 * 
	 * @param rule A ground rule
	 * @return TRUE if successful; FALSE if rule was never registered
	 */
	public boolean unregisterGroundRule(GroundRule rule) {
		if (registeredGroundRules == null)
			return false;
		return registeredGroundRules.remove(rule.getRule(), rule);
	}

	/**
	 * Returns a set of all registered ground rules that match a given rule.
	 * 
	 * @param rule A rule
	 * @return A set of all registered ground rules that match rule
	 */
	public Set<GroundRule> getRegisteredGroundRules(Rule rule) {
		if (registeredGroundRules == null)
			return emptyGroundRules;
		return registeredGroundRules.get(rule);
	}

	/**
	 * Returns a set of all registered ground rules.
	 * 
	 * @return A collection of all registered ground rules
	 */
	public Collection<GroundRule> getRegisteredGroundRules() {
		if (registeredGroundRules == null)
			return emptyGroundRules;
		return registeredGroundRules.values();
	}

	/**
	 * Returns the number of registered ground rules.
	 * 
	 * @return The number of registered ground rules
	 */
	public int getNumRegisteredGroundRules() {
		if (registeredGroundRules == null)
			return 0;
		return registeredGroundRules.size();
	}
}
