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
package org.linqs.psl.model.rule;

import org.linqs.psl.database.Database;
import org.linqs.psl.database.RawQuery;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.atom.Atom;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.reasoner.term.TermStore;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A template for functions that either constrain or measure the compatibility
 * of the values of GroundAtom.
 * A Rule is responsible for instantiating GroundRules.
 * A Rule must instantiate only WeightedGroundRules or only UnweightedGroundRules.
 */
public interface Rule extends Serializable {
    public long groundAll(TermStore termStore, Database database, Grounding.GroundRuleCallback groundRuleCallback);

    /**
     * A boolean indicating whether this rule is active during inference.
     */
    public boolean isActive();

    /**
     * Set the active state of the rule.
     */
    public void setActive(boolean active);

    public boolean isWeighted();

    public String getName();

    /**
     * Get the "core" atoms for a query.
     * What defines the core set is up to the individual rule type,
     * but it should not include filters or summations.
     */
    public void getCoreAtoms(Set<Atom> result);

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
    public RawQuery getGroundingQuery(Database database);

    /**
     * Get the formula that we can use for grounding.
     * Should throw if supportsIndividualGrounding() == false.
     */
    public void ground(Constant[] constants, Map<Variable, Integer> variableMap, Database database, List<GroundRule> results);

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
