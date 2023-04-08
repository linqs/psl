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
package org.linqs.psl.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RuntimeConfigTest extends RuntimeTest {
    @Test
    public void testGoodSyntax() {
        for (int i = 0; i < GOOD_SYNTAX.size(); i++) {
            RuntimeConfig config = RuntimeConfig.fromJSON(GOOD_SYNTAX.get(i));
            assertEquals(config, GOOD_SYNTAX_CONFIG.get(i));
        }
    }

    @Test
    public void testBadSyntax() {
        for (int i = 0; i < BAD_SYNTAX.size(); i++) {
            try {
                RuntimeConfig.fromJSON(BAD_SYNTAX.get(i));
                fail("Failed to throw an error on parse for config at index " + i + ".");
            } catch (Exception ex) {
                // Expected.
            }
        }
    }

    @Test
    public void testBadValidation() {
        for (int i = 0; i < BAD_VALIDATION.size(); i++) {
            RuntimeConfig config = RuntimeConfig.fromJSON(BAD_VALIDATION.get(i));

            try {
                config.validate();
                fail("Failed to throw an error on validation for config at index " + i + ".");
            } catch (Exception ex) {
                // Expected.
            }
        }
    }

    // A 1-1 pairing with string JSON and a manually constructed RuntimeConfig object.
    private static List<String> GOOD_SYNTAX = null ;
    private static List<RuntimeConfig> GOOD_SYNTAX_CONFIG = null;

    // JSON that fails to parse, but not for JSON syntax reasons.
    private static List<String> BAD_SYNTAX = Arrays.asList(
        "{'predicates':{'BadArity/2':{'arity':3}}}",
        // Bad type.
        "{'predicates':{'BadType/2':{'type':'org.some.type'}}}"
    );

    // JSON that passes parsing, but fails validation.
    private static List<String> BAD_VALIDATION = Arrays.asList(
        // Arity issues.
        "{'predicates':{'BadArity':{}}}",
        "{'predicates':{'BadArity':{'arity':0}}}",
        "{'predicates':{'BadArity/2':{'types':['UniqueIntID']}}}",
        "{'predicates':{'BadArity/2':{'types':['UniqueIntID', 'UniqueIntID', 'UniqueIntID']}}}",
        // Bad Type.
        "{'predicates':{'BadType/1':{'types':['ZZZ']}}}",
        // Bad data path.
        "{'predicates':{'BadPath/2':{'observations':['some/path/to/data.txt']}}}",
        // Bad embeded data size.
        "{'predicates':{'BadData/2':{'observations':[['1']]}}}",
        "{'predicates':{'BadData/2':{'observations':[['1', '2', '3', '4']]}}}",
        // Bad function.
        "{'predicates':{'BadFunction/2':{'function':'org.some.implementation'}}}",
        // Bad evaluation.
        "{'predicates':{'BadEval/2':{'evaluations':['FakeEvaluator']}}}",
        "{'predicates':{'BadEval/2':{'evaluations':['org.linqs.psl.runtime.RuntimeConfig']}}}",
        "{'predicates':{'BadEval/2':{'evaluations':[{'evaluator':'ContinuousEvaluator','primary':true},{'evaluator':'ContinuousEvaluator','primary':true}]}}}",
        // Bad rule syntax.
        "{'rules': ['CommonRule1']}",
        // Non-existant predicate in rule.
        "{'rules': ['1.0: Foo(A, B) = 0.0']}",
        "{'learn': {'rules': ['1.0: Foo(A, B) = 0.0']}}",
        "{'infer': {'rules': ['1.0: Foo(A, B) = 0.0']}}"
    );

    @BeforeClass
    public static void initGoodSyntaxConfigs() {
        GOOD_SYNTAX = new ArrayList<String>();
        GOOD_SYNTAX_CONFIG = new ArrayList<RuntimeConfig>();

        RuntimeConfig config = null;
        RuntimeConfig.PredicateConfigInfo predicate = null;
        RuntimeConfig.EvalInfo eval = null;
        Map<String, String> options = null;

        // Exercise all parts of the syntax.

        GOOD_SYNTAX.add("{}");
        GOOD_SYNTAX_CONFIG.add(new RuntimeConfig());

        GOOD_SYNTAX.add("{'rules': ['CommonRule1']}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'rules': ['CommonRule1','CommonRule2']}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1", "CommonRule2");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'rules': 'common/rule/path.txt'}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RulePath("common/rule/path.txt");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'options':{'common.key':'common.value'}}");
        config = new RuntimeConfig();
        config.options.put("common.key", "common.value");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'infer':{'rules':['InferRule1'],'options':{'infer.key':'infer.value'}}}");
        config = new RuntimeConfig();
        config.infer.rules = new RuntimeConfig.RuleList("InferRule1");
        config.infer.options.put("infer.key", "infer.value");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'learn':{'rules':'learn/rule/path.txt','options':{'learn.key':'learn.value'}}}");
        config = new RuntimeConfig();
        config.learn.rules = new RuntimeConfig.RulePath("learn/rule/path.txt");
        config.learn.options.put("learn.key", "learn.value");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'DataDemo1/2':{'observations':['some/path/to/data.txt'],'targets':[['embeded','data']],'truth':[['embeded','data',0.5]]}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("DataDemo1", 2);
        predicate.observations.all.paths.add("some/path/to/data.txt");
        predicate.targets.all.data.add(Arrays.asList("embeded", "data"));
        predicate.truth.all.data.add(Arrays.asList("embeded", "data", "0.5"));
        config.predicates.put("DataDemo1", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'DataDemo2/2':{'observations':{'all':['common/data.txt'],'learn':[['learn-only','data']],'infer':[['infer-only','data',1.0],'infer/only/data.txt']}}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("DataDemo2", 2);
        predicate.observations.all.paths.add("common/data.txt");
        predicate.observations.learn.data.add(Arrays.asList("learn-only", "data"));
        predicate.observations.infer.data.add(Arrays.asList("infer-only", "data", "1.0"));
        predicate.observations.infer.paths.add("infer/only/data.txt");
        config.predicates.put("DataDemo2", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'EvalDemo1/2':{'evaluations':['ContinuousEvaluator']}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo1", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        config.predicates.put("EvalDemo1", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'EvalDemo2/2':{'evaluations':['ContinuousEvaluator']}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo2", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        config.predicates.put("EvalDemo2", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'EvalDemo3/2':{'evaluations':[{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo3", 2);
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo3", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'EvalDemo4a/2':{'evaluations':['ContinuousEvaluator',{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo4a", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo4a", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'EvalDemo4b/2':{'evaluations':[{'evaluator':'ContinuousEvaluator'},{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo4b", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("ContinuousEvaluator"));
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo4b", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'EvalDemo5/2':{'evaluations':[{'evaluator':'DiscreteEvaluator'},{'evaluator':'DiscreteEvaluator','options':{'discreteevaluator.representative':'RMSE','discreteevaluator.threshold':0.75}}]}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("EvalDemo5", 2);
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator"));
        options = new HashMap<String, String>();
        options.put("discreteevaluator.representative", "RMSE");
        options.put("discreteevaluator.threshold", "0.75");
        predicate.evaluations.add(new RuntimeConfig.EvalInfo("DiscreteEvaluator", options, false));
        config.predicates.put("EvalDemo5", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'ArityDemo1/2':{}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ArityDemo1", 2);
        config.predicates.put("ArityDemo1", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'ArityDemo2':{'arity':2}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ArityDemo2", 2);
        config.predicates.put("ArityDemo2", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'ArityDemo3':{'types':['Int','String']}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("ArityDemo3", -1);
        predicate.types.add("Int");
        predicate.types.add("String");
        config.predicates.put("ArityDemo3", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'TypePredicate1/2':{'type':'StandardPredicate'}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("TypePredicate1", 2);
        predicate.setType("StandardPredicate");
        config.predicates.put("TypePredicate1", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'FunctionPredicate1/2':{'function':'org.some.implementation'}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("FunctionPredicate1", 2);
        predicate.function = "org.some.implementation";
        config.predicates.put("FunctionPredicate1", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'predicates':{'OptionsDemo/2':{'options':{'predicate.option.key':'predicate.option.value'}}}}");
        config = new RuntimeConfig();
        predicate = new RuntimeConfig.PredicateConfigInfo("OptionsDemo", 2);
        predicate.options.put("predicate.option.key", "predicate.option.value");
        config.predicates.put("OptionsDemo", predicate);
        GOOD_SYNTAX_CONFIG.add(config);

        // Non-standard JSON syntax that is recognized by our parser.

        // Comments

        GOOD_SYNTAX.add("{'rules': /* Comment */ ['CommonRule1','CommonRule2']}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1", "CommonRule2");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'rules': /* Comment \n Comment */ ['CommonRule1','CommonRule2']}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1", "CommonRule2");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'rules': ['CommonRule1','CommonRule2'] // Comment \n }");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1", "CommonRule2");
        GOOD_SYNTAX_CONFIG.add(config);

        GOOD_SYNTAX.add("{'rules': ['CommonRule1','CommonRule2'] # Comment \n }");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1", "CommonRule2");
        GOOD_SYNTAX_CONFIG.add(config);

        // Quotes
        // Technicially JSON uses double quotes and single quotes are non-standard,
        // but it is easier for us to use literal single quotes in Java.

        GOOD_SYNTAX.add("{\"rules\": [\"CommonRule1\"]}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1");
        GOOD_SYNTAX_CONFIG.add(config);

        // Leading Zeroes

        GOOD_SYNTAX.add("{'options': {'int': 01, 'float': 01.2}}");
        config = new RuntimeConfig();
        config.options.put("int", "1");
        config.options.put("float", "1.2");
        GOOD_SYNTAX_CONFIG.add(config);

        // Leading Plus Sign

        GOOD_SYNTAX.add("{'options': {'int': +1, 'float': +1.2}}");
        config = new RuntimeConfig();
        config.options.put("int", "1");
        config.options.put("float", "1.2");
        GOOD_SYNTAX_CONFIG.add(config);

        // Trailing Comma

        GOOD_SYNTAX.add("{'rules': ['CommonRule1',], 'options': {'foo': 'bar',},}");
        config = new RuntimeConfig();
        config.rules = new RuntimeConfig.RuleList("CommonRule1");
        config.options.put("foo", "bar");
        GOOD_SYNTAX_CONFIG.add(config);

        if (GOOD_SYNTAX.size() != GOOD_SYNTAX_CONFIG.size()) {
            throw new IllegalStateException("Mismatch in GOOD_SYNTAX[_CONFIG] sizes.");
        }
    }
}
