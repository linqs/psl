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
package org.linqs.psl.application.inference.online;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.OnlineTest;
import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.online.messages.OnlineMessage;
import org.linqs.psl.application.inference.online.messages.actions.controls.Exit;
import org.linqs.psl.application.inference.online.messages.actions.controls.Stop;
import org.linqs.psl.application.inference.online.messages.actions.controls.Sync;
import org.linqs.psl.application.inference.online.messages.actions.model.AddAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.DeleteAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.GetAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.ObserveAtom;
import org.linqs.psl.application.inference.online.messages.actions.model.UpdateObservation;
import org.linqs.psl.application.inference.online.messages.actions.template.ActivateRule;
import org.linqs.psl.application.inference.online.messages.actions.template.AddRule;
import org.linqs.psl.application.inference.online.messages.actions.template.DeactivateRule;
import org.linqs.psl.application.inference.online.messages.actions.template.DeleteRule;
import org.linqs.psl.application.inference.online.messages.responses.ActionStatus;
import org.linqs.psl.application.inference.online.messages.responses.OnlineResponse;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.Coefficient;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.util.Arrays;
import java.util.HashSet;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class SGDOnlineInferenceTest {
    private TestModel.ModelInformation modelInfo;
    private Database inferDB;
    private OnlineInferenceThread onlineInferenceThread;

    public SGDOnlineInferenceTest() {
        modelInfo = null;
        inferDB = null;
        onlineInferenceThread = null;
    }

    @Before
    public void setup() {
        Options.SGD_LEARNING_RATE.set(10.0);
        Options.SGD_INVERSE_TIME_EXP.set(0.5);

        modelInfo = TestModel.getModel(true);

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition,
                new HashSet<StandardPredicate>(), modelInfo.observationPartition);

        // Start up inference on separate thread.
        onlineInferenceThread = new OnlineInferenceThread();
        onlineInferenceThread.start();
    }

    @After
    public void cleanup() {
        stop();

        if (inferDB != null) {
            inferDB.close();
            inferDB = null;
        }

        if (modelInfo != null) {
            modelInfo.dataStore.close();
            modelInfo = null;
        }

        Options.SGD_LEARNING_RATE.clear();
        Options.SGD_INVERSE_TIME_EXP.clear();
    }

    protected void stop() {
        if (onlineInferenceThread == null) {
            return;
        }

        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        Stop stop = new Stop();
        commands.add(stop);

        OnlineResponse[] expectedResponses = new OnlineResponse[1];
        expectedResponses[0] = new ActionStatus(stop, true, "OnlinePSL inference stopped.");

        OnlineTest.assertServerResponse(commands, expectedResponses);

        try {
            onlineInferenceThread.join();
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }

        onlineInferenceThread = null;
    }

    /**
     * A test that to see if the inference method is running, accepting client connections, and stopping.
     */
    @Test
    public void baseTest() {
        stop();
    }

    /**
     * Make sure that new atoms are added to model, are considered during inference, and
     * result in the expected groundings.
     */
    @Test
    public void testAddAtoms() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        // Check that adding atoms will not create new random variable atoms.
        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {-1.0, -1.0, -1.0, -1.0});

        // Reset model.
        cleanup();
        setup();

        // Check that atoms added to the model have the expected values at the MAP state.
        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}, 0.0f));
        commands.add(new GetAtom(StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0});
    }

    @Test
    public void testAtomDeleting() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        commands.add(new DeleteAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}, 1.0f));
        commands.add(new DeleteAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new GetAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new Exit());

        double[] values = {-1.0};

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        commands.add(new DeleteAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new DeleteAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new GetAtom(StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new GetAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new Exit());

        values = new double[]{-1.0, -1.0};

        OnlineTest.assertAtomValues(commands, values);
    }

    /**
     * Test that an update to an existing observed atom will change its value to the provided number.
     */
    @Test
    public void testUpdateObservation() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        commands.add(new UpdateObservation(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}, 0.0f));
        commands.add(new GetAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Alice")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[]{0.0});
    }

    /**
     * Test three ways to change the partition of an atom.
     * 1. Add an atom with predicates and arguments that already exists in the model but with a different partition.
     * 2. Delete and then Add an atom.
     * 3. Using the Observe action for random variables and observations respectively. (preferred).
     * */
    @Test
    public void testChangeAtomPartition() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();
        double[] values = {0.5};

        // Add existing atom with different partition.
        commands.add(new AddAtom("Read", StandardPredicate.get("Friends"),
                new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        // Delete and then Add an atom.
        commands.add(new DeleteAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new AddAtom("Read", StandardPredicate.get("Friends"),
                new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, values);

        // Reset model.
        cleanup();
        setup();

        // Observe atom.
        commands.add(new ObserveAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}, 0.5f));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, values);
    }

    /**
     * Test expected response received from AddRule action and the added rule has the expected effect on MAP state.
     */
    @Test
    public void testRuleAddition() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();
        Rule newRule = new WeightedArithmeticRule(
                new ArithmeticRuleExpression(
                        Arrays.asList((Coefficient) (new ConstantNumber(1))),
                        Arrays.asList(new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))),
                        FunctionComparator.EQ, new ConstantNumber(1)
                ),
                1000.0f, false);

        // Delete rule to simulate adding an unregistered rule on the server.
        newRule.unregister();

        AddRule addRule = new AddRule(newRule);
        Exit exit = new Exit();
        commands.add(addRule);
        commands.add(exit);

        // Test expected response.
        OnlineResponse[] expectedResponses = new OnlineResponse[2];
        expectedResponses[0] = new ActionStatus(addRule, true,
                String.format("Added rule: %s", addRule.getRule().toString()));
        expectedResponses[1] = new ActionStatus(exit, true, "Session Closed.");

        OnlineTest.assertServerResponse(commands, expectedResponses);

        // Test expected effect on MAP state.
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {1.0});
    }

    @Test
    public void testDuplicateRuleAddition() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();
        Rule newRule = modelInfo.model.getRules().get(0);

        AddRule addRule = new AddRule(newRule);
        Exit exit = new Exit();
        commands.add(addRule);
        commands.add(exit);

        OnlineResponse[] expectedResponses = new OnlineResponse[2];
        expectedResponses[0] = new ActionStatus(addRule, true,
                String.format("Rule: %s already exists in model.", addRule.getRule()));
        expectedResponses[1] = new ActionStatus(exit, true,"Session Closed.");

        OnlineTest.assertServerResponse(commands, expectedResponses);
    }

    @Test
    public void testRuleDeletion() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        Rule niceRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("A")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                0.5f, true);

        Rule friendsRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("A")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("B"), new Variable("A"))
                ),
                10.0f, true);

        Rule negativePriorRule = new WeightedLogicalRule(
                new Negation(
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                1.0f, true);

        commands.add(new DeleteRule(negativePriorRule));
        commands.add(new DeleteRule(friendsRule));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {1.0});

        // Reset model.
        cleanup();
        setup();

        commands.add(new DeleteRule(niceRule));
        commands.add(new DeleteRule(friendsRule));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {0.0});
    }

    /**
     * Check that rules with no initial groundings are still deleted.
     */
    @Test
    public void testZeroGroundingRuleDeletion() {
        // Add rule that will have no groundings.
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();
        Rule newRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("A")),
                                new Negation(new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("A"))),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                100.0f, true);

        // Delete rule to simulate adding an unregistered rule on the server.
        newRule.unregister();

        AddRule addRule = new AddRule(newRule);
        Exit exit = new Exit();
        commands.add(addRule);
        commands.add(exit);

        // Test expected response.
        OnlineResponse[] expectedResponses = new OnlineResponse[2];
        expectedResponses[0] = new ActionStatus(addRule, true,
                String.format("Added rule: %s", addRule.getRule().toString()));
        expectedResponses[1] = new ActionStatus(exit, true, "Session Closed.");

        OnlineTest.assertServerResponse(commands, expectedResponses);

        // Delete zero grounding rule.
        DeleteRule deleteRule = new DeleteRule(newRule);
        commands.add(deleteRule);
        commands.add(exit);

        // Test expected response.
        expectedResponses = new OnlineResponse[2];
        expectedResponses[0] = new ActionStatus(deleteRule, true,
                String.format("Deleted rule: %s", deleteRule.getRule().toString()));
        expectedResponses[1] = new ActionStatus(exit, true, "Session Closed.");

        OnlineTest.assertServerResponse(commands, expectedResponses);

        // Check that atoms added to the model are not influenced by new rule.
        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}, 0.0f));
        commands.add(new GetAtom(StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Bob"), new UniqueStringID("Connor")}));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {1.0, 0.0, 0.0, 0.0, 0.0, 0.0});
    }

    /**
     * Test ActivateRule and DeactivateRule actions activate and deactivate term pages
     * and result in the expected MAP state.
     */
    @Test
    public void testRuleActivation() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        Rule niceRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("A")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                5.0f, true);

        Rule friendsRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("A")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("B"), new Variable("A"))
                ),
                10.0f, true);

        Rule negativePriorRule = new WeightedLogicalRule(
                new Negation(
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                1.0f, true);

        commands.add(new DeactivateRule(niceRule));
        commands.add(new DeactivateRule(friendsRule));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {0.0});

        commands.add(new DeactivateRule(negativePriorRule));
        commands.add(new ActivateRule(niceRule));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Bob")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {1.0});
    }

    /**
     * Test deactivated rules are still partially grounded.
     */
    @Test
    public void testRuleDeactivatedGrounding() {
        BlockingQueue<OnlineMessage> commands = new LinkedBlockingQueue<OnlineMessage>();

        Rule niceRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("A")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Nice"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                5.0f, true);

        Rule friendsRule = new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("A")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Person"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B")),
                                new org.linqs.psl.model.atom.QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("B"), new Variable("A"))
                ),
                10.0f, true);

        Rule negativePriorRule = new WeightedLogicalRule(
                new Negation(
                        new org.linqs.psl.model.atom.QueryAtom(StandardPredicate.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                1.0f, true);

        // Deactivate negative prior rule.
        commands.add(new DeactivateRule(negativePriorRule));
        commands.add(new Sync());
        commands.add(new Exit());

        OnlineTest.clientSession(commands);

        // Add entity "Connor" to model with targets.
        commands.add(new AddAtom("Read", StandardPredicate.get("Person"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Read", StandardPredicate.get("Nice"), new Constant[]{new UniqueStringID("Connor")}, 1.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}, 0.0f));
        commands.add(new AddAtom("Write", StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Connor"), new UniqueStringID("Alice")}, 0.0f));
        commands.add(new Sync());
        commands.add(new Exit());

        OnlineTest.clientSession(commands);

        // Activate negative prior rule, and deactivate other rules.
        commands.add(new DeactivateRule(niceRule));
        commands.add(new DeactivateRule(friendsRule));
        commands.add(new ActivateRule(negativePriorRule));
        commands.add(new GetAtom(StandardPredicate.get("Friends"), new Constant[]{new UniqueStringID("Alice"), new UniqueStringID("Connor")}));
        commands.add(new Exit());

        OnlineTest.assertAtomValues(commands, new double[] {0.0});
    }

    private class OnlineInferenceThread extends Thread {
        SGDOnlineInference onlineInference;

        public OnlineInferenceThread() {
            onlineInference = new SGDOnlineInference(modelInfo.model.getRules(), inferDB);
        }

        @Override
        public void run() {
            onlineInference.inference(false, false);
        }

        public void close() {
            if (onlineInference != null) {
                onlineInference.close();
                onlineInference = null;
            }
        }
    }
}
