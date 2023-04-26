/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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
package org.linqs.psl.test;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.DatabaseTestUtil;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.arithmetic.WeightedArithmeticRule;
import org.linqs.psl.model.rule.arithmetic.expression.ArithmeticRuleExpression;
import org.linqs.psl.model.rule.arithmetic.expression.SummationAtomOrAtom;
import org.linqs.psl.model.rule.arithmetic.expression.coefficient.ConstantNumber;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.function.FunctionComparator;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An easy interface for loading models to test.
 * This is not for testing the model itself, but for integration tests where models are required.
 */
public class TestModel {
    public static final String PARTITION_OBSERVATIONS = "observations";
    public static final String PARTITION_TARGETS = "targets";
    public static final String PARTITION_VALIDATION = "validation";
    public static final String PARTITION_TRUTH = "truth";
    // This class promises not to use this partition, so tests can guarantee it will be empty.
    public static final String PARTITION_UNUSED = "unused";

    // Give each model a unique identifier.
    private static int modelId = 0;

    // Static only.
    private TestModel() {
    }

    /**
     * Get a default model.
     * The caller owns everything that is returned and should make sure to close the datastore.
     * Predicates:
     *     Nice(UniqueStringID)
     *     Person(UniqueStringID)
     *     Friends(UniqueStringID, UniqueStringID)
     *
     * Rules:
     *     5: Nice(A) & Nice(B) & (A - B) -> Friends(A, B) ^2
     *     10: Person(A) & Person(B) & Friends(A, B) & (A - B) -> Friends(B, A) ^2
     *     1: ~Friends(A, B) ^2
     *
     * Data:
     *     - There are 5 people.
     *     - Every person has a Nice value. Alice starts at 0.8 then is decreases by 0.2 alphabetically (Eugue is 0.0).
     *     - All Friendships are in the target partition.
     *     - All Friendships have a binary truth value in the truth partition.
     *
     * Data is added as well and can be seen in the code.
     */
    public static ModelInformation getModel() {
        return getModel(false);
    }

    /**
     * Same as getModel(), but if specified all people have a nice truth value of 1.0.
     */
    public static ModelInformation getModel(boolean nicePeople) {
        return getModel(nicePeople, DatabaseTestUtil.getDatabaseDriver());
    }

    public static ModelInformation getModel(boolean nicePeople, DatabaseDriver driver) {
        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Nice", new ConstantType[]{ConstantType.UniqueStringID});
        predicatesInfo.put("Person", new ConstantType[]{ConstantType.UniqueStringID});
        predicatesInfo.put("Friends", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        Map<String, StandardPredicate> predicates = new HashMap<String, StandardPredicate>();
        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            predicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        // Different variable names will be used between rules to make grounding optimization harder.

        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new WeightedLogicalRule(
                new Implication(
                    new Conjunction(
                        new QueryAtom(predicates.get("Nice"), new Variable("A")),
                        new QueryAtom(predicates.get("Nice"), new Variable("B")),
                        new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                    ),
                    new QueryAtom(predicates.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                5.0f,
                true));

        rules.add(new WeightedLogicalRule(
                new Implication(
                    new Conjunction(
                        new QueryAtom(predicates.get("Person"), new Variable("X")),
                        new QueryAtom(predicates.get("Person"), new Variable("Y")),
                        new QueryAtom(predicates.get("Friends"), new Variable("X"), new Variable("Y")),
                        new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("X"), new Variable("Y"))
                    ),
                    new QueryAtom(predicates.get("Friends"), new Variable("Y"), new Variable("X"))
                ),
                10.0f,
                true));

        rules.add(new WeightedLogicalRule(
                new Negation(
                    new QueryAtom(predicates.get("Friends"), new Variable("I"), new Variable("J"))
                ),
                1.0f,
                true));

        // Data
        Map<StandardPredicate, List<PredicateData>> observations = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> targets = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> validation = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> truths = new HashMap<StandardPredicate, List<PredicateData>>();

        // Person
        observations.put(predicates.get("Person"), new ArrayList<PredicateData>(Arrays.asList(
            new PredicateData(new Object[]{"Alice"}),
            new PredicateData(new Object[]{"Bob"}),
            new PredicateData(new Object[]{"Charlie"}),
            new PredicateData(new Object[]{"Derek"}),
            new PredicateData(new Object[]{"Eugene"})
        )));

        // Nice
        if (nicePeople) {
            observations.put(predicates.get("Nice"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(1.0, new Object[]{"Alice"}),
                new PredicateData(1.0, new Object[]{"Bob"}),
                new PredicateData(1.0, new Object[]{"Charlie"}),
                new PredicateData(1.0, new Object[]{"Derek"}),
                new PredicateData(1.0, new Object[]{"Eugene"})
            )));
        } else {
            observations.put(predicates.get("Nice"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(0.9, new Object[]{"Alice"}),
                new PredicateData(0.8, new Object[]{"Bob"}),
                new PredicateData(0.7, new Object[]{"Charlie"}),
                new PredicateData(0.6, new Object[]{"Derek"}),
                new PredicateData(0.0, new Object[]{"Eugene"})
            )));
        }

