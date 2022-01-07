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
package org.linqs.psl.application.learning.weight;

import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.atom.QueryAtom;
import org.linqs.psl.model.formula.Conjunction;
import org.linqs.psl.model.formula.Implication;
import org.linqs.psl.model.formula.Negation;
import org.linqs.psl.model.predicate.GroundingOnlyPredicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.logical.WeightedLogicalRule;
import org.linqs.psl.model.term.UniqueStringID;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;
import org.linqs.psl.util.MathUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * General tests for all classes that implement WeightLearningApplication.
 * TODO(eriq): We have to disable some tests because they give different weights.
 *  But some are legitimately different. We need more robust cases.
 */
public abstract class WeightLearningTest extends PSLBaseTest {
    public static final String RULE_PRIOR = "prior";
    public static final String RULE_NICE = "nice";
    public static final String RULE_SYMMETRY = "symmetry";

    protected Database weightLearningTrainDB;
    protected Database weightLearningTruthDB;
    protected TestModel.ModelInformation info;

    // Give all the rules a name to make it easier to check weight learning results.
    protected Map<String, WeightedRule> ruleMap;

    // Variables thatcan control disabling checking test results.
    protected boolean assertBaseTest;
    protected boolean assertFriendshipRankTest;

    public WeightLearningTest() {
        // TODO(eriq): Disable until weight learning is in a stable place.
        assertBaseTest = false;
        assertFriendshipRankTest = false;
    }

    @Before
    public void setup() {
        Config.init();
        initModel(true);
    }

    @After
    public void cleanup() {
        disableLogger();

        weightLearningTrainDB.close();
        weightLearningTrainDB = null;

        weightLearningTruthDB.close();
        weightLearningTruthDB = null;

        info.dataStore.close();
        info = null;

        Config.init();
    }

    /**
     * @return the WeightLearningApplication to be tested.
     */
    protected abstract WeightLearningApplication getWLA();

    protected void initModel(boolean useNice) {
        if (weightLearningTrainDB != null) {
            weightLearningTrainDB.close();
            weightLearningTrainDB = null;
        }

        if (weightLearningTruthDB != null) {
            weightLearningTruthDB.close();
            weightLearningTruthDB = null;
        }

        if (info != null) {
            info.dataStore.close();
            info = null;
        }

        info = TestModel.getModel(useNice);

        // Put all the rules in a map for easier checking later and set all the rule weights to 1.0.
        ruleMap = new HashMap<String, WeightedRule>();
        for (Rule rawRule : info.model.getRules()) {
            WeightedRule rule = (WeightedRule)rawRule;

            if (MathUtils.equals(rule.getWeight(), 1.0)) {
                ruleMap.put(RULE_PRIOR, rule);
            } else if (MathUtils.equals(rule.getWeight(), 5.0)) {
                ruleMap.put(RULE_NICE, rule);
            } else if (MathUtils.equals(rule.getWeight(), 10.0)) {
                ruleMap.put(RULE_SYMMETRY, rule);
            } else {
                throw new IllegalArgumentException("Unknown rule: " + rule);
            }

            rule.setWeight(1.0f);
        }

        Set<StandardPredicate> allPredicates = new HashSet<StandardPredicate>(info.predicates.values());
        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>(info.predicates.values());
        closedPredicates.remove(info.predicates.get("Friends"));

        weightLearningTrainDB = info.dataStore.getDatabase(info.targetPartition, closedPredicates, info.observationPartition);
        weightLearningTruthDB = info.dataStore.getDatabase(info.truthPartition, allPredicates);
    }

    /**
     * A quick test that only checks to see if the weight learning is.
     * This is not a targeted or exhaustive test, just a starting point.
     */
    @Test
    public void baseTest() {
        WeightLearningApplication weightLearner = getWLA();
        weightLearner.learn();
        weightLearner.close();

        if (assertBaseTest) {
            assertRank(RULE_PRIOR, RULE_NICE, RULE_SYMMETRY);
        }
    }

