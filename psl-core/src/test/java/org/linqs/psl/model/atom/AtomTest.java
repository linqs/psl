/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.PSLTest;
import org.linqs.psl.TestModel;
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
    /**
     * Make sure that we can to trivial (and numeric string parsing) type conversions.
     * This test does not include ConstantType.DeferredFunctionalUniqueID.
     */
    @Test
    public void testConversions() {
        Predicate predicateDouble = StandardPredicate.get("Double", new ConstantType[]{ConstantType.Double});
        Predicate predicateInteger = StandardPredicate.get("Integer", new ConstantType[]{ConstantType.Integer});
        Predicate predicateLong = StandardPredicate.get("Long", new ConstantType[]{ConstantType.Long});
        Predicate predicateString = StandardPredicate.get("String", new ConstantType[]{ConstantType.String});
        Predicate predicateUniqueIntID = StandardPredicate.get("UniqueIntID", new ConstantType[]{ConstantType.UniqueIntID});
        Predicate predicateUniqueStringID = StandardPredicate.get("UniqueStringID", new ConstantType[]{ConstantType.UniqueStringID});

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

    /**
     * Ensure that conversions from strings fail when they should.
     */
    @Test
    public void testStringConversions() {
        Predicate predicateDouble = StandardPredicate.get("Double", new ConstantType[]{ConstantType.Double});
        Predicate predicateInteger = StandardPredicate.get("Integer", new ConstantType[]{ConstantType.Integer});
        Predicate predicateLong = StandardPredicate.get("Long", new ConstantType[]{ConstantType.Long});
        Predicate predicateString = StandardPredicate.get("String", new ConstantType[]{ConstantType.String});
        Predicate predicateUniqueIntID = StandardPredicate.get("UniqueIntID", new ConstantType[]{ConstantType.UniqueIntID});
        Predicate predicateUniqueStringID = StandardPredicate.get("UniqueStringID", new ConstantType[]{ConstantType.UniqueStringID});

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

    /**
     * Atom equality is a high-traffic piece of code because of Sets and Maps that hold them.
     * As such, equals() has been optimized.
     * Make sure these optmizations don't miss any cases.
     * We are specifically checking Atom.equals(), so no need to have anything more complex than a GetAtom.
     */
    @Test
    public void testEqualityBase() {
        Predicate singleInt = StandardPredicate.get("SingleInt", new ConstantType[]{ConstantType.Integer});
        Predicate doubleInt = StandardPredicate.get("DoubleInt", new ConstantType[]{ConstantType.Integer, ConstantType.Integer});

        Atom[] atoms = new Atom[]{
            new QueryAtom(singleInt, new IntegerAttribute(1)),
            new QueryAtom(singleInt, new IntegerAttribute(1)),
            new QueryAtom(singleInt, new IntegerAttribute(-1)),
            new QueryAtom(singleInt, new IntegerAttribute(2)),
            new QueryAtom(doubleInt, new IntegerAttribute(1), new IntegerAttribute(1)),
            new QueryAtom(doubleInt, new IntegerAttribute(1), new IntegerAttribute(1)),
            new QueryAtom(doubleInt, new IntegerAttribute(-1), new IntegerAttribute(-1)),
            new QueryAtom(doubleInt, new IntegerAttribute(1), new IntegerAttribute(2)),
            new QueryAtom(doubleInt, new IntegerAttribute(2), new IntegerAttribute(1))
        };
        boolean[][] equalityGrid = new boolean[][]{
            new boolean[]{true , true , false, false, false, false, false, false, false},
            new boolean[]{true , true , false, false, false, false, false, false, false},
            new boolean[]{false, false, true , false, false, false, false, false, false},
            new boolean[]{false, false, false, true , false, false, false, false, false},
            new boolean[]{false, false, false, false, true , true , false, false, false},
            new boolean[]{false, false, false, false, true , true , false, false, false},
            new boolean[]{false, false, false, false, false, false, true , false, false},
            new boolean[]{false, false, false, false, false, false, false, true , false},
            new boolean[]{false, false, false, false, false, false, false, false, true }
        };

        for (int i = 0; i < atoms.length; i++) {
            for (int j = 0; j < atoms.length; j++) {
                if (equalityGrid[i][j]) {
                    assertEquals(atoms[i], atoms[j]);
                } else {
                    assertNotEquals(atoms[i], atoms[j]);
                }
            }
        }
    }

    /**
     * Many of the atom equality optimizations center around the argument array and deep equality checks.
     */
    @Test
    public void testEqualityArgs() {
        Predicate singleInt = StandardPredicate.get("SingleInt", new ConstantType[]{ConstantType.Integer});
        Predicate doubleInt = StandardPredicate.get("DoubleInt", new ConstantType[]{ConstantType.Integer, ConstantType.Integer});

        Term[] singleArray1 = new Term[1];
        Term[] singleArray2 = new Term[1];
        Term[] doubleArray1 = new Term[2];
        Term[] doubleArray2 = new Term[2];

        Atom[] atoms = new Atom[16];

        Term term1 = null;
        Term term2 = null;
        Term term3 = null;
        Term term4 = null;

        // Same array, same terms.
        term1 = new IntegerAttribute(1);
        term2 = new IntegerAttribute(2);

        singleArray1[0] = term1;
        doubleArray1[0] = term1;
        doubleArray1[1] = term2;

        atoms[0] = new QueryAtom(singleInt, singleArray1);
        atoms[1] = new QueryAtom(singleInt, singleArray1);
        atoms[2] = new QueryAtom(doubleInt, doubleArray1);
        atoms[3] = new QueryAtom(doubleInt, doubleArray1);

        // Same array, diff terms.
        term1 = new IntegerAttribute(1);
        term2 = new IntegerAttribute(1);
        term3 = new IntegerAttribute(2);
        term4 = new IntegerAttribute(2);

        singleArray1[0] = term1;
        doubleArray1[0] = term1;
        doubleArray1[1] = term3;
        atoms[4] = new QueryAtom(singleInt, singleArray1);
        atoms[5] = new QueryAtom(doubleInt, doubleArray1);

        singleArray1[0] = term2;
        doubleArray1[0] = term2;
        doubleArray1[1] = term4;
        atoms[6] = new QueryAtom(singleInt, singleArray1);
        atoms[7] = new QueryAtom(doubleInt, doubleArray1);

        // Diff array, same terms.
        term1 = new IntegerAttribute(1);
        term2 = new IntegerAttribute(2);

        singleArray1[0] = term1;
        singleArray2[0] = term1;

        doubleArray1[0] = term1;
        doubleArray1[1] = term2;

        doubleArray2[0] = term1;
        doubleArray2[1] = term2;

        atoms[8] = new QueryAtom(singleInt, singleArray1);
        atoms[9] = new QueryAtom(singleInt, singleArray2);
        atoms[10] = new QueryAtom(doubleInt, doubleArray1);
        atoms[11] = new QueryAtom(doubleInt, doubleArray2);

        // Diff array, diff terms.
        term1 = new IntegerAttribute(1);
        term2 = new IntegerAttribute(1);
        term3 = new IntegerAttribute(2);
        term4 = new IntegerAttribute(2);

        singleArray1[0] = term1;
        doubleArray1[0] = term1;
        doubleArray1[1] = term3;
        atoms[12] = new QueryAtom(singleInt, singleArray1);
        atoms[13] = new QueryAtom(doubleInt, doubleArray1);

        singleArray2[0] = term2;
        doubleArray2[0] = term2;
        doubleArray2[1] = term4;
        atoms[14] = new QueryAtom(singleInt, singleArray2);
        atoms[15] = new QueryAtom(doubleInt, doubleArray2);

        // We expect all singles to be the same and all doubles to be the same.

        for (int i = 0; i < atoms.length; i++) {
            for (int j = 0; j < atoms.length; j++) {
                if (atoms[i].getArity() == atoms[j].getArity()) {
                    assertEquals(atoms[i], atoms[j]);
                } else {
                    assertNotEquals(atoms[i], atoms[j]);
                }
            }
        }
    }

    /**
     * Similar to testEqualityArrays(), but change the values for terms, so we expect much less equality.
     */
    @Test
    public void testInequalityArgs() {
        Predicate singleInt = StandardPredicate.get("SingleInt", new ConstantType[]{ConstantType.Integer});
        Predicate doubleInt = StandardPredicate.get("DoubleInt", new ConstantType[]{ConstantType.Integer, ConstantType.Integer});

        Term[] singleArray1 = new Term[1];
        Term[] singleArray2 = new Term[1];
        Term[] doubleArray1 = new Term[2];
        Term[] doubleArray2 = new Term[2];

        Atom[] atoms = new Atom[16];

        Term term1 = null;
        Term term2 = null;
        Term term3 = null;
        Term term4 = null;

        // Same array, same terms.
        term1 = new IntegerAttribute(1);
        term2 = new IntegerAttribute(2);

        singleArray1[0] = term1;
        doubleArray1[0] = term1;
        doubleArray1[1] = term2;

        atoms[0] = new QueryAtom(singleInt, singleArray1);
        atoms[1] = new QueryAtom(singleInt, singleArray1);
        atoms[2] = new QueryAtom(doubleInt, doubleArray1);
        atoms[3] = new QueryAtom(doubleInt, doubleArray1);

        // Same array, diff terms.
        term1 = new IntegerAttribute(3);
        term2 = new IntegerAttribute(4);
        term3 = new IntegerAttribute(5);
        term4 = new IntegerAttribute(6);

        singleArray1[0] = term1;
        doubleArray1[0] = term1;
        doubleArray1[1] = term3;
        atoms[4] = new QueryAtom(singleInt, singleArray1);
        atoms[5] = new QueryAtom(doubleInt, doubleArray1);

        singleArray1[0] = term2;
        doubleArray1[0] = term2;
        doubleArray1[1] = term4;
        atoms[6] = new QueryAtom(singleInt, singleArray1);
        atoms[7] = new QueryAtom(doubleInt, doubleArray1);

        // Diff array, same terms.
        term1 = new IntegerAttribute(7);
        term2 = new IntegerAttribute(8);

        singleArray1[0] = term1;
        singleArray2[0] = term1;

        doubleArray1[0] = term1;
        doubleArray1[1] = term2;

        doubleArray2[0] = term1;
        doubleArray2[1] = term2;

        atoms[8] = new QueryAtom(singleInt, singleArray1);
        atoms[9] = new QueryAtom(singleInt, singleArray2);
        atoms[10] = new QueryAtom(doubleInt, doubleArray1);
        atoms[11] = new QueryAtom(doubleInt, doubleArray2);

        // Diff array, diff terms.
        term1 = new IntegerAttribute(9);
        term2 = new IntegerAttribute(10);
        term3 = new IntegerAttribute(11);
        term4 = new IntegerAttribute(12);

        singleArray1[0] = term1;
        doubleArray1[0] = term1;
        doubleArray1[1] = term3;
        atoms[12] = new QueryAtom(singleInt, singleArray1);
        atoms[13] = new QueryAtom(doubleInt, doubleArray1);

        singleArray2[0] = term2;
        doubleArray2[0] = term2;
        doubleArray2[1] = term4;
        atoms[14] = new QueryAtom(singleInt, singleArray2);
        atoms[15] = new QueryAtom(doubleInt, doubleArray2);

        boolean[][] equalityGrid = new boolean[][]{
            new boolean[]{true , true , false, false, false, false, false, false, false, false, false, false, false, false, false, false}, // 0
            new boolean[]{true , true , false, false, false, false, false, false, false, false, false, false, false, false, false, false}, // 1
            new boolean[]{false, false, true , true , false, false, false, false, false, false, false, false, false, false, false, false}, // 2
            new boolean[]{false, false, true , true , false, false, false, false, false, false, false, false, false, false, false, false}, // 3
            new boolean[]{false, false, false, false, true , false, false, false, false, false, false, false, false, false, false, false}, // 4
            new boolean[]{false, false, false, false, false, true , false, false, false, false, false, false, false, false, false, false}, // 5
            new boolean[]{false, false, false, false, false, false, true , false, false, false, false, false, false, false, false, false}, // 6
            new boolean[]{false, false, false, false, false, false, false, true , false, false, false, false, false, false, false, false}, // 7
            new boolean[]{false, false, false, false, false, false, false, false, true , true , false, false, false, false, false, false}, // 8
            new boolean[]{false, false, false, false, false, false, false, false, true , true , false, false, false, false, false, false}, // 9
            new boolean[]{false, false, false, false, false, false, false, false, false, false, true , true , false, false, false, false}, // 10
            new boolean[]{false, false, false, false, false, false, false, false, false, false, true , true , false, false, false, false}, // 11
            new boolean[]{false, false, false, false, false, false, false, false, false, false, false, false, true , false, false, false}, // 12
            new boolean[]{false, false, false, false, false, false, false, false, false, false, false, false, false, true , false, false}, // 13
            new boolean[]{false, false, false, false, false, false, false, false, false, false, false, false, false, false, true , false}, // 14
            new boolean[]{false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, true }, // 15
        };

        for (int i = 0; i < atoms.length; i++) {
            for (int j = 0; j < atoms.length; j++) {
                if (equalityGrid[i][j]) {
                    assertEquals(atoms[i], atoms[j]);
                } else {
                    assertNotEquals(atoms[i], atoms[j]);
                }
            }
        }
    }

    /**
     * Test atom equality when there are strings with hash collisions.
     */
    @Test
    public void testStringHashCollision() {
        Predicate singleString = StandardPredicate.get("SingleString", new ConstantType[]{ConstantType.String});

        String collision1 = "C-L";
        String collision2 = "BLL";
        String noCollision = "ZZZ";

        assertEquals(collision1.hashCode(), collision2.hashCode());
        assertNotEquals(collision1.hashCode(), noCollision.hashCode());

        Atom atomCollision1 = new QueryAtom(singleString, new StringAttribute(collision1));
        Atom atomCollision2 = new QueryAtom(singleString, new StringAttribute(collision2));
        Atom atomNoCollision = new QueryAtom(singleString, new StringAttribute(noCollision));

        assertNotEquals(atomCollision1, atomCollision2);
        assertNotEquals(atomCollision1, atomNoCollision);
        assertNotEquals(atomCollision2, atomNoCollision);
    }
}
