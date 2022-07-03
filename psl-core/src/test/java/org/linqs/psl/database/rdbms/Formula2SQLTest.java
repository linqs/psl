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
package org.linqs.psl.database.rdbms;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.mpe.MPEInference;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.ReadableDatabase;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;

public class Formula2SQLTest extends PSLBaseTest {
    @Test
    /**
     * Ensure that ExternalFunctions work with only one argument.
     */
    public void testUnaryExternalFunction() {
        TestModel.ModelInformation info = TestModel.getModel();

        SpyFunction function = new SpyFunction(1);
        Predicate functionPredicate = ExternalFunctionalPredicate.get("UnaryFunction", function);

        // Add a rule using the new function.
        // 10: Person(A) & Person(B) & UnaryFunction(A) & UnaryFunction(B) & (A - B) -> Friends(A, B) ^2
        Formula ruleFormula = new Implication(
            new Conjunction(
                new QueryAtom(info.predicates.get("Person"), new Variable("A")),
                new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                new QueryAtom(functionPredicate, new Variable("A")),
                new QueryAtom(functionPredicate, new Variable("B")),
                new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
            ),
            new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
        );

        Rule rule = new WeightedLogicalRule(ruleFormula, 10.0f, true);
        info.model.addRule(rule);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = new MPEInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();

        // There are 5 people, and the rule chooses 2.
        // So, we expect 20 ground rules.
        // But the DB caches the atoms, so we only expect one call per person.
        // However because of the parallel nature of ground rule instantiation,
        // it is possible for a function to be called before the previous call is
        // put into the cache.
        // Because these methods are supposed to be deterministic, we will not worry about
        // slight over calls.
        assertTrue("Got " + function.getCallCount() + ", expected 5 <= x <= 15", 5 <= function.getCallCount() && function.getCallCount() <= 15);
    }

    @Test
    /**
     * Ensure that ExternalFunctions work with three arguments.
     */
    public void testTernaryExternalFunction() {
        TestModel.ModelInformation info = TestModel.getModel();

        SpyFunction function = new SpyFunction(3);
        Predicate functionPredicate = ExternalFunctionalPredicate.get("TernaryFunction", function);

        // Add a rule using the new function.
        // 10: Person(A) & Person(B) & TernaryFunction(A, B, A) & (A - B) -> Friends(A, B) ^2
        Rule rule = new WeightedLogicalRule(
                new Implication(
                    new Conjunction(
                        new QueryAtom(info.predicates.get("Person"), new Variable("A")),
                        new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                        new QueryAtom(functionPredicate, new Variable("A"), new Variable("B"), new Variable("A")),
                        new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                    ),
                    new QueryAtom(info.predicates.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                10.0f,
                true);
        info.model.addRule(rule);

        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();
        Database inferDB = info.dataStore.getDatabase(info.targetPartition, toClose, info.observationPartition);
        InferenceApplication inference = new MPEInference(info.model.getRules(), inferDB);

        inference.inference();
        inference.close();
        inferDB.close();

        // External functions are only called when instantiating ground rules.
        // So, we should only get one call for each ground rule.
        // However because of the parallel nature of ground rule instantiation,
        // it is possible for a function to be called before the previous call is
        // put into the cache.
        // Because these methods are supposed to be deterministic, we will not worry about
        // slight over calls.
        assertTrue("Got " + function.getCallCount() + ", expected 20 <= x <= 40", 20 <= function.getCallCount() && function.getCallCount() <= 40);
    }

    /**
     * A spy ExternalFunction.
     * Only returns 1, but keeps track of how many times it was called.
     * The number of arguments it accepts is set on construction.
     */
    private class SpyFunction implements ExternalFunction {
        private int callCount;
        private int arity;

        public SpyFunction(int arity) {
            this.arity = arity;
            callCount = 0;
        }

        public int getArity() {
            return arity;
        }

        public ConstantType[] getArgumentTypes() {
            ConstantType[] args = new ConstantType[arity];
            for (int i = 0; i < arity; i++) {
                args[i] = ConstantType.UniqueStringID;
            }

            return args;
        }

        public synchronized double getValue(ReadableDatabase db, Constant... args) {
            callCount++;
            return 1;
        }

        public int getCallCount() {
            return callCount;
        }
    }
}
