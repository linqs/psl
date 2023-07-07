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
package org.linqs.psl.application.learning.weight.gradient;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.mpe.ADMMInference;
import org.linqs.psl.config.Config;
import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.test.PSLBaseTest;
import org.linqs.psl.test.TestModel;
import org.linqs.psl.util.MathUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * General tests for all classes that implement LearningBatchGenerator.
 */
public abstract class LearningBatchGeneratorTest extends PSLBaseTest {
    public static final String RULE_PRIOR = "prior";
    public static final String RULE_NICE = "nice";
    public static final String RULE_SYMMETRY = "symmetry";

    protected Database trainTargetDatabase;
    protected Database trainTruthDatabase;

    protected Database validationTargetDatabase;
    protected Database validationTruthDatabase;


    protected TestModel.ModelInformation info;

    // Give all the rules a name to make it easier to check weight learning results.
    protected Map<String, WeightedRule> ruleMap;

    public LearningBatchGeneratorTest() {
        super();
    }

    @Before
    public void setup() {
        Config.init();
        initModel(true);
    }

    protected abstract LearningBatchGenerator getGenerator(InferenceApplication inference);

    protected InferenceApplication getInference(List<Rule> rules, Database db) {
        return new ADMMInference(rules, db);
    }

    @After
    public void cleanup() {
        disableLogger();

        trainTargetDatabase.close();
        trainTargetDatabase = null;

        trainTruthDatabase.close();
        trainTruthDatabase = null;

        validationTargetDatabase.close();
        validationTargetDatabase = null;

        validationTruthDatabase.close();
        validationTruthDatabase = null;

        info.dataStore.close();
        info = null;

        Config.init();
    }

    protected void initModel(boolean useNice) {
        if (trainTargetDatabase != null) {
            trainTargetDatabase.close();
            trainTargetDatabase = null;
        }

        if (trainTruthDatabase != null) {
            trainTruthDatabase.close();
            trainTruthDatabase = null;
        }

        if (validationTargetDatabase != null) {
            validationTargetDatabase.close();
            validationTargetDatabase = null;
        }

        if (validationTruthDatabase != null) {
            validationTruthDatabase.close();
            validationTruthDatabase = null;
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

        Set<StandardPredicate> allTrainPredicates = new HashSet<StandardPredicate>(info.predicates.values());
        Set<StandardPredicate> closedTrainPredicates = new HashSet<StandardPredicate>(info.predicates.values());
        closedTrainPredicates.remove(info.predicates.get("Friends"));

        trainTargetDatabase = info.dataStore.getDatabase(info.targetPartition, closedTrainPredicates, info.observationPartition);
        trainTruthDatabase = info.dataStore.getDatabase(info.truthPartition, allTrainPredicates);

        validationTargetDatabase = info.dataStore.getDatabase(info.validationTargetPartition, closedTrainPredicates, info.validationObservationPartition);
        validationTruthDatabase = info.dataStore.getDatabase(info.validationTruthPartition, allTrainPredicates);
    }

    @Test
    public void baseTest() {
        LearningBatchGenerator batchGenerator = getGenerator(getInference(info.model.getRules(), trainTargetDatabase));
        batchGenerator.generateBatches();

        batchGenerator.close();
    }
}