    @Test
    public void friendshipRankTest() {
        // Reset the current rules.
        info.model.clear();
        ruleMap.clear();

        WeightedRule rule = null;

        // Always true
        // Person('Alice') & Person(B) & ('Alice' != B) >> Friends('Alice', B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Person"), new UniqueStringID("Alice")),
                    new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NotEqual, new UniqueStringID("Alice"), new Variable("B"))
                ),
                new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("Alice"), new Variable("B"))
            ),
            1.0f,
            true
        );
        info.model.addRule(rule);
        ruleMap.put("Alice", rule);

        // Almost always false (except for Eugene)
        // Person('Bob') & Person(B) & ('Bob' != B) >> Friends('Bob', B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Person"), new UniqueStringID("Bob")),
                    new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NotEqual, new UniqueStringID("Bob"), new Variable("B"))
                ),
                new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("Bob"), new Variable("B"))
            ),
            1.0f,
            true
        );
        info.model.addRule(rule);
        ruleMap.put("Bob", rule);

        // Almost always false (except for Alice)
        // Person('Eugene') & Person(B) & ('Eugene' != B) >> Friends('Eugene', B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Person"), new UniqueStringID("Eugene")),
                    new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NotEqual, new UniqueStringID("Eugene"), new Variable("B"))
                ),
                new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("Eugene"), new Variable("B"))
            ),
            1.0f,
            true
        );
        info.model.addRule(rule);
        ruleMap.put("Eugene", rule);

        // Always false
        // Person('Alice') & Person(B) & ('Alice' != B) >> !Friends('Alice', B)
        rule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Person"), new UniqueStringID("Alice")),
                    new QueryAtom(info.predicates.get("Person"), new Variable("B")),
                    new QueryAtom(GroundingOnlyPredicate.NotEqual, new UniqueStringID("Alice"), new Variable("B"))
                ),
                new Negation(new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("Alice"), new Variable("B")))
            ),
            1.0f,
            true
        );
        info.model.addRule(rule);
        ruleMap.put("NotAlice", rule);

        WeightLearningApplication weightLearner = getWLA();
        weightLearner.learn();
        weightLearner.close();

        if (assertFriendshipRankTest) {
            assertRank("NotAlice", "Eugene", "Bob", "Alice");
        }
    }

    /**
     * Ensure that a rule with no groundings does not break.
     */
    @Test
    public void ruleWithNoGroundingsTest() {
        // Add in a rule that will have zero groundings.
        Rule newRule = new WeightedLogicalRule(
            new Implication(
                new Conjunction(
                    new QueryAtom(info.predicates.get("Nice"), new UniqueStringID("ZzZ__FAKE_PERSON_A__ZzZ")),
                    new QueryAtom(info.predicates.get("Nice"), new Variable("B"))
                ),
                new QueryAtom(info.predicates.get("Friends"), new UniqueStringID("ZzZ__FAKE_PERSON_A__ZzZ"), new Variable("B"))
            ),
            5.0f,
            true
        );
        info.model.addRule(newRule);

        WeightLearningApplication weightLearner = getWLA();
        weightLearner.learn();
        weightLearner.close();
    }

    /**
     * Assert that the rules (specified by the keys on the rule map) are in the same order as passed in.
     * The order should be ascending.
     * No ties allowed.
     */
    protected void assertRankHard(String... rank) {
        List<Rule> rules = new ArrayList<Rule>(info.model.getRules());
        Collections.sort(rules, new Comparator<Rule>() {
            @Override
            public int compare(Rule a, Rule b) {
                return MathUtils.compare(((WeightedRule)a).getWeight(), ((WeightedRule)b).getWeight());
            }
        });

        assertEquals(rules.size(), rank.length);
        for (int i = 0; i < rank.length; i++) {
            assertEquals(ruleMap.get(rank[i]), rules.get(i));
        }
    }

    /**
     * Assert that the rules (specified by the keys on the rule map) are in the same order as passed in.
     * The order should be ascending.
     * Ties are allowed.
     */
    protected void assertRank(String... rank) {
        List<Rule> rules = new ArrayList<Rule>(info.model.getRules());
        Collections.sort(rules, new Comparator<Rule>() {
            @Override
            public int compare(Rule a, Rule b) {
                return MathUtils.compare(((WeightedRule)a).getWeight(), ((WeightedRule)b).getWeight());
            }
        });
        assertEquals(rules.size(), rank.length);

        List<Set<Rule>> ruleSets = new ArrayList<Set<Rule>>();
        double lastWeight = -1;
        for (int i = 0; i < rules.size(); i++) {
            WeightedRule rule = (WeightedRule)rules.get(i);
            double weight = rule.getWeight();

            if (i != 0 && MathUtils.equals(weight, lastWeight)) {
                ruleSets.get(ruleSets.size() - 1).add(rule);
            } else {
                Set<Rule> newSet = new HashSet<Rule>();
                newSet.add(rule);
                ruleSets.add(newSet);
            }

            lastWeight = weight;
        }

        int interSetIndex = 0;
        int intraSetIndex = 0;

        for (int i = 0; i < rank.length; i++) {
            Rule expected = ruleMap.get(rank[i]);

            if (!ruleSets.get(interSetIndex).contains(expected)) {
                System.out.println("Rule Ranks:");
                for (Rule rule : rules) {
                    System.out.println("  " + rule);
                }
                fail(String.format("Did not find expected rule (%s) at index %d.", expected, i));
            }

            intraSetIndex++;
            if (ruleSets.get(interSetIndex).size() <= intraSetIndex) {
                interSetIndex++;
                intraSetIndex = 0;
            }
        }
    }
}
