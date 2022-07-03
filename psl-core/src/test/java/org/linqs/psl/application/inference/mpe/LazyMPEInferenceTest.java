/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Cardinality;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class LazyMPEInferenceTest extends PSLBaseTest {
    private Database inferDB;
    private Partition targetPartition;
    private Set<StandardPredicate> allPredicates;
    private Set<StandardPredicate> closedPredicates;
    private TestModel.ModelInformation info;

    @Before
    public void setup() {
        initModel(true);
    }

    @After
    public void cleanup() {
        inferDB.close();
        inferDB = null;

        info.dataStore.close();
        info = null;
    }

    private void initModel(boolean useNice) {
        if (inferDB != null) {
            inferDB.close();
            inferDB = null;
        }

        if (info != null) {
            info.dataStore.close();
            info = null;
        }

        info = TestModel.getModel(useNice);

        // Get an empty partition so that no targets will exist in it and we will have to lazily instantiate them all.
        targetPartition = info.dataStore.getPartition(TestModel.PARTITION_UNUSED);

        allPredicates = new HashSet<StandardPredicate>(info.predicates.values());
        closedPredicates = new HashSet<StandardPredicate>(info.predicates.values());
        closedPredicates.remove(info.predicates.get("Friends"));

        inferDB = info.dataStore.getDatabase(targetPartition, closedPredicates, info.observationPartition);
    }

    /**
     * A quick test that only checks to see if LazyMPEInference is running.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void testBase() {
        LazyMPEInference inference = new LazyMPEInference(info.model.getRules(), inferDB);

        // The Friends predicate should be empty.
        assertEquals(0, inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends")));

        inference.inference();

        // There are multiple optimal configuration to the first round of grounding (which snowballs later),
        // but we know there should be at least 16 ground atoms and less than 20.
        int groundCount = inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends"));
        assertEquals(20, groundCount);

        inference.close();
    }

    @Test
    public void testBaseNotNice() {
        initModel(false);

        LazyMPEInference inference = new LazyMPEInference(info.model.getRules(), inferDB);

        // The Friends predicate should be empty.
        assertEquals(0, inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends")));

        inference.inference();

        // There are multiple optimal configuration to the first round of grounding,
        // but we know that at least all 'Eugene's grounding will be excluded.
        int groundCount = inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends"));
        assertEquals(12, groundCount);

        inference.close();
    }

    /**
     * Ensure that simple arithmetic groundings (no summation atoms) works.
     */
    @Test
    public void testSimpleArithmeticBase() {
        // 1.0: Friends(A, B) >= 0.5 ^2
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B")))
        );

        Rule rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(0.5f)),
                1.0f,
                true
        );
        info.model.addRule(rule);

        LazyMPEInference inference = new LazyMPEInference(info.model.getRules(), inferDB);

        // The Friends predicate should be empty.
        assertEquals(0, inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends")));

        inference.inference();

        // There are multiple optimal configuration to the first round of grounding (which snowballs later),
        // but we know there should be at least 16 ground atoms and less than 20.
        int groundCount = inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends"));
        assertEquals(20, groundCount);

        inference.close();
    }

    /**
     * Ensure that complex arithmetic groundings (has summation atoms) works.
     */
    @Test
    public void testComplexArithmeticBase() {
        // |B| * Friends(A, +B) >= 1 {B: Nice(B)}

        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new Cardinality(new SummationVariable("B")))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                info.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        Map<SummationVariable, Formula> filters = new HashMap<SummationVariable, Formula>();
        filters.put(new SummationVariable("B"), new QueryAtom(info.predicates.get("Nice"), new Variable("B")));

        Rule rule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1.0f)),
                filters,
                1.0f,
                true
        );
        info.model.addRule(rule);

        LazyMPEInference inference = new LazyMPEInference(info.model.getRules(), inferDB);

        // The Friends predicate should be empty.
        assertEquals(0, inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends")));

        inference.inference();

        // There are multiple optimal configuration to the first round of grounding (which snowballs later),
        // but we know there should be at least 16 ground atoms and less than 20.
        int groundCount = inferDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends"));
        assertEquals(20, groundCount);

        inference.close();
    }

    /**
     * Make sure lazy inference works even when everything is fully specified.
     */
    @Test
    public void testFullySpecified() {
        Database fullTargetDB = info.dataStore.getDatabase(info.targetPartition, closedPredicates, info.observationPartition);
        LazyMPEInference inference = new LazyMPEInference(info.model.getRules(), fullTargetDB);

        // The Friends predicate should be fully defined.
        assertEquals(20, fullTargetDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends")));

        inference.inference();

        assertEquals(20, fullTargetDB.countAllGroundRandomVariableAtoms(info.predicates.get("Friends")));

        inference.close();
        fullTargetDB.close();
    }
}
