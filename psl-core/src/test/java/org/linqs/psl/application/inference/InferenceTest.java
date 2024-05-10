/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2024 The Regents of the University of California
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
package org.linqs.psl.application.inference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assume.assumeNotNull;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.evaluation.statistics.ContinuousEvaluator;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.Weight;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.reasoner.InitialValue;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public abstract class InferenceTest extends PSLBaseTest {
    public static final int NUM_INFERENCE_RUNS = 10;

    protected TestModel.ModelInformation info;

    protected abstract InferenceApplication getInference(List<Rule> rules, Database db);

    public InferenceTest() {
        info = null;
    }

    @Before
    public void init() {
        DatabaseDriver driver = getDatabaseDriver();
        assumeNotNull(driver);

        info = TestModel.getModel(false, driver);
    }

    @After
    public void cleanup() {
        Options.INFERENCE_INITIAL_VARIABLE_VALUE.clear();

        if (info != null) {
            info.close();
        }
    }

    protected DatabaseDriver getDatabaseDriver() {
        return DatabaseTestUtil.getDatabaseDriver();
    }

    /**
     * A quick test that only checks to see if the inference method is running.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void baseTest() {
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(info.predicates.get("Nice"));
        toClose.add(info.predicates.get("Person"));

        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();
    }

    /**
     * Same as baseTest(), but explicitly using postgres.
     */
    @Test
    public void baseTestPostgres() {
        info.close();

        DatabaseDriver driver = DatabaseTestUtil.getPostgresDriver();
        assumeNotNull(driver);

        info = TestModel.getModel(false, driver);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();
    }

    /**
     * Make sure we do not crash on a logical rule with no open predicates.
     */
    @Test
    public void testLogicalNoOpenPredicates() {
        // Reset the model with only a single rule.
        info.model = new Model();

        // Nice(A) & Nice(B) -> Friends(A, B)
        info.model.addRule(new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(info.predicates.get("Nice"), new Variable("B"))
                ),
                new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            new Weight(1.0f),
            true
        ));

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(info.predicates.get("Nice"));
        toClose.add(info.predicates.get("Friends"));

        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();
    }

    /**
     * Make sure we do not crash on an arithmetic rule with no open predicates.
     */
    @Test
    public void testArithmeticNoOpenPredicates() {
        List<Coefficient> coefficients;
        List<SummationAtomOrAtom> atoms;

        coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f)),
            (Coefficient)(new ConstantNumber(1.0f))
        );

        atoms = Arrays.asList(
            (SummationAtomOrAtom)(new QueryAtom(info.predicates.get("Nice"), new Variable("A"))),
            (SummationAtomOrAtom)(new QueryAtom(info.predicates.get("Nice"), new Variable("B")))
        );

        // Nice(A) + Nice(B) >= 1.0
        info.model.addRule(new WeightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.GTE, new ConstantNumber(1)),
                new Weight(1.0f),
                true
        ));

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(info.predicates.get("Nice"));

        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();
    }

    /**
     * Make sure that we remove terms that cause a tautology from logical rules.
     */
    @Test
    public void testLogicalTautologyTrivial() {
        // Clear out the model.
        info.model.clear();

        // Friends(A, B) -> Friends(A, B)
        info.model.addRule(new WeightedLogicalRule(
            new Implication(
                new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B")),
                new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            new Weight(1.0f),
            true
        ));

        final List<GroundRule> groundRules = new ArrayList<GroundRule>();
        Grounding.setGroundRuleCallback(new Grounding.GroundRuleCallback() {
            public synchronized void call(GroundRule groundRule) {
                groundRules.add(groundRule);
            }
        });

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);
        Grounding.setGroundRuleCallback(null);

        inference.inference();

        // There are 20 ground rules, and they are all trivial.
        assertEquals(20, groundRules.size());
        assertEquals(0, inference.getTermStore().size());

        inference.close();
        inferDB.close();
    }

    /**
    * Make sure that commitAtom flag works and all atoms are persisted in the database.
    */
    @Test
    public void testCommitAtom() {
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        float preInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAtomStore().getRandomVariableAtoms(info.predicates.get("Friends"))) {
            preInferenceTotalValue += atom.getValue();
        }

        inference.inference(true, true);

        inference.close();

        // The database should be closed after inference to clear the cache before reading in values again.
        // This ensures that the values returned won't be from the cache.
        inferDB.close();

        // Create a new instance of a database with an empty cache.
        inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        float postInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAtomStore().getRandomVariableAtoms(info.predicates.get("Friends"))) {
            postInferenceTotalValue += atom.getValue();
        }

        assertNotEquals(preInferenceTotalValue, postInferenceTotalValue, 0.001f);

        inferDB.close();
    }

    /**
    * Make sure that setting the commitAtom flag to false works and no atom is persisted in the database.
    */
    @Test
    public void testNoCommitAtom() {
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);

        float preInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAtomStore().getRandomVariableAtoms(info.predicates.get("Friends"))) {
            preInferenceTotalValue += atom.getValue();
        }

        InferenceApplication inference = getInference(info.model.getRules(), inferDB);
        inference.inference(false, true);
        inference.close();

        // The database should be closed after inference to clear the cache before reading in values again.
        // This ensures that the values returned won't be from the cache.
        inferDB.close();

        // Create a new instance of a database with an empty cache.
        inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        float postInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAtomStore().getRandomVariableAtoms(info.predicates.get("Friends"))) {
            postInferenceTotalValue += atom.getValue();
        }

        assertEquals(preInferenceTotalValue, postInferenceTotalValue, 0.001f);

        inferDB.close();
    }

    /**
     * Run inference multiple time for different initial values,
     * and expect them all to get the same answer.
     */
    @Test
    public void initialValueTest() {
        double oldAvgObjective = 0.0f;
        InitialValue oldInitialValue = null;

        for (InitialValue initialValue : InitialValue.values()) {
            Options.INFERENCE_INITIAL_VARIABLE_VALUE.set(initialValue.toString());

            Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();

            double avgObjective = 0.0;
            for (int i = 0; i < NUM_INFERENCE_RUNS; i++) {
                Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
                InferenceApplication inference = getInference(info.model.getRules(), inferDB);

                avgObjective += inference.inference() / NUM_INFERENCE_RUNS;

                inference.close();
                inferDB.close();
            }

            if (oldInitialValue != null) {
                assertEquals(
                        String.format("Found differing values for two initial values: %s (%f) vs %s (%f).",
                        oldInitialValue, oldAvgObjective, initialValue, avgObjective),
                        avgObjective, oldAvgObjective, 0.05);
            }

            oldAvgObjective = avgObjective;
            oldInitialValue = initialValue;
        }
    }

    /**
     * Test that inference applications find a nearly feasible solution with a simplex constraint.
     */
    @Test
    public void testSimplexConstraints() {
        List<Coefficient> coefficients = Arrays.asList((Coefficient)(new ConstantNumber(1.0f)));
        List<SummationAtomOrAtom> atoms =  Arrays.asList(
                (SummationAtomOrAtom)(new SummationAtom(info.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new SummationVariable("A"), new SummationVariable("B")}))
        );

        // Add rule: Friends(+A, +B) = 1.0
        info.model.addRule(new UnweightedArithmeticRule(
                new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1))
        ));

        // Create inference application.
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, new HashSet<StandardPredicate>(), info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        // Test the constraint is enforced by the inference application.
        inference.inference();

        float sum = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAtomStore().getRandomVariableAtoms()) {
            sum += atom.getValue();
        }

        assertEquals(1.0f, sum, 0.1f);

        inference.close();
        inferDB.close();
    }

    /**
     * Run inference on simple models with expected MAP states.
     */
    @Test
    public void testSimpleModels() {
        // Negative prior model.
        TestModel.ModelInformation info = TestModel.getNegativePriorModel();

        // Create inference application.
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, new HashSet<StandardPredicate>(), info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        // Test the inference application is able to find the MAP state.
        assertEquals(0.0, inference.inference(), 0.1f);

        inference.close();
        inferDB.close();

        // Prior model.
        info = TestModel.getPriorModel();
        inferDB = info.dataStore.getDatabase(info.targetPartition, new HashSet<StandardPredicate>(), info.observationPartition);
        inference = getInference(info.model.getRules(), inferDB);

        // Test the inference application is able to find the MAP state.
        assertEquals(0.0, inference.inference(), 0.1f);

        inference.close();
        inferDB.close();

        // Symmetry model.
        info = TestModel.getSymmetryModel();
        inferDB = info.dataStore.getDatabase(info.targetPartition, new HashSet<StandardPredicate>(), info.observationPartition);
        inference = getInference(info.model.getRules(), inferDB);

        // Test the inference application is able to find the MAP state.
        assertEquals(0.0, inference.inference(), 0.1f);

        inference.close();
        inferDB.close();

        // Exogenous model.
        info = TestModel.getExogenousModel();
        inferDB = info.dataStore.getDatabase(info.targetPartition, new HashSet<StandardPredicate>(), info.observationPartition);
        inference = getInference(info.model.getRules(), inferDB);

        // Test the inference application is able to find the MAP state.
        assertEquals(0.0, inference.inference(), 0.1f);

        inference.close();
        inferDB.close();
    }

    /**
     * Test that inference using evaluation within optimization runs.
     */
    @Test
    public void reasonerEvaluateTest() {
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        Database truthDatabase = info.dataStore.getDatabase(info.truthPartition, info.dataStore.getRegisteredPredicates());

        List<EvaluationInstance> evaluations = new ArrayList<EvaluationInstance>();
        evaluations.add(new EvaluationInstance(info.predicates.get("Friends"), new ContinuousEvaluator(), true));

        inference.inference(true, false, evaluations, truthDatabase);
        inference.close();
        inferDB.close();
        truthDatabase.close();
    }

    @Test
    public void testAtomWithConstant() {
        // Nice(A) & Nice(B) & Friends('Alice', B) && (A != B) -> Friends(A, B)
        info.model.addRule(new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Nice"), new Variable("A")),
                    new QueryAtom(info.predicates.get("Nice"), new Variable("B")),
                    new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("Alice"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                ),
                new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            new Weight(1.0f),
            true
        ));

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = getInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();
    }
}
