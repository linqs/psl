/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.config.Config;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.QueryResultIterable;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.rdbms.QueryRewriter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Static utilities for common {@link Model}-grounding tasks.
 */
public class Grounding {
    private static final Logger log = LoggerFactory.getLogger(Grounding.class);

    public static final String CONFIG_PREFIX = "grounding";

    /**
     * Potentially rewrite the grounding queries.
     */
    public static final String REWRITE_QUERY_KEY = CONFIG_PREFIX + ".rewritequeries";
    public static final boolean REWRITE_QUERY_DEFAULT = false;

    /**
     * Whether or not queries are being rewritten, perform the grounding queries one at a time.
     */
    public static final String SERIAL_KEY = CONFIG_PREFIX + ".serial";
    public static final boolean SERIAL_DEFAULT = false;

    // Static only.
    private Grounding() {}

    /**
     * Ground all the given rules.
     * @return the number of ground rules generated.
     */
    public static int groundAll(Model model, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        return groundAll(model.getRules(), atomManager, groundRuleStore);
    }

    /**
     * Ground all the given rules one at a time.
     * Callers should prefer groundAll() to this since it will perform a more efficient grounding.
     * @return the number of ground rules generated.
     */
    public static int groundAllSerial(List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        int groundCount = 0;
        for (Rule rule : rules) {
            groundCount += rule.groundAll(atomManager, groundRuleStore);
        }

        return groundCount;
    }

    /**
     * Ground all the given rules.
     * @return the number of ground rules generated.
     */
    public static int groundAll(List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        boolean rewrite = Config.getBoolean(REWRITE_QUERY_KEY, REWRITE_QUERY_DEFAULT);
        boolean serial = Config.getBoolean(SERIAL_KEY, SERIAL_DEFAULT);

        Map<Formula, List<Rule>> queries = new HashMap<Formula, List<Rule>>();
        List<Rule> bypassRules = new ArrayList<Rule>();

        DataStore dataStore = atomManager.getDatabase().getDataStore();
        if (rewrite && !(dataStore instanceof RDBMSDataStore)) {
            log.warn("Cannot rewrite queries with a non-RDBMS DataStore. Queries will not be rewritten.");
            rewrite = false;
        }

        QueryRewriter rewriter = null;
        if (rewrite) {
            rewriter = new QueryRewriter();
        }

        for (Rule rule : rules) {
            if (!rule.supportsGroundingQueryRewriting()) {
                bypassRules.add(rule);
                continue;
            }

            Formula query = rule.getRewritableGroundingFormula(atomManager);
            if (rewrite) {
                query = rewriter.rewrite(query, (RDBMSDataStore)dataStore);
            }

            if (!queries.containsKey(query)) {
                queries.put(query, new ArrayList<Rule>());
            }

            queries.get(query).add(rule);
        }

        int initialSize = groundRuleStore.size();

        // First perform all the rewritten querties.
        for (Map.Entry<Formula, List<Rule>> entry : queries.entrySet()) {
            if (!serial) {
                // If parallel, ground all the rules that match this formula at once.
                groundParallel(entry.getKey(), entry.getValue(), atomManager, groundRuleStore);
            } else {
                // If serial, ground the rules with this formula one at a time.
                for (Rule rule : entry.getValue()) {
                    List<Rule> tempRules = new ArrayList<Rule>();
                    tempRules.add(rule);

                    groundParallel(entry.getKey(), tempRules, atomManager, groundRuleStore);
                }
            }
        }

        // Now ground the bypassed rules.
        groundAllSerial(bypassRules, atomManager, groundRuleStore);

        return groundRuleStore.size() - initialSize;
    }

    private static int groundParallel(Formula query, List<Rule> rules, AtomManager atomManager, GroundRuleStore groundRuleStore) {
        log.debug("Grounding {} rule(s) with query: [{}].", rules.size(), query);
        for (Rule rule : rules) {
            log.trace("    " + rule);
        }

        // We will manually handle these in the grounding process.
        // We do not want to throw too early because the ground rule may turn out to be trivial in the end.
        boolean oldAccessExceptionState = atomManager.enableAccessExceptions(false);

        int initialCount = groundRuleStore.size();
        QueryResultIterable queryResults = atomManager.executeGroundingQuery(query);
        Parallel.RunTimings timings = Parallel.foreach(queryResults, new GroundWorker(atomManager, groundRuleStore, queryResults.getVariableMap(), rules));
        int groundCount = groundRuleStore.size() - initialCount;

        atomManager.enableAccessExceptions(oldAccessExceptionState);

        log.trace("Got {} results from query [{}].", timings.iterations, query);
        log.debug("Generated {} ground rules with query: [{}].", groundCount, query);
        return groundCount;
    }

    private static class GroundWorker extends Parallel.Worker<Constant[]> {
        private AtomManager atomManager;
        private GroundRuleStore groundRuleStore;
        private Map<Variable, Integer> variableMap;
        private List<Rule> rules;
        private List<GroundRule> groundRules;

        public GroundWorker(AtomManager atomManager, GroundRuleStore groundRuleStore,
                Map<Variable, Integer> variableMap, List<Rule> rules) {
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
        public void work(int index, Constant[] row) {
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
