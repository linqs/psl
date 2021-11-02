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
package org.linqs.psl.model.rule;

import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.RawQuery;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * A template for functions that either constrain or measure the compatibility
 * of the values of GroundAtom.
 * A Rule is responsible for instantiating GroundRules.
 * A Rule must instantiate only WeightedGroundRules or only UnweightedGroundRules.
 */
public interface Rule extends Serializable {
    /**
     * Adds all GroundRules to a GroundRuleStore using the AtomManager
     * to instantiate ground atoms.
     *
     * @param atomManager AtomManager on which to base the grounding
     * @param groundRuleStore store for new GroundRules
     * @return the number of ground rules generated.
     */
    public long groundAll(AtomManager atomManager, GroundRuleStore groundRuleStore);

    public boolean isWeighted();

    public String getName();

    /**
     * Does this rule support rewriting the grounding formula.
     * Rules that do can take advantage of some more advanced grounding techniques.
     * However, they will have to supply their grounding queries as a Formula
     * instead of a raw query.
     * Rules that return true here must also return true for supportsIndividualGrounding().
     */
    public boolean supportsGroundingQueryRewriting();

    /**
     * Get a grounding formula that can be rewritten.
     * Should throw if supportsGroundingQueryRewriting() == false.
     */
    public Formula getRewritableGroundingFormula();

    /**
     * Does this rule support grounding out single instances at a time.
     * Rules that do can take advantage of some more advanced grounding techniques.
     */
    public boolean supportsIndividualGrounding();

    /**
     * Get the formula that we can use for grounding.
     * Should throw if supportsIndividualGrounding() == false.
     */
    public RawQuery getGroundingQuery(AtomManager atomManager);

    /**
     * Get the formula that we can use for grounding.
     * Should throw if supportsIndividualGrounding() == false.
     */
    public void ground(Constant[] constants, Map<Variable, Integer> variableMap, AtomManager atomManager, List<GroundRule> results);

    /**
     * Check if this rule needs to be broken up into multiple rules.
     * This may be because of language semantics or performance.
     */
    public boolean requiresSplit();

    /**
     * Split this rule into multiple rules.
     * The net effect of all the rules should be the same as the pre-split rule.
     */
    public List<Rule> split();

    /**
     * Check if this rule instance is registered.
     */
    public boolean isRegistered();

    /**
     * Ensure that the rule instance is registered.
     */
    public void ensureRegistration();

    /**
     * Ensure that the rule instance is not registered.
     */
    public void unregister();
}
