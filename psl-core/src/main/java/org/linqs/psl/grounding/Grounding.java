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
package org.linqs.psl.grounding;

import org.linqs.psl.config.Options;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.QueryResultIterable;
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
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;

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
    private static final Logger log = Logger.getLogger(Grounding.class);

    private static GroundRuleCallback groundRuleCallback = null;

    // Static only.
    private Grounding() {}

    public static void setGroundRuleCallback(GroundRuleCallback groundRuleCallback) {
        Grounding.groundRuleCallback = groundRuleCallback;
    }

    public static long groundAll(List<Rule> rules, TermStore termStore, Database database) {
        boolean collective = Options.GROUNDING_COLLECTIVE.getBoolean();
        if (collective) {
            return groundCollective(rules, termStore, database);
        }

        return groundIndependent(rules, termStore, database);
    }

    /**
     * Ground each of the passed in rules independently.
     */
    private static long groundIndependent(List<Rule> rules, TermStore termStore, Database database) {
        long termCount = 0;
        for (Rule rule : rules) {
            termCount += rule.groundAll(termStore, database, groundRuleCallback);
        }

        return termCount;
    }

    /**
     * Ground all the given rules collectively.
     * Note that collective grounding assumes that no PAM exceptions will happen,
     * so it may make optimizations based on this assumption.
     */
    private static long groundCollective(List<Rule> rules, TermStore termStore, Database database) {
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

        Set<CandidateQuery> candidates = genCandidates(collectiveRules, database);

        Set<CandidateQuery> coverage = Coverage.compute(collectiveRules, candidates);

        long initialSize = termStore.size();

        // Ground the bypassed rules.
        groundIndependent(bypassRules, termStore, database);

        int batchSize = Options.GROUNDING_COLLECTIVE_BATCH_SIZE.getInt();

        // Ground the collective rules.
        for (CandidateQuery candidate : coverage) {
            // Multiple candidates may cover the same rule.
            // So, we need to track which rules still need to ground.

            Set<Rule> toGround = new HashSet<Rule>(collectiveRules);
            toGround.retainAll(candidate.getCoveredRules());

            sharedGrounding(candidate, toGround, termStore, database, batchSize);

            collectiveRules.removeAll(candidate.getCoveredRules());
        }

        return termStore.size() - initialSize;
    }

    private static Set<CandidateQuery> genCandidates(List<Rule> collectiveRules, Database database) {
        Set<CandidateQuery> candidates = Collections.synchronizedSet(new HashSet<CandidateQuery>());

        if (!database.getDataStore().canExplain()) {
            log.warn("Cannot generate query candidates without EXPLAIN capabilities, grounding will be suboptimal.");
            for (Rule rule : collectiveRules) {
                candidates.add(new CandidateQuery(rule, rule.getRewritableGroundingFormula(), 0.0));
            }

            return candidates;
        }

        final CandidateGeneration candidateGeneration = new CandidateGeneration();
        final int candiatesPerRule = Options.GROUNDING_COLLECTIVE_CANDIDATE_COUNT.getInt();

        final Database finalDatabase = database;
        final Set<CandidateQuery> finalCandidates = candidates;

        log.debug("Generating candidates.");

        Parallel.RunTimings timings = Parallel.foreach(collectiveRules, new Parallel.Worker<Rule>() {
            @Override
            public void work(long index, Rule rule) {
                candidateGeneration.generateCandidates(rule, finalDatabase, candiatesPerRule, finalCandidates);
            }
        });

        log.debug("Generated {} candidates", candidates.size());
        log.trace("    " + timings);

        return candidates;
    }

    /**
     * Use the provided formula to ground all of the provided rules.
     */
    private static long sharedGrounding(CandidateQuery candidate, Set<Rule> rules, TermStore termStore, Database database, int batchSize) {
        log.debug("Grounding {} rule(s) with query: [{}].", rules.size(), candidate.getFormula());
        for (Rule rule : rules) {
            log.trace("    " + rule);
        }

        Parallel.RunTimings timings = null;
        long termCount = -1;

        // Run the query.
        try (QueryResultIterable queryResults = database.executeGroundingQuery(candidate.getFormula())) {
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

            long initialCount = termStore.size();
            timings = Parallel.foreachBatch(queryResults, batchSize, new GroundWorker(termStore, database, variableMaps, rules));
            termCount = termStore.size() - initialCount;
        }

        log.debug("Generated {} terms from {} query results.", termCount, timings.iterations);
        log.trace("   " + timings);

        return termCount;
    }

    private static class GroundWorker extends Parallel.Worker<List<Constant[]>> {
        private TermStore termStore;
        private Database database;
        private Map<Rule, Map<Variable, Integer>> variableMaps;
        private Set<Rule> rules;
        private List<GroundRule> groundRules;

        public GroundWorker(TermStore termStore, Database database, Map<Rule, Map<Variable, Integer>> variableMaps, Set<Rule> rules) {
            this.termStore = termStore;
            this.database = database;
            this.variableMaps = variableMaps;
            this.rules = rules;
            this.groundRules = new ArrayList<GroundRule>();
        }

        @Override
        public Object clone() {
            return new GroundWorker(termStore, database, variableMaps, rules);
        }

        @Override
        public void work(long size, List<Constant[]> batch) {
            GroundRule groundRule = null;

            for (Rule rule : rules) {
                for (int rowIndex = 0; rowIndex < size; rowIndex++) {
                    rule.ground(batch.get(rowIndex), variableMaps.get(rule), database, groundRules);

                    for (int groundRuleIndex = 0; groundRuleIndex < groundRules.size(); groundRuleIndex++) {
                        groundRule = groundRules.get(groundRuleIndex);
                        if (groundRule != null) {
                            termStore.add(groundRule);

                            if (groundRuleCallback != null) {
                                groundRuleCallback.call(groundRule);
                            }
                        }
                    }

                    groundRules.clear();
                }
            }

            ((QueryResultIterable)source).reuse(batch);
        }
    }

    /**
     * An optional callback for each ground rule.
     */
    public static interface GroundRuleCallback {
        public void call(GroundRule groundRule);
    }
}
