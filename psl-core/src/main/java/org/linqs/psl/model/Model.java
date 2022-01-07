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
package org.linqs.psl.model;

import org.linqs.psl.application.ModelApplication;
import org.linqs.psl.model.rule.Rule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * A probabilistic soft logic model.
 * Encapsulates a set of {@link Rule Rules}.
 */
public class Model {
    private static final Logger log = LoggerFactory.getLogger(Model.class);

    protected final List<Rule> rules;

    public Model() {
        rules = new LinkedList<Rule>();
    }

    public List<Rule> getRules() {
        return Collections.unmodifiableList(rules);
    }

    /**
     * Adds a Rule to this Model.
     *
     * @throws IllegalArgumentException if the Rule is already in this Model.
     */
    public void addRule(Rule rule) {
        if (rules.contains(rule)) {
            log.warn("Rule already added to this model, skipping add: " + rule);
            return;
        }

        if (!rule.requiresSplit()) {
            rules.add(rule);
            return;
        }

        log.info("Rule is being split into multiple rules: {}", rule);

        // This rule needs to be split into multiple rules.
        for (Rule splitRule : rule.split()) {
            rules.add(splitRule);
        }
    }

    /**
     * Removes a Rule from this Model.
     *
     * @throws IllegalArgumentException if the Rule is not in this Model.
     */
    public void removeRule(Rule rule) {
        if (!rules.contains(rule)) {
            throw new IllegalArgumentException("Rule (" + rule + ") not in this model.");
        }

        rules.remove(rule);
    }

    public void clear() {
        rules.clear();
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("Model:");
        builder.append(System.lineSeparator());
        builder.append(asString());
        return builder.toString();
    }

    /**
     * Create a model string that can be directly interpreted by the parser.
     */
    public String asString() {
        StringBuilder builder = new StringBuilder();
        if (rules.size() > 0) {
            builder.append(rules.get(0));
        }

        for (int i = 1; i < rules.size(); i++) {
            builder.append(System.lineSeparator()).append(rules.get(i));
        }

        return builder.toString();
    }
}