        // Friends
        targets.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
            new PredicateData(new Object[]{"Alice", "Bob"}),
            new PredicateData(new Object[]{"Bob", "Alice"}),
            new PredicateData(new Object[]{"Alice", "Charlie"}),
            new PredicateData(new Object[]{"Charlie", "Alice"}),
            new PredicateData(new Object[]{"Alice", "Derek"}),
            new PredicateData(new Object[]{"Derek", "Alice"}),
            new PredicateData(new Object[]{"Alice", "Eugene"}),
            new PredicateData(new Object[]{"Eugene", "Alice"}),
            new PredicateData(new Object[]{"Bob", "Charlie"}),
            new PredicateData(new Object[]{"Charlie", "Bob"}),
            new PredicateData(new Object[]{"Bob", "Derek"}),
            new PredicateData(new Object[]{"Derek", "Bob"}),
            new PredicateData(new Object[]{"Bob", "Eugene"}),
            new PredicateData(new Object[]{"Eugene", "Bob"}),
            new PredicateData(new Object[]{"Charlie", "Derek"}),
            new PredicateData(new Object[]{"Derek", "Charlie"}),
            new PredicateData(new Object[]{"Charlie", "Eugene"}),
            new PredicateData(new Object[]{"Eugene", "Charlie"}),
            new PredicateData(new Object[]{"Derek", "Eugene"}),
            new PredicateData(new Object[]{"Eugene", "Derek"})
        )));

        truths.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
            new PredicateData(1, new Object[]{"Alice", "Bob"}),
            new PredicateData(1, new Object[]{"Bob", "Alice"}),
            new PredicateData(1, new Object[]{"Alice", "Charlie"}),
            new PredicateData(1, new Object[]{"Charlie", "Alice"}),
            new PredicateData(1, new Object[]{"Alice", "Derek"}),
            new PredicateData(1, new Object[]{"Derek", "Alice"}),
            new PredicateData(1, new Object[]{"Alice", "Eugene"}),
            new PredicateData(1, new Object[]{"Eugene", "Alice"}),
            new PredicateData(1, new Object[]{"Bob", "Charlie"}),
            new PredicateData(1, new Object[]{"Charlie", "Bob"}),
            new PredicateData(1, new Object[]{"Bob", "Derek"}),
            new PredicateData(1, new Object[]{"Derek", "Bob"}),
            new PredicateData(0, new Object[]{"Bob", "Eugene"}),
            new PredicateData(0, new Object[]{"Eugene", "Bob"}),
            new PredicateData(1, new Object[]{"Charlie", "Derek"}),
            new PredicateData(1, new Object[]{"Derek", "Charlie"}),
            new PredicateData(0, new Object[]{"Charlie", "Eugene"}),
            new PredicateData(0, new Object[]{"Eugene", "Charlie"}),
            new PredicateData(0, new Object[]{"Derek", "Eugene"}),
            new PredicateData(0, new Object[]{"Eugene", "Derek"})
        )));

        return getModel(driver, predicates, rules, observations, targets, validation, truths);
    }

    /**
     * A generalized version of getModel().
     * Because of the complexity of defining each part by hand, it usually suggested to use the simpler getModel() and work
     * with the given model.
     * Any of the data maps can be null or empty to represent to data present.
     */
    public static ModelInformation getModel(
            DatabaseDriver driver,
            Map<String, StandardPredicate> predicates, List<Rule> rules,
            Map<StandardPredicate, List<PredicateData>> observations,
            Map<StandardPredicate, List<PredicateData>> targets,
            Map<StandardPredicate, List<PredicateData>> validation,
            Map<StandardPredicate, List<PredicateData>> truths) {
        DataStore dataStore = new RDBMSDataStore(driver);

        Model model = new Model();

        // Predicates
        for (StandardPredicate predicate : predicates.values()) {
            dataStore.registerPredicate(predicate);
        }

        // Rules
        for (Rule rule : rules) {
            model.addRule(rule);
        }

        // Load Data

        // Partitions
        Partition obsPartition = dataStore.getPartition(PARTITION_OBSERVATIONS);
        Partition targetPartition = dataStore.getPartition(PARTITION_TARGETS);
        Partition validationPartition = dataStore.getPartition(PARTITION_VALIDATION);
        Partition truthPartition = dataStore.getPartition(PARTITION_TRUTH);

        Map<Partition, Map<StandardPredicate, List<PredicateData>>> allData = new HashMap<Partition, Map<StandardPredicate, List<PredicateData>>>();
        if (observations != null && observations.size() != 0) {
            allData.put(obsPartition, observations);
        }

        if (targets != null && targets.size() != 0) {
            allData.put(targetPartition, targets);
        }

        if (validation != null && validation.size() != 0) {
            allData.put(validationPartition, validation);
        }

        if (truths != null && truths.size() != 0) {
            allData.put(truthPartition, truths);
        }

        for (Map.Entry<Partition, Map<StandardPredicate, List<PredicateData>>> partition : allData.entrySet()) {
            for (Map.Entry<StandardPredicate, List<PredicateData>> predicateData : partition.getValue().entrySet()) {
                Inserter inserter = dataStore.getInserter(predicateData.getKey(), partition.getKey());
                for (PredicateData dataInstance : predicateData.getValue()) {
                    inserter.insertValue(dataInstance.truthValue, dataInstance.args);
                }
            }
        }

        return new ModelInformation(modelId++, dataStore, model, predicates, obsPartition, targetPartition, validationPartition, truthPartition);
    }

    /**
     * A model with only a single exogenous rule.
     *     5: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) ^2
     */
    public static ModelInformation getExogenousModel() {
        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Nice", new ConstantType[]{ConstantType.UniqueStringID});
        predicatesInfo.put("Friends", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        Map<String, StandardPredicate> predicates = new HashMap<String, StandardPredicate>();
        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            predicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new QueryAtom(predicates.get("Nice"), new Variable("A")),
                                new QueryAtom(predicates.get("Nice"), new Variable("B")),
                                new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new QueryAtom(predicates.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                5.0f,
                true));

        // Data
        Map<StandardPredicate, List<PredicateData>> observations = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> targets = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> validation = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> truths = new HashMap<StandardPredicate, List<PredicateData>>();

        // Nice
        observations.put(predicates.get("Nice"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(1.0, new Object[]{"Alice"}),
                new PredicateData(1.0, new Object[]{"Bob"}),
                new PredicateData(1.0, new Object[]{"Charlie"}),
                new PredicateData(1.0, new Object[]{"Derek"}),
                new PredicateData(1.0, new Object[]{"Eugene"})
        )));

        // Friends
        targets.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(new Object[]{"Alice", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Alice"}),
                new PredicateData(new Object[]{"Bob", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Bob"}),
                new PredicateData(new Object[]{"Charlie", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Charlie"}),
                new PredicateData(new Object[]{"Derek", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Derek"})
        )));

        truths.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(1, new Object[]{"Alice", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Eugene"}),
                new PredicateData(1, new Object[]{"Eugene", "Alice"}),
                new PredicateData(1, new Object[]{"Bob", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Bob"}),
                new PredicateData(0, new Object[]{"Bob", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Bob"}),
                new PredicateData(1, new Object[]{"Charlie", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Charlie"}),
                new PredicateData(0, new Object[]{"Charlie", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Charlie"}),
                new PredicateData(0, new Object[]{"Derek", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Derek"})
        )));

        return getModel(DatabaseTestUtil.getH2Driver(), predicates, rules, observations, targets, validation, truths);
    }

    /**
     * A model with only a single symmetry rule.
     *     10: Person(A) & Person(B) & Friends(A, B) & (A != B) -> Friends(B, A) ^2
     */
    public static ModelInformation getSymmetryModel() {
        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Person", new ConstantType[]{ConstantType.UniqueStringID});
        predicatesInfo.put("Friends", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        Map<String, StandardPredicate> predicates = new HashMap<String, StandardPredicate>();
        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            predicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new WeightedLogicalRule(
                new Implication(
                        new Conjunction(
                                new QueryAtom(predicates.get("Person"), new Variable("A")),
                                new QueryAtom(predicates.get("Person"), new Variable("B")),
                                new QueryAtom(predicates.get("Friends"), new Variable("A"), new Variable("B")),
                                new QueryAtom(GroundingOnlyPredicate.NotEqual, new Variable("A"), new Variable("B"))
                        ),
                        new QueryAtom(predicates.get("Friends"), new Variable("B"), new Variable("A"))
                ),
                10.0f,
                false));

        // Data
        Map<StandardPredicate, List<PredicateData>> observations = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> targets = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> validation = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> truths = new HashMap<StandardPredicate, List<PredicateData>>();


        // Person
        observations.put(predicates.get("Person"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(new Object[]{"Alice"}),
                new PredicateData(new Object[]{"Bob"}),
                new PredicateData(new Object[]{"Charlie"}),
                new PredicateData(new Object[]{"Derek"}),
                new PredicateData(new Object[]{"Eugene"})
        )));

        // Friends
        targets.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(new Object[]{"Alice", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Alice"}),
                new PredicateData(new Object[]{"Bob", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Bob"}),
                new PredicateData(new Object[]{"Charlie", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Charlie"}),
                new PredicateData(new Object[]{"Derek", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Derek"})
        )));

        truths.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(1, new Object[]{"Alice", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Eugene"}),
                new PredicateData(1, new Object[]{"Eugene", "Alice"}),
                new PredicateData(1, new Object[]{"Bob", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Bob"}),
                new PredicateData(0, new Object[]{"Bob", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Bob"}),
                new PredicateData(1, new Object[]{"Charlie", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Charlie"}),
                new PredicateData(0, new Object[]{"Charlie", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Charlie"}),
                new PredicateData(0, new Object[]{"Derek", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Derek"})
        )));

        return getModel(DatabaseTestUtil.getH2Driver(), predicates, rules, observations, targets, validation, truths);
    }

    /**
     * A model with two priors.
     *     1: Friends(A, B) < 0.5 ^2
     *     1: Friends(A, B) > 0.5 ^2
     */
    public static ModelInformation getPriorModel() {
        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Friends", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        Map<String, StandardPredicate> predicates = new HashMap<String, StandardPredicate>();
        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            predicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new WeightedArithmeticRule(
                new ArithmeticRuleExpression(
                        new ArrayList<>(Arrays.asList(new ConstantNumber(1.0f))),
                        new ArrayList<SummationAtomOrAtom>(Arrays.asList(
                                new QueryAtom(predicates.get("Friends"), new Variable("A"), new Variable("B")))
                        ), FunctionComparator.LTE, new ConstantNumber(0.5f)
                ),
                1.0f,
                false));

        rules.add(new WeightedArithmeticRule(
                new ArithmeticRuleExpression(
                        new ArrayList<>(Arrays.asList(new ConstantNumber(1.0f))),
                        new ArrayList<SummationAtomOrAtom>(Arrays.asList(
                                new QueryAtom(predicates.get("Friends"), new Variable("A"), new Variable("B")))
                        ), FunctionComparator.GTE, new ConstantNumber(0.5f)
                ),
                1.0f,
                false));

        // Data
        Map<StandardPredicate, List<PredicateData>> observations = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> targets = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> validation = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> truths = new HashMap<StandardPredicate, List<PredicateData>>();

        // Friends
        targets.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(new Object[]{"Alice", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Alice"}),
                new PredicateData(new Object[]{"Bob", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Bob"}),
                new PredicateData(new Object[]{"Charlie", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Charlie"}),
                new PredicateData(new Object[]{"Derek", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Derek"})
        )));

        truths.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(1, new Object[]{"Alice", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Eugene"}),
                new PredicateData(1, new Object[]{"Eugene", "Alice"}),
                new PredicateData(1, new Object[]{"Bob", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Bob"}),
                new PredicateData(0, new Object[]{"Bob", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Bob"}),
                new PredicateData(1, new Object[]{"Charlie", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Charlie"}),
                new PredicateData(0, new Object[]{"Charlie", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Charlie"}),
                new PredicateData(0, new Object[]{"Derek", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Derek"})
        )));

        return getModel(DatabaseTestUtil.getH2Driver(), predicates, rules, observations, targets, validation, truths);
    }

    /**
     * A model with only a single negative prior rule.
     *     1: ~Friends(A, B) ^2
     */
    public static ModelInformation getNegativePriorModel() {
        // Define Predicates
        Map<String, ConstantType[]> predicatesInfo = new HashMap<String, ConstantType[]>();
        predicatesInfo.put("Friends", new ConstantType[]{ConstantType.UniqueStringID, ConstantType.UniqueStringID});

        Map<String, StandardPredicate> predicates = new HashMap<String, StandardPredicate>();
        for (Map.Entry<String, ConstantType[]> predicateEntry : predicatesInfo.entrySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateEntry.getKey(), predicateEntry.getValue());
            predicates.put(predicateEntry.getKey(), predicate);
        }

        // Define Rules
        List<Rule> rules = new ArrayList<Rule>();
        rules.add(new WeightedLogicalRule(
                new Negation(
                        new QueryAtom(predicates.get("Friends"), new Variable("A"), new Variable("B"))
                ),
                1.0f,
                false));

        // Data
        Map<StandardPredicate, List<PredicateData>> observations = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> targets = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> validation = new HashMap<StandardPredicate, List<PredicateData>>();
        Map<StandardPredicate, List<PredicateData>> truths = new HashMap<StandardPredicate, List<PredicateData>>();

        // Friends
        targets.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(new Object[]{"Alice", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Alice"}),
                new PredicateData(new Object[]{"Alice", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Alice"}),
                new PredicateData(new Object[]{"Bob", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Bob"}),
                new PredicateData(new Object[]{"Bob", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Bob"}),
                new PredicateData(new Object[]{"Charlie", "Derek"}),
                new PredicateData(new Object[]{"Derek", "Charlie"}),
                new PredicateData(new Object[]{"Charlie", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Charlie"}),
                new PredicateData(new Object[]{"Derek", "Eugene"}),
                new PredicateData(new Object[]{"Eugene", "Derek"})
        )));

        truths.put(predicates.get("Friends"), new ArrayList<PredicateData>(Arrays.asList(
                new PredicateData(1, new Object[]{"Alice", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Alice"}),
                new PredicateData(1, new Object[]{"Alice", "Eugene"}),
                new PredicateData(1, new Object[]{"Eugene", "Alice"}),
                new PredicateData(1, new Object[]{"Bob", "Charlie"}),
                new PredicateData(1, new Object[]{"Charlie", "Bob"}),
                new PredicateData(1, new Object[]{"Bob", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Bob"}),
                new PredicateData(0, new Object[]{"Bob", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Bob"}),
                new PredicateData(1, new Object[]{"Charlie", "Derek"}),
                new PredicateData(1, new Object[]{"Derek", "Charlie"}),
                new PredicateData(0, new Object[]{"Charlie", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Charlie"}),
                new PredicateData(0, new Object[]{"Derek", "Eugene"}),
                new PredicateData(0, new Object[]{"Eugene", "Derek"})
        )));

        return getModel(DatabaseTestUtil.getH2Driver(), predicates, rules, observations, targets, validation, truths);
    }

    /**
     * The information you may need to work with the new PSL model.
     * Most of the information is straightforward.
     * id is a unique id given to each constructed model, it is used as an identifier for the datastore.
     * predicates is a mapping of predicate names to the actual predicte.
     */
    public static class ModelInformation {
        public int id;
        public DataStore dataStore;
        public Model model;
        public Map<String, StandardPredicate> predicates;
        public Partition observationPartition;
        public Partition targetPartition;
        public Partition validationPartition;
        public Partition truthPartition;

        // Keep track of open models so we can close them.
        private static List<ModelInformation> openModels = new ArrayList<ModelInformation>();

        public ModelInformation(
                int id, DataStore dataStore, Model model,
                Map<String, StandardPredicate> predicates,
                Partition observationPartition, Partition targetPartition, Partition validationPartition, Partition truthPartition) {
            this.id = id;
            this.dataStore = dataStore;
            this.model = model;
            this.predicates = predicates;
            this.observationPartition = observationPartition;
            this.targetPartition = targetPartition;
            this.validationPartition = validationPartition;
            this.truthPartition = truthPartition;

            openModels.add(this);
        }

        public void close() {
            if (dataStore != null) {
                for (Database database : dataStore.getOpenDatabases()) {
                    database.close();
                }

                dataStore.close();
                dataStore = null;
            }

            if (predicates != null) {
                predicates.clear();
                predicates = null;
            }
        }

        public static void closeAll() {
            for (ModelInformation model : openModels) {
                model.close();
            }

            openModels.clear();
        }

        @Override
        public boolean equals(Object other) {
            return (other != null && other == this);
        }
    }

    /**
     * A simple tuple-like object for data to be inserted.
     */
    public static class PredicateData {
        public double truthValue;
        public Object[] args;

        public PredicateData(double truthValue, Object[] args) {
            this.truthValue = truthValue;
            this.args = args;
        }

        public PredicateData(Object[] args) {
            this(1.0, args);
        }
    }
}
