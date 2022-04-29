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
package org.linqs.psl.grounding;

import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.util.IteratorUtils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * A simple {@link GroundRuleStore} that just stores each {@link GroundRule}
 * in memory.
 * addGroundRule() is thread-safe and will silently ignore already added rules.
 * Other methods are not guaranteed safe.
 * Can hold up to the maximum size of a Collection (about 2^32).
 */
public class MemoryGroundRuleStore implements GroundRuleStore {
    protected List<GroundRule> groundRules;

    public MemoryGroundRuleStore() {
        groundRules = new ArrayList<GroundRule>();
    }

    @Override
    public synchronized void addGroundRule(GroundRule groundRule) {
        groundRules.add(groundRule);
    }

    /**
     * O(n) check for a ground rule.
     */
    @Override
    public boolean containsGroundRule(GroundRule groundRule) {
        return groundRules.contains(groundRule);
    }

    @Override
    public Iterable<WeightedGroundRule> getCompatibilityRules() {
        return IteratorUtils.filterClass(groundRules, WeightedGroundRule.class);
    }

    @Override
    public Iterable<UnweightedGroundRule> getConstraintRules() {
        return IteratorUtils.filterClass(groundRules, UnweightedGroundRule.class);
    }

    @Override
    public Iterable<GroundRule> getGroundRules() {
        return groundRules;
    }

    @Override
    public Iterable<GroundRule> getGroundRules(Rule rule) {
        final Rule finalRule = rule;

        return IteratorUtils.filter(groundRules, new IteratorUtils.FilterFunction<GroundRule>() {
            public boolean keep(GroundRule groundRule) {
                // Note that order is very important because not all GroundRules have a parent.
                return finalRule.equals(groundRule.getRule());
            }
        });
    }

    /**
     * O(n).
     */
    @Override
    public void removeGroundRule(GroundRule groundRule) {
        groundRules.remove(groundRule);
    }

    /**
     * O(n).
     */
    @Override
    public void removeGroundRules(Rule rule) {
        Iterator<GroundRule> iterator = groundRules.iterator();
        while (iterator.hasNext()) {
            GroundRule groundRule = iterator.next();
            // Note that order is very important because not all GroundRules have a parent.
            if (rule.equals(groundRule.getRule())) {
                iterator.remove();
            }
        }
    }

    @Override
    public long size() {
        return groundRules.size();
    }

    /**
     * O(n).
     */
    @Override
    public long count(Rule rule) {
        long count = 0;
        for (GroundRule groundRule : groundRules) {
            // Note that order is very important because not all GroundRules have a parent.
            if (rule.equals(groundRule.getRule())) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void close() {
        if (groundRules != null) {
            groundRules.clear();
            groundRules = null;
        }
    }
}
