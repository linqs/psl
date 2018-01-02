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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModelFactory;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.DoubleAttribute;
import org.linqs.psl.model.term.IntegerAttribute;
import org.linqs.psl.model.term.LongAttribute;
import org.linqs.psl.model.term.StringAttribute;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.model.term.UniqueIntID;
import org.linqs.psl.model.term.UniqueStringID;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class AtomTest {
	@Test
	/**
	 * Make sure that we can to trivial (and numeric string parsing) type conversions.
	 * This test does not include ConstantType.Date and ConstantType.DeferredFunctionalUniqueID.
	 */
	public void testConversions() {
		Predicate predicateDouble = new StandardPredicate("Double", new ConstantType[]{ConstantType.Double});
		Predicate predicateInteger = new StandardPredicate("Integer", new ConstantType[]{ConstantType.Integer});
		Predicate predicateLong = new StandardPredicate("Long", new ConstantType[]{ConstantType.Long});
		Predicate predicateString = new StandardPredicate("String", new ConstantType[]{ConstantType.String});
		Predicate predicateUniqueIntID = new StandardPredicate("UniqueIntID", new ConstantType[]{ConstantType.UniqueIntID});
		Predicate predicateUniqueStringID = new StandardPredicate("UniqueStringID", new ConstantType[]{ConstantType.UniqueStringID});

		Predicate[] predicates = new Predicate[]{
			predicateDouble,
			predicateInteger,
			predicateLong,
			predicateString,
			predicateUniqueIntID,
			predicateUniqueStringID
		};

		// Make sure to only use term values that can actually be converted into the target types (ie ints).
		Term termDouble = new DoubleAttribute(new Double(1));
		Term termInteger = new IntegerAttribute(new Integer(2));
		Term termLong = new LongAttribute(new Long(3));
		Term termString = new StringAttribute("4");
		Term termUniqueIntID = new UniqueIntID(5);
		Term termUniqueStringID = new UniqueStringID("6");

		// Conversions from the key to its values are allowed.
		// Everything else should throw an exception.
		Map<Term, Set<Predicate>> conversionMap = new HashMap<Term, Set<Predicate>>();

		// We will not truncate doubles.
		conversionMap.put(termDouble, new HashSet<Predicate>(Arrays.asList(
			predicateDouble,
			predicateString,
			predicateUniqueStringID
		)));

		// Integers are also safe to convert to all types.
		conversionMap.put(termInteger, new HashSet<Predicate>(Arrays.asList(
			predicateDouble,
			predicateInteger,
			predicateLong,
			predicateString,
			predicateUniqueIntID,
			predicateUniqueStringID
		)));

		// We will treat longs like ints.
		conversionMap.put(termLong, new HashSet<Predicate>(Arrays.asList(
			predicateDouble,
			predicateInteger,
			predicateLong,
			predicateString,
			predicateUniqueIntID,
			predicateUniqueStringID
		)));

		// Strings can be converted into anything.
		conversionMap.put(termString, new HashSet<Predicate>(Arrays.asList(
			predicateDouble,
			predicateInteger,
			predicateLong,
			predicateString,
			predicateUniqueIntID,
			predicateUniqueStringID
		)));

		// Unique ids are not allowed to be converted to anything else.
		conversionMap.put(termUniqueIntID, new HashSet<Predicate>(Arrays.asList(
			predicateUniqueIntID
		)));

		conversionMap.put(termUniqueStringID, new HashSet<Predicate>(Arrays.asList(
			predicateUniqueStringID
		)));

		for (Term term : conversionMap.keySet()) {
			for (Predicate predicate : predicates) {
				if (conversionMap.get(term).contains(predicate)) {
					// Conversion allowed
					new QueryAtom(predicate, term);
				} else {
					// Conversion disallowed
					try {
						new QueryAtom(predicate, term);
						fail(String.format("Illegal conversion (%s (%s) to %s) did not throw an exception.",
								term, term.getClass().getName(), predicate.getName()));
					} catch (IllegalArgumentException ex) {
						// Expected
					}
				}
			}
		}
	}

	@Test
	/**
	 * Ensure that conversions from strings fail when they should.
	 */
	public void testStringConversions() {
		Predicate predicateDouble = new StandardPredicate("Double", new ConstantType[]{ConstantType.Double});
		Predicate predicateInteger = new StandardPredicate("Integer", new ConstantType[]{ConstantType.Integer});
		Predicate predicateLong = new StandardPredicate("Long", new ConstantType[]{ConstantType.Long});
		Predicate predicateString = new StandardPredicate("String", new ConstantType[]{ConstantType.String});
		Predicate predicateUniqueIntID = new StandardPredicate("UniqueIntID", new ConstantType[]{ConstantType.UniqueIntID});
		Predicate predicateUniqueStringID = new StandardPredicate("UniqueStringID", new ConstantType[]{ConstantType.UniqueStringID});

		Predicate[] predicates = new Predicate[]{
			predicateDouble,
			predicateInteger,
			predicateLong,
			predicateString,
			predicateUniqueIntID,
			predicateUniqueStringID
		};

		// Conversions from the key to its values are allowed.
		// Everything else should throw an exception.
		Map<Term, Set<Predicate>> conversionMap = new HashMap<Term, Set<Predicate>>();

		conversionMap.put(new StringAttribute("aaa"), new HashSet<Predicate>(Arrays.asList(
			predicateString,
			predicateUniqueStringID
		)));

		conversionMap.put(new StringAttribute("1"), new HashSet<Predicate>(Arrays.asList(
			predicateDouble,
			predicateInteger,
			predicateLong,
			predicateString,
			predicateUniqueIntID,
			predicateUniqueStringID
		)));

		conversionMap.put(new StringAttribute("1.0"), new HashSet<Predicate>(Arrays.asList(
			predicateDouble,
			predicateString,
			predicateUniqueStringID
		)));

		conversionMap.put(new StringAttribute("1.a"), new HashSet<Predicate>(Arrays.asList(
			predicateString,
			predicateUniqueStringID
		)));

		for (Term term : conversionMap.keySet()) {
			for (Predicate predicate : predicates) {
				if (conversionMap.get(term).contains(predicate)) {
					// Conversion allowed
					new QueryAtom(predicate, term);
				} else {
					// Conversion disallowed
					try {
						new QueryAtom(predicate, term);
						fail(String.format("Illegal conversion (%s (%s) to %s) did not throw an exception.",
								term, term.getClass().getName(), predicate.getName()));
					} catch (IllegalArgumentException ex) {
						// Expected
					}
				}
			}
		}
	}
}
