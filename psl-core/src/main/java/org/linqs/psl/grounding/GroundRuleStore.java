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
import org.linqs.psl.model.rule.WeightedRule;

/**
 * Container for a set of {@link GroundRule GroundRules}.
 * <p>
 * Since this container uses set semantics, no two GroundRules that are equal
 * can be stored in it. If a {@link Rule} wants to add another GroundRule
 * that does the same thing over the same GroundAtoms, then it should retrieve
 * the original GroundRule and modify it.
 */
public interface GroundRuleStore {
    /**
     * Adds a GroundRule to this store.
     * The behavior on what to do when a rule is already added is up tothe implementation.
     * It may ignore it or throw an exception.
     *
     * @param rule the GroundRule to add
     */
    public void addGroundRule(GroundRule rule);

    /**
     * Release any memory held by the store.
     * A store that has been closed cannot be used again.
     */
    public void close();

    /**
     * Removes a GroundRule from this store.
     * Check with the implementation on the runtime.
     *
     * @param rule the GroundRule to remove
     * @throws IllegalArgumentException if rule is not in this store
     */
    public void removeGroundRule(GroundRule rule);

    /**
     * Removes all GroundRules that was instantiated by a given rule.
     * Check with the implementation on the runtime.
     *
     * @param rule the originator of the ground rules
     */
    public void removeGroundRules(Rule rule);

    /**
     * Checks whether a GroundRule is in this store.
     * Check with the implementation on the runtime.
     *
     * @param rule the GroundRule to check
     * @return true if rule is in this store
     */
    public boolean containsGroundRule(GroundRule rule);

    /**
     * @return every GroundRule in this store
     */
    public Iterable<GroundRule> getGroundRules();

    /**
     * @return every {@link WeightedGroundRule} in this store
     */
    public Iterable<WeightedGroundRule> getCompatibilityRules();

    /**
     * @return every {@link UnweightedGroundRule} in this store
     */
    public Iterable<UnweightedGroundRule> getConstraintRules();

    /**
     * Returns every GroundRule that was instantiated by a given Rule.
     *
     * @param rule the Rule of the GroundRules to return
     * @return the Rule's GroundRules
     */
    public Iterable<GroundRule> getGroundRules(Rule rule);

    /**
     * @return the number of GroundRules in this store
     */
    public long size();

    /**
     * @return the number of GroundRules for a specific rule in this store
     */
    public long count(Rule rule);
}
