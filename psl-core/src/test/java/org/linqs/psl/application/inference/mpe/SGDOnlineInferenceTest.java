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
package org.linqs.psl.application.inference.mpe;

import org.linqs.psl.TestModel;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.online.actions.OnlineAction;
import org.linqs.psl.application.inference.online.actions.OnlineActionException;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.reasoner.sgd.term.SGDObjectiveTerm;
import org.linqs.psl.reasoner.term.VariableTermStore;
import org.linqs.psl.reasoner.term.streaming.StreamingTermStore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;

public class SGDOnlineInferenceTest {
    private TestModel.ModelInformation modelInfo;
    private Database inferDB;
    private SGDOnlineInference inference;

    public SGDOnlineInferenceTest() {
        modelInfo = null;
        inferDB = null;
        inference = null;
    }

    /**
     * Initialize a baseline model that we will be modifying with the online inference application
     */
    private void initBaselineModel() {
        Map<String, StandardPredicate> baselinePredicates = new HashMap<String, StandardPredicate>();
        List<Rule> baselineRules = new ArrayList<Rule>();
        Map<StandardPredicate, List<TestModel.PredicateData>> baselineObservations = new HashMap<StandardPredicate, List<TestModel.PredicateData>>();
        Map<StandardPredicate, List<TestModel.PredicateData>> baselineTargets = new HashMap<StandardPredicate, List<TestModel.PredicateData>>();
        Map<StandardPredicate, List<TestModel.PredicateData>> baselineTruths = new HashMap<StandardPredicate, List<TestModel.PredicateData>>();

        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Sim_Users", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});
        predicatesInfo.put("Rating", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            baselinePredicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        // Rating(U1, M) && Sim_Users(U1, U2) => Rating(U2, M)
        baselineRules.add(new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new QueryAtom(baselinePredicates.get("Rating"), new Variable("A"), new Variable("M")),
                                new QueryAtom(baselinePredicates.get("Sim_Users"), new Variable("A"), new Variable("B")),
                                new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new QueryAtom(baselinePredicates.get("Rating"), new Variable("B"), new Variable("M"))
                ),
                1.0,
                true));

        // Data

