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
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.Formula2SQL;
import org.linqs.psl.database.rdbms.QueryRewriter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.RDBMSDatabase;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.formula.Formula;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Variable;
import org.linqs.psl.util.Parallel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
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
        // TODO(eriq): Get from config.
        int candiatesPerRule = 3;

        // Rules that cannot take part in the collective process.
        List<Rule> bypassRules = new ArrayList<Rule>();

        List<Rule> collectiveRules = new ArrayList<Rule>(rules.size());
        List<CandidateQuery> candidates = new ArrayList<CandidateQuery>(rules.size() * candiatesPerRule);

        if (!(atomManager.getDatabase() instanceof RDBMSDatabase)
                || !(((RDBMSDataStore)atomManager.getDatabase().getDataStore()).getDriver() instanceof PostgreSQLDriver)) {
            log.warn("Cannot generate query candidates without a PostgreSQL database, grounding will be suboptimal.");
        }

        for (Rule rule : rules) {
            if (!rule.supportsGroundingQueryRewriting()) {
                bypassRules.add(rule);
                continue;
            }
            collectiveRules.add(rule);

            generateCandidates(candidates, rule, atomManager);
        }

        long initialSize = groundRuleStore.size();

        List<CandidateQuery> coverage = computeCoverage(collectiveRules, candidates);

        // Ground the bypassed rules.
        groundIndependent(bypassRules, atomManager, groundRuleStore);

        // Ground the collective rules.
        for (CandidateQuery candidate : coverage) {
            // Multiple candidates may cover the same rule.
            // So, we need to track which rules still need to ground.

            Set<Rule> toGround = new HashSet<Rule>(collectiveRules);
            toGround.retainAll(candidate.coveredRules);

            sharedGrounding(candidate.formula, toGround, atomManager, groundRuleStore);

            collectiveRules.removeAll(candidate.coveredRules);
        }

        return groundRuleStore.size() - initialSize;
    }

    private static void generateCandidates(List<CandidateQuery> candidates, Rule rule, AtomManager atomManager) {
        Formula baseFormula = rule.getRewritableGroundingFormula();

        if (!(atomManager.getDatabase() instanceof RDBMSDatabase)
                || !(((RDBMSDataStore)atomManager.getDatabase().getDataStore()).getDriver() instanceof PostgreSQLDriver)) {
            // A warning has already been issued for this.
            candidates.add(new CandidateQuery(rule, baseFormula, 0.0));
            return;
        }

        RDBMSDatabase database = (RDBMSDatabase)atomManager.getDatabase();
        DatabaseDriver driver = ((RDBMSDataStore)database.getDataStore()).getDriver();

        // TODO(eriq): A real implementation.

        String query = Formula2SQL.getQuery(baseFormula, database, false);
        DatabaseDriver.ExplainResult explainResult = driver.explain(query);

        candidates.add(new CandidateQuery(rule, baseFormula, explainResult.totalCost));
        return;
    }

    private static List<CandidateQuery> computeCoverage(List<Rule> collectiveRules, List<CandidateQuery> candidates) {
        // TODO(eriq): Part of computing the coverage is computing containment (and containment mappings).
        //  These mappings are necessary for ground rule instantiation.

        // TODO(eriq): A real implementation.
        return candidates;
    }

    /**
     * Use the provided formula to ground all of the provided rules.
     */
    private static long sharedGrounding(Formula query, Set<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        log.debug("Grounding {} rule(s) with query: [{}].", rules.size(), query);
        for (Rule rule : rules) {
            log.trace("    " + rule);
        }

        // We will manually handle these in the grounding process.
        // We do not want to throw too early because the ground rule may turn out to be trivial in the end.
        boolean oldAccessExceptionState = atomManager.enableAccessExceptions(false);

        long initialCount = groundRuleStore.size();
        QueryResultIterable queryResults = atomManager.executeGroundingQuery(query);
        Parallel.RunTimings timings = Parallel.foreach(queryResults, new GroundWorker(atomManager, groundRuleStore, queryResults.getVariableMap(), rules));
        long groundCount = groundRuleStore.size() - initialCount;

        atomManager.enableAccessExceptions(oldAccessExceptionState);

        log.debug("Generated {} ground rules from {} query results.", groundCount, timings.iterations);

        return groundCount;
    }

    private static class CandidateQuery {
        public final Formula formula;
        public final double score;

        // Verified rules that this candidate can ground for.
        public final Set<Rule> coveredRules;

        // Verified rules that this candidate cannot ground for.
        public final Set<Rule> uncoveredRules;

        public CandidateQuery(Rule rule, Formula formula, double score) {
            this.formula = formula;
            this.score = score;

            coveredRules = new HashSet<Rule>();
            coveredRules.add(rule);

            uncoveredRules = new HashSet<Rule>();
        }
    }

    private static class GroundWorker extends Parallel.Worker<Constant[]> {
        private AtomManager atomManager;
        private GroundRuleStore groundRuleStore;
        private Map<Variable, Integer> variableMap;
        private Set<Rule> rules;
        private List<GroundRule> groundRules;

        public GroundWorker(AtomManager atomManager, GroundRuleStore groundRuleStore,
                Map<Variable, Integer> variableMap, Set<Rule> rules) {
            this.atomManager = atomManager;
            this.groundRuleStore = groundRuleStore;
            this.variableMap = variableMap;
            this.rules = rules;
            this.groundRules = new ArrayList<GroundRule>();
        }

        @Override
        public Object clone() {
            return new GroundWorker(atomManager, groundRuleStore, variableMap, rules);
        }

        @Override
        public void work(long index, Constant[] row) {
            for (Rule rule : rules) {
                rule.ground(row, variableMap, atomManager, groundRules);

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
