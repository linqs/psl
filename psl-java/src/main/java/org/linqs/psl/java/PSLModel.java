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
package org.linqs.psl.java;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.FunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.parser.RulePartial;

import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * A representation of a PSL model for the Java interface.
 */
public class PSLModel extends Model {
    private DataStore dataStore;

    public PSLModel(DataStore dataStore) {
        this.dataStore = dataStore;
    }

    /**
     * Add a standard predicate to the model.
     */
    public StandardPredicate addPredicate(String name, ConstantType... args) {
        StandardPredicate predicate = StandardPredicate.get(name, args);
        dataStore.registerPredicate(predicate);
        return predicate;
    }

    /**
     * Add an external function to the model.
     */
    public FunctionalPredicate addFunction(String name, ExternalFunction function) {
        FunctionalPredicate predicate = ExternalFunctionalPredicate.get(name, function);
        return predicate;
    }

    /**
     * Add a rule that is fully specified in string form.
     */
    public Rule addRule(String ruleString) {
        Rule rule = ModelLoader.loadRule(ruleString);
        addRule(rule);
        return rule;
    }

    /**
     * Add a weighted rule that has the body in string form, but the additional traits (weight/squared) not in the string.
     */
    public Rule addWeightedRule(String ruleString, float weight, boolean squared) {
        return addRule(ruleString, true, weight, squared);
    }

    /**
     * Add an unweighted rule.
     */
    public Rule addUnweightedRule(String ruleString) {
        return addRule(ruleString);
    }

    /**
     * Add a rule that has the body in string form, but the additional traits (weight/squared) unspecified.
     */
    public Rule addRule(String ruleString, boolean weighted, float weight, boolean squared) {
        RulePartial rulePartial = ModelLoader.loadRulePartial(ruleString);

        Rule rule = null;

        if (weighted) {
            rule = rulePartial.toRule(Float.valueOf(weight), Boolean.valueOf(squared));
        } else {
            rule = rulePartial.toRule(null, null);
        }

        addRule(rule);
        return rule;
    }

    /**
     * Alternative interface to addRules().
     */
    public List<Rule> addRules(String rules) {
        return addRules(new StringReader(rules));
    }

    /**
     * Add all the rules from a reader.
     * Rules must be fully specified (with weight and squared) in their string form.
     */
    public List<Rule> addRules(Reader rules) {
        List<Rule> addedRules = new ArrayList<Rule>();

        Model model = ModelLoader.load(rules);
        for (Rule rule : model.getRules()) {
            addRule(rule);
            addedRules.add(rule);
        }

        return addedRules;
    }

    public Predicate getPredicate(String name) {
        return Predicate.get(name);
    }

    public StandardPredicate getStandardPredicate(String name) {
        return StandardPredicate.get(name);
    }

    public FunctionalPredicate getFunctionalPredicate(String name) {
        return ExternalFunctionalPredicate.get(name);
    }
}