        // Observed
        // Rating
        baselineObservations.put(baselinePredicates.get("Rating"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(1.0, new Object[]{"Alice", "Avatar"})
        )));

        // Sim_Users
        baselineObservations.put(baselinePredicates.get("Sim_Users"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(1.0, new Object[]{"Alice", "Bob"}),
                new TestModel.PredicateData(1.0, new Object[]{"Bob", "Alice"}),
                new TestModel.PredicateData(1.0, new Object[]{"Eddie", "Alice"}),
                new TestModel.PredicateData(1.0, new Object[]{"Alice", "Eddie"}),
                new TestModel.PredicateData(0.0, new Object[]{"Eddie", "Bob"}),
                new TestModel.PredicateData(0.0, new Object[]{"Bob", "Eddie"})
        )));

        // Targets
        // Rating
        baselineTargets.put(baselinePredicates.get("Rating"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(new Object[]{"Eddie", "Avatar"}),
                new TestModel.PredicateData(new Object[]{"Bob", "Avatar"})
        )));

        // Truths
        baselineTruths.put(baselinePredicates.get("Rating"), new ArrayList<TestModel.PredicateData>(Arrays.asList(
                new TestModel.PredicateData(1.0, new Object[]{"Bob", "Avatar"}),
                new TestModel.PredicateData(1.0, new Object[]{"Eddie", "Avatar"})
        )));

        DatabaseDriver driver = DatabaseTestUtil.getH2Driver();
        modelInfo = TestModel.getModel(driver, baselinePredicates, baselineRules,
                baselineObservations, baselineTargets, baselineTruths);
    }

    @Before
    public void setup() {
        cleanup();

        Options.ONLINE.set(true);

        initBaselineModel();

        // Close the predicates we are using.
        Set<StandardPredicate> toClose = new HashSet<StandardPredicate>();

        inferDB = modelInfo.dataStore.getDatabase(modelInfo.targetPartition, toClose, modelInfo.observationPartition);

        inference = new SGDOnlineInference(modelInfo.model.getRules(), inferDB);
    }

    @After
    public void cleanup() {
        if (inferDB != null) {
            inferDB.close();
            inferDB = null;
        }

        if (modelInfo != null) {
            modelInfo.dataStore.close();
            modelInfo = null;
        }

        if (inference != null) {
            inference.close();
            inference = null;
        }
    }

    private void queueCommands(SGDOnlineInference inference, ArrayList<String> commands) {
        OnlineAction action = null;

        for(String command : commands) {
            try {
                action = OnlineAction.getOnlineAction(command);
            } catch (OnlineActionException ex) {
                throw new RuntimeException(ex);
            }
            inference.addOnlineActionForTesting(action);
        }
    }

    private GroundAtom getAtom(SGDOnlineInference inference, String predicateName, String[] argumentStrings) {
        Constant[] arguments = new Constant[argumentStrings.length];
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = new UniqueStringID(argumentStrings[i]);
        }

        Predicate predicate = Predicate.get(predicateName);
        return inference.getAtomManager().getAtom(predicate, arguments);
    }

    private float getAtomValue(SGDOnlineInference inference, String predicateName, String[] argumentStrings) {
        StreamingTermStore termstore = (StreamingTermStore)inference.getTermStore();
        GroundAtom atom = getAtom(inference, predicateName, argumentStrings);

        return termstore.getVariableValue(termstore.getVariableIndex(atom));
    }

    @Test
    public void testUpdateObservation() {
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "UpdateObservation\tSim_Users\tAlice\tEddie\t0.0",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();

        float atomValue = getAtomValue(inference, "Sim_Users", new String[]{"Alice", "Eddie"});
        assertEquals(atomValue, 0.0, 0.01);
    }

    @Test
    public void testAddAtoms() {
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "AddAtom\tRead\tSim_Users\tConnor\tAlice\t1.0",
                "AddAtom\tRead\tSim_Users\tAlice\tConnor\t1.0",
                "AddAtom\tWrite\tRating\tConnor\tAvatar",
                "WriteInferredPredicates",
                "AddAtom\tWrite\tRating\tConnor\tSurfs Up\t1.0",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();

        float atomValue = getAtomValue(inference, "Rating", new String[]{"Connor", "Avatar"});
        assertEquals(atomValue, 1.0, 0.01);
    }

    @Test
    public void testPageRewriting() {
        Options.STREAMING_TS_PAGE_SIZE.set(2);
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "AddAtom\tRead\tSim_Users\tConnor\tAlice\t0.0",
                "AddAtom\tRead\tSim_Users\tAlice\tConnor\t0.0",
                "AddAtom\tWrite\tRating\tConnor\tAvatar",
                "WriteInferredPredicates",
                "AddAtom\tRead\tSim_Users\tConnor\tBob\t1.0",
                "AddAtom\tRead\tSim_Users\tBob\tConnor\t1.0",
                "AddAtom\tRead\tRating\tBob\tSurfs Up\t0.5",
                "AddAtom\tWrite\tRating\tConnor\tSurfs Up",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();

        float atomValue = getAtomValue(inference, "Rating", new String[]{"Connor", "Avatar"});
        assertEquals(atomValue, 1.0, 0.01);
    }

    @Test
    public void testAtomDeleting() {
        // TODO (Charles): This order of commands will catch a behavior where there may be an unexpected outcome.
        //  The atom will not be deleted if there is an add and then a delete of the same atom before the atoms are
        //  activated. This behavior is also noted in streaming term store deleteAtom.
        /*
        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "DeleteAtom\tRead\tSim_Users\tAlice\tEddie",
                "AddAtom\tRead\tSim_Users\tAlice\tEddie\t1.0",
                "DeleteAtom\tRead\tSim_Users\tAlice\tEddie",
                "WriteInferredPredicates",
                "Close"));
        */

        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "DeleteAtom\tRead\tSim_Users\tAlice\tEddie",
                "DeleteAtom\tRead\tSim_Users\tEddie\tAlice",
                "WriteInferredPredicates",
                "Close"));
        queueCommands(inference, commands);

        @SuppressWarnings("unchecked")
        VariableTermStore<SGDObjectiveTerm, GroundAtom> termStore = (VariableTermStore<SGDObjectiveTerm, GroundAtom>)inference.getTermStore();
        int numTerms = 0;
        for (SGDObjectiveTerm term : termStore) {
            numTerms++;
        }

        assertEquals(2.0, numTerms, 0.01);

        inference.inference();

        numTerms = 0;
        for (SGDObjectiveTerm term: termStore) {
            numTerms++;
        }

        assertEquals(1.0, numTerms, 0.01);
    }

    @Test
    public void testChangeAtomPartition() {
        Options.STREAMING_TS_PAGE_SIZE.set(4);
        setup();

        ArrayList<String> commands = new ArrayList<String>(Arrays.asList(
                "AddAtom\tRead\tSim_Users\tConnor\tAlice\t1.0",
                "AddAtom\tRead\tSim_Users\tAlice\tConnor\t1.0",
                "AddAtom\tWrite\tRating\tConnor\tAvatar",
                "AddAtom\tRead\tSim_Users\tConnor\tBob\t1.0",
                "AddAtom\tRead\tSim_Users\tBob\tConnor\t1.0",
                "AddAtom\tRead\tRating\tBob\tSurfs Up\t0.5",
                "AddAtom\tWrite\tRating\tConnor\tSurfs Up",
                "AddAtom\tRead\tRating\tAlice\tAvatar\t0.5",
                "WriteInferredPredicates",
                "Close"));

        queueCommands(inference, commands);
        inference.inference();
        float atomValue = getAtomValue(inference, "Rating", new String[]{"Alice", "Avatar"});
        assertEquals(atomValue, 0.5, 0.01);
    }
}
