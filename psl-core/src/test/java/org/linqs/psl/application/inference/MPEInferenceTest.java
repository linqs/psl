/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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
import static org.junit.Assert.fail;

import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MPEInferenceTest {
    /**
     * A quick test that only checks to see if MPEInference is running.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void baseTest() {
        TestModel.ModelInformation info = TestModel.getModel();

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Same as testBase(), but using postgres.
     */
    @Test
    public void testBasePostgres() {
        DatabaseDriver driver = DatabaseTestUtil.getPostgresDriver();
        if (driver == null) {
            return;
        }

        TestModel.ModelInformation info = TestModel.getModel(false, driver);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Make sure we do not crash on a logical rule with no open predicates.
     */
    @Test
    public void testLogicalNoOpenPredicates() {
        TestModel.ModelInformation info = TestModel.getModel();

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
            1.0,
            true
        ));

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(info.predicates.get("Nice"));
        toClose.add(info.predicates.get("Friends"));

        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Make sure we do not crash on an arithmetic rule with no open predicates.
     */
    @Test
    public void testArithmeticNoOpenPredicates() {
        TestModel.ModelInformation info = TestModel.getModel();

        // Reset the model with only a single rule.
        // info.model = new Model();

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
                1.0,
                true
        ));

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        toClose.add(info.predicates.get("Nice"));

        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Make sure that we remove terms that cause a tautology from logical rules.
     */
    @Test
    public void testLogicalTautologyTrivial() {
        TestModel.ModelInformation info = TestModel.getModel();

        // Clear out the model.
        info.model.clear();

        // Friends(A, B) -> Friends(A, B)
        info.model.addRule(new WeightedLogicalRule(
            new Implication(
                new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B")),
                new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
            ),
            1.0,
            true
        ));

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();

        // There are 20 ground rules, and they are all trivial.
        assertEquals(20, mpe.getGroundRuleStore().size());
        assertEquals(0, mpe.getTermStore().size());

        mpe.close();
        inferDB.close();
    }

    /**
    * Make sure that commitAtom flag works and all atoms are persisted in the database.
    */
    @Test
    public void testCommitAtom() {
        TestModel.ModelInformation info = TestModel.getModel();
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication mpe = new MPEInference(info.model, inferDB);

        float preInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAllGroundRandomVariableAtoms(info.predicates.get("Friends"))) {
            preInferenceTotalValue += atom.getValue();
        }

        mpe.inference(true);

        mpe.close();

        // The database should be closed after inference to clear the cache before reading in values again.
        // This ensures that the values returned won't be from the cache.
        inferDB.close();

        // Create a new instance of a database with an empty cache.
        inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        float postInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAllGroundRandomVariableAtoms(info.predicates.get("Friends"))) {
            postInferenceTotalValue += atom.getValue();
        }

        assertNotEquals(preInferenceTotalValue, postInferenceTotalValue, 0.001f);
    }

    /**
    * Make sure that setting the commitAtom flag to false works and no atom is persisted in the database.
    */
    @Test
    public void testNoCommitAtom() {
        TestModel.ModelInformation info = TestModel.getModel();
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication mpe = new MPEInference(info.model, inferDB);

        float preInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAllGroundRandomVariableAtoms(info.predicates.get("Friends"))) {
            preInferenceTotalValue += atom.getValue();
        }

        mpe.inference(false);

        mpe.close();

        // The database should be closed after inference to clear the cache before reading in values again.
        // This ensures that the values returned won't be from the cache.
        inferDB.close();

        // Create a new instance of a database with an empty cache.
        inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        float postInferenceTotalValue = 0.0f;
        for (RandomVariableAtom atom : inferDB.getAllGroundRandomVariableAtoms(info.predicates.get("Friends"))) {
            postInferenceTotalValue += atom.getValue();
        }

        assertEquals(preInferenceTotalValue, postInferenceTotalValue, 0.001f);
    }
}
