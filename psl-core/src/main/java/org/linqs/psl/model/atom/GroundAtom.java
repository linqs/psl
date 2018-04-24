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
package org.linqs.psl.model.atom;

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

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

/**
 * An Atom with only {@link Constant GroundTerms} for arguments.
 *
 * A GroundAtom has a truth value.
 */
public abstract class GroundAtom extends Atom implements Comparable<GroundAtom> {
	private static final Set<GroundRule> emptyGroundRules = ImmutableSet.of();

	protected final Database db;
	protected double value;

	protected GroundAtom(Predicate predicate, Constant[] args, Database db, double value) {
		super(predicate, args);
		this.db = db;
		this.value = value;
	}

	public Database getDatabase() {
		return db;
	}

	@Override
	public Constant[] getArguments() {
		return (Constant[])arguments;
	}

	/**
	 * @return the truth value of this Atom
	 */
	public double getValue() {
		return value;
	}

	public String toStringWithValue() {
		return super.toString() + " = " + getValue();
	}

	public abstract AtomFunctionVariable getVariable();

	public VariableTypeMap collectVariables(VariableTypeMap varMap) {
		// No Variables in GroundAtoms.
		return varMap;
	}

	/**
	 * First order by value (descending), the predicate name (natural),
	 * and then the arguments (in order).
	 */
	@Override
	public int compareTo(GroundAtom other) {
		if (this.getValue() < other.getValue()) {
			return 1;
		} else if (this.getValue() > other.getValue()) {
			return -1;
		} else {
			int val = this.predicate.getName().compareTo(other.predicate.getName());
			if (val != 0) {
				return val;
			}

			for (int i = 0; i < this.arguments.length; i++) {
				val = this.arguments[i].compareTo(other.arguments[i]);
				if (val != 0) {
					return val;
				}
			}

			return 0;
		}
	}
}
