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

package org.linqs.psl.reasoner.bool;

import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.grounding.AtomRegisterGroundRuleStore;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.arithmetic.UnweightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariable;
import org.linqs.psl.model.rule.arithmetic.expression.SummationVariableOrTerm;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.bool.BooleanMaxWalkSat;
import org.linqs.psl.reasoner.function.FunctionComparator;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTermStore;
import org.linqs.psl.reasoner.term.blocker.ConstraintBlockerTermGenerator;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class BooleanMaxWalkSatTest {
    private TestModel.ModelInformation info;

    @Before
    public void setup() {
        Config.init();
        info = TestModel.getModel();

        Config.setProperty(MPEInference.REASONER_KEY, BooleanMaxWalkSat.class.getName());
        Config.setProperty(MPEInference.GROUND_RULE_STORE_KEY, AtomRegisterGroundRuleStore.class.getName());
        Config.setProperty(MPEInference.TERM_STORE_KEY, ConstraintBlockerTermStore.class.getName());
        Config.setProperty(MPEInference.TERM_GENERATOR_KEY, ConstraintBlockerTermGenerator.class.getName());
    }

    @After
    public void clear() {
        Config.init();
    }

    /**
     * A quick test that only checks to see if MPEInference works with BooleanMaxWalkSat.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void baseTest() {
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Make sure that the constraint blocker works with functional constraints.
     */
    @Test
    public void functionalConstraintTest() {
        // Friends(A, +B) = 1.0
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                info.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        info.model.addRule(new UnweightedArithmeticRule(
            new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.EQ, new ConstantNumber(1.0f))
        ));

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }

    /**
     * Make sure that the constraint blocker works with partial functional constraints.
     */
    @Test
    public void partialFunctionalConstraintTest() {
        // Friends(A, +B) <= 1.0
        List<Coefficient> coefficients = Arrays.asList(
            (Coefficient)(new ConstantNumber(1.0f))
        );

        List<SummationAtomOrAtom> atoms = Arrays.asList(
            (SummationAtomOrAtom)(new SummationAtom(
                info.predicates.get("Friends"),
                new SummationVariableOrTerm[]{new Variable("A"), new SummationVariable("B")}
            ))
        );

        info.model.addRule(new UnweightedArithmeticRule(
            new ArithmeticRuleExpression(coefficients, atoms, FunctionComparator.LTE, new ConstantNumber(1.0f))
        ));

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        MPEInference mpe = new MPEInference(info.model, inferDB);

        mpe.inference();
        mpe.close();
        inferDB.close();
    }
}
