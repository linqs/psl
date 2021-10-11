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
package org.linqs.psl.grounding;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.grounding.collective.CandidateGeneration;
import org.linqs.psl.grounding.collective.CandidateQuery;
import org.linqs.psl.grounding.collective.Containment;
import org.linqs.psl.grounding.collective.Coverage;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Static utilities for common grounding tasks.
 */
public class Grounding {
    private static final Logger log = LoggerFactory.getLogger(Grounding.class);

    // Static only.
    private Grounding() {}

    public static long groundAll(List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        boolean collective = Options.GROUNDING_COLLECTIVE.getBoolean();
        if (collective) {
            return groundCollective(rules, atomManager, groundRuleStore);
        }

        return groundIndependent(rules, atomManager, groundRuleStore);
    }

    /**
     * Ground each of the passed in rules independently.
     */
    private static long groundIndependent(List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        long groundCount = 0;
        for (Rule rule : rules) {
            groundCount += rule.groundAll(atomManager, groundRuleStore);
        }

        return groundCount;
    }

    /**
     * Ground all the given rules collectively.
     */
    private static long groundCollective(List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        // Rules that cannot take part in the collective process.
        List<Rule> bypassRules = new ArrayList<Rule>();
        List<Rule> collectiveRules = new ArrayList<Rule>(rules.size());

        for (Rule rule : rules) {
            if (rule.supportsGroundingQueryRewriting()) {
                collectiveRules.add(rule);
            } else {
                bypassRules.add(rule);
            }
        }

        Set<CandidateQuery> candidates = genCandidates(collectiveRules, atomManager.getDatabase());

        Set<CandidateQuery> coverage = Coverage.compute(collectiveRules, candidates);

        long initialSize = groundRuleStore.size();

        // Ground the bypassed rules.
        groundIndependent(bypassRules, atomManager, groundRuleStore);

        // Ground the collective rules.
        for (CandidateQuery candidate : coverage) {
            // Multiple candidates may cover the same rule.
            // So, we need to track which rules still need to ground.

            Set<Rule> toGround = new HashSet<Rule>(collectiveRules);
            toGround.retainAll(candidate.getCoveredRules());

            sharedGrounding(candidate, toGround, atomManager, groundRuleStore);

            collectiveRules.removeAll(candidate.getCoveredRules());
        }

        return groundRuleStore.size() - initialSize;
    }

    private static Set<CandidateQuery> genCandidates(List<Rule> collectiveRules, Database database) {
        // TODO(eriq): Get from config.
        final int candiatesPerRule = 3;

        Set<CandidateQuery> candidates = Collections.synchronizedSet(new HashSet<CandidateQuery>());

        final CandidateGeneration candidateGeneration;
        if (!(database instanceof RDBMSDatabase)
                || !(((RDBMSDataStore)database.getDataStore()).getDriver() instanceof PostgreSQLDriver)) {
            log.warn("Cannot generate query candidates without a PostgreSQL database, grounding will be suboptimal.");
            candidateGeneration = null;
        } else {
            candidateGeneration = new CandidateGeneration();
        }

        if (candidateGeneration == null) {
            for (Rule rule : collectiveRules) {
                candidates.add(new CandidateQuery(rule, rule.getRewritableGroundingFormula(), 0.0));
            }

            return candidates;
        }

        final RDBMSDatabase finalDatabase = (RDBMSDatabase)database;
        final Set<CandidateQuery> finalCandidates = candidates;

        Parallel.foreach(collectiveRules, new Parallel.Worker<Rule>() {
            @Override
            public void work(long index, Rule rule) {
                candidateGeneration.generateCandidates(rule, finalDatabase, candiatesPerRule, finalCandidates);
            }
        });

        return candidates;
    }

    /**
     * Use the provided formula to ground all of the provided rules.
     */
    private static long sharedGrounding(CandidateQuery candidate, Set<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        log.debug("Grounding {} rule(s) with query: [{}].", rules.size(), candidate.getFormula());
        for (Rule rule : rules) {
            log.trace("    " + rule);
        }

        // We will manually handle these in the grounding process.
        // We do not want to throw too early because the ground rule may turn out to be trivial in the end.
        boolean oldAccessExceptionState = atomManager.enableAccessExceptions(false);

        // Run the query.
        QueryResultIterable queryResults = atomManager.executeGroundingQuery(candidate.getFormula());

        // Build a per-rule variable mapping.
        Map<Rule, Map<Variable, Integer>> variableMaps = new HashMap<Rule, Map<Variable, Integer>>();
        Map<Variable, Integer> baseVariableMap = queryResults.getVariableMap();

        for (Rule rule : rules) {
            if (rule == candidate.getBaseRule()) {
                variableMaps.put(rule, baseVariableMap);
            } else {
                Map<Variable, Integer> variableMap = new HashMap<Variable, Integer>();
                Map<Variable, Variable> containmentMapping = candidate.getVariableMapping(rule);

                for (Map.Entry<Variable, Integer> baseVariabelMapEntry : baseVariableMap.entrySet()) {
                    variableMap.put(containmentMapping.get(baseVariabelMapEntry.getKey()), baseVariabelMapEntry.getValue());
                }

                variableMaps.put(rule, variableMap);
            }
        }

        long initialCount = groundRuleStore.size();
        Parallel.RunTimings timings = Parallel.foreach(queryResults, new GroundWorker(atomManager, groundRuleStore, variableMaps, rules));
        long groundCount = groundRuleStore.size() - initialCount;

        atomManager.enableAccessExceptions(oldAccessExceptionState);

        log.debug("Generated {} ground rules from {} query results.", groundCount, timings.iterations);

        return groundCount;
    }

    private static class GroundWorker extends Parallel.Worker<Constant[]> {
        private AtomManager atomManager;
        private GroundRuleStore groundRuleStore;
        private Map<Rule, Map<Variable, Integer>> variableMaps;
        private Set<Rule> rules;
        private List<GroundRule> groundRules;

        public GroundWorker(AtomManager atomManager, GroundRuleStore groundRuleStore,
                Map<Rule, Map<Variable, Integer>> variableMaps, Set<Rule> rules) {
            this.atomManager = atomManager;
            this.groundRuleStore = groundRuleStore;
            this.variableMaps = variableMaps;
            this.rules = rules;
            this.groundRules = new ArrayList<GroundRule>();
        }

        @Override
        public Object clone() {
            return new GroundWorker(atomManager, groundRuleStore, variableMaps, rules);
        }

        @Override
        public void work(long index, Constant[] row) {
            for (Rule rule : rules) {
                rule.ground(row, variableMaps.get(rule), atomManager, groundRules);

                for (GroundRule groundRule : groundRules) {
                    if (groundRule != null) {
                        groundRuleStore.addGroundRule(groundRule);
                    }
                }

                groundRules.clear();
            }
        }
    }
}
