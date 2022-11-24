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
package org.linqs.psl.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.linqs.psl.config.RuntimeOptions;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class RuntimeConfigTest {
    @Test
    public void testGoodSyntax() {
        for (int i = 0; i < GOOD_SYNTAX.length; i++) {
            RuntimeConfig config = RuntimeConfig.fromJSON(GOOD_SYNTAX[i]);
            assertEquals(config, GOOD_SYNTAX_CONFIG[i]);
        }
    }

    private static final String[] GOOD_SYNTAX = new String[]{
        // Exercise all parts of the syntax.
        "{}",
        "{'rules': ['CommonRule1']}",
        "{'rules': ['CommonRule1','CommonRule2']}",
        "{'rules': 'common/rule/path.txt'}",
        "{'options':{'common.key':'common.value'}}",
        "{'infer':{'rules':['InferRule1'],'options':{'infer.key':'infer.value'}}}",
        "{'learn':{'rules':'learn/rule/path.txt','options':{'learn.key':'learn.value'}}}",
        "{'predicates':{'DataDemo1/2':{'observations':['some/path/to/data.txt'],'targets':[['embeded','data']],'truth':[['embeded','data',0.5]]}}}",
        "{'predicates':{'DataDemo2/2':{'observations':{'all':['common/data.txt'],'learn':[['learn-only','data']],'infer':[['infer-only','data',1.0],'infer/only/data.txt']}}}}",
        "{'predicates':{'EvalDemo1/2':{'evaluations':['ContinuousEvaluator']}}}",
        "{'predicates':{'EvalDemo2/2':{'evaluations':['ContinuousEvaluator']}}}",
        "{'predicates':{'EvalDemo3/2':{'evaluations':[{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}",
        "{'predicates':{'EvalDemo4a/2':{'evaluations':['ContinuousEvaluator',{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}",
        "{'predicates':{'EvalDemo4b/2':{'evaluations':[{'evaluator':'ContinuousEvaluator'},{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}",
        "{'predicates':{'EvalDemo5/2':{'evaluations':[{'evaluator':'DiscreteEvaluator'},{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}",
        "{'predicates':{'ArityDemo1/2':{}}}",
        "{'predicates':{'ArityDemo2':{'arity':2}}}",
        "{'predicates':{'ArityDemo3':{'types':['Int','String']}}}",
        "{'predicates':{'ModelPredicate1/2':{'model':'org.some.model'}}}",
        "{'predicates':{'FunctionPredicate1/2':{'function':'org.some.implementation'}}}",
        "{'predicates':{'OptionsDemo/2':{'options':{'predicate.option.key':'predicate.option.value'}}}}",


        // TEST: TODO
        // Use non-standard JSON syntax that is recognized by our parser.
    };

    // Manually constructed RuntimeConfig objects that match 1-1 with GOOD_SYNTAX.
    private static RuntimeConfig[] GOOD_SYNTAX_CONFIG = new RuntimeConfig[GOOD_SYNTAX.length];

    @BeforeClass
    public static void initGoodSyntaxConfigs() {
        RuntimeConfig config = null;
        RuntimeConfig.PredicateConfigInfo predicate = null;
        RuntimeConfig.EvalInfo eval = null;
        Map<String, String> options = null;

        GOOD_SYNTAX_CONFIG[0] = new RuntimeConfig();

        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1");
        GOOD_SYNTAX_CONFIG[1] = config;

        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1", "CommonRule2");
        GOOD_SYNTAX_CONFIG[2] = config;

        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RulePath("common/rule/path.txt");
        GOOD_SYNTAX_CONFIG[3] = config;

        config = new RuntimeConfig();
        config.options.put("common.key", "common.value");
        GOOD_SYNTAX_CONFIG[4] = config;

        config = new RuntimeConfig();
        config.infer.rules = new RuntimeConfig.RuleList("InferRule1");
        config.infer.options.put("infer.key", "infer.value");
        GOOD_SYNTAX_CONFIG[5] = config;

        config = new RuntimeConfig();
        config.learn.rules = new RuntimeConfig.RulePath("learn/rule/path.txt");
        config.learn.options.put("learn.key", "learn.value");
        GOOD_SYNTAX_CONFIG[6] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("DataDemo1", 2);
        predicate.observations.all.paths.add("some/path/to/data.txt");
        predicate.targets.all.data.add(Arrays.asList("embeded", "data"));
        predicate.truth.all.data.add(Arrays.asList("embeded", "data", "0.5"));
        config.predicates.put("DataDemo1", predicate);
        GOOD_SYNTAX_CONFIG[7] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("DataDemo2", 2);
        predicate.observations.all.paths.add("common/data.txt");
        predicate.observations.learn.data.add(Arrays.asList("learn-only", "data"));
        predicate.observations.infer.data.add(Arrays.asList("infer-only", "data", "1.0"));
        predicate.observations.infer.paths.add("infer/only/data.txt");
        config.predicates.put("DataDemo2", predicate);
        GOOD_SYNTAX_CONFIG[8] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo1", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        config.predicates.put("EvalDemo1", predicate);
        GOOD_SYNTAX_CONFIG[9] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo2", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        config.predicates.put("EvalDemo2", predicate);
        GOOD_SYNTAX_CONFIG[10] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo3", 2);
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo3", predicate);
        GOOD_SYNTAX_CONFIG[11] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo4a", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo4a", predicate);
        GOOD_SYNTAX_CONFIG[12] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo4b", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo4b", predicate);
        GOOD_SYNTAX_CONFIG[13] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo5", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator"));
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo5", predicate);
        GOOD_SYNTAX_CONFIG[14] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ArityDemo1", 2);
        config.predicates.put("ArityDemo1", predicate);
        GOOD_SYNTAX_CONFIG[15] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ArityDemo2", 2);
        config.predicates.put("ArityDemo2", predicate);
        GOOD_SYNTAX_CONFIG[16] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ArityDemo3", 2);
        predicate.types.add("Int");
        predicate.types.add("String");
        config.predicates.put("ArityDemo3", predicate);
        GOOD_SYNTAX_CONFIG[17] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ModelPredicate1", 2);
        predicate.model = "org.some.model";
        config.predicates.put("ModelPredicate1", predicate);
        GOOD_SYNTAX_CONFIG[18] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("FunctionPredicate1", 2);
        predicate.function = "org.some.implementation";
        config.predicates.put("FunctionPredicate1", predicate);
        GOOD_SYNTAX_CONFIG[19] = config;

        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("OptionsDemo", 2);
        predicate.options.put("predicate.option.key", "predicate.option.value");
        config.predicates.put("OptionsDemo", predicate);
        GOOD_SYNTAX_CONFIG[20] = config;
    }
}
