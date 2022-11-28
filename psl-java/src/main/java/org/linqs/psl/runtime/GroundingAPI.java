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
package org.linqs.psl.runtime;

import org.linqs.psl.config.Config;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractGroundLogicalRule;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.sgd.term.SGDTermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;
import org.linqs.psl.util.Version;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A interface to PSL's grounding functionality.
 */
public final class GroundingAPI extends Runtime {
    private static final Logger log = Logger.getLogger(GroundingAPI.class);

    public static List<GroundRuleInfo> groundStatic(String configPath) {
        GroundingAPI api = new GroundingAPI();
        return api.ground(configPath);
    }

    public static List<GroundRuleInfo> groundStatic(RuntimeConfig config) {
        GroundingAPI api = new GroundingAPI();
        return api.ground(config);
    }

    public List<GroundRuleInfo> ground(String configPath) {
        RuntimeConfig config = RuntimeConfig.fromFile(configPath);
        return ground(config);
    }

    public List<GroundRuleInfo> ground(RuntimeConfig config) {
        Config.pushLayer();

        try {
            return groundInternal(config);
        } finally {
            Config.popLayer();
            cleanup();
        }
    }

    public List<GroundRuleInfo> groundInternal(RuntimeConfig config) {
        // Apply any top-level options found in the config.
        for (Map.Entry<String, String> entry : config.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        // Specially check if we need to re-init the logger.
        initLogger();

        log.info("PSL Grounding API Version {}", Version.getFull());
        config.validate();

        // Apply top-level options again after validation (since options may have been changed or added).
        for (Map.Entry<String, String> entry : config.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        List<Rule> rules = new ArrayList<Rule>();
        for (Rule rule : config.rules.getRules()) {
            rules.add(rule);
        }

        DataStore dataStore = initDataStore(config);
        loadData(dataStore, config, true);

        Set<StandardPredicate> closedPredicates = config.getClosedPredicates(true);

        Partition targetPartition = dataStore.getPartition(Runtime.PARTITION_NAME_TARGET);
        Partition observationsPartition = dataStore.getPartition(Runtime.PARTITION_NAME_OBSERVATIONS);

        Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        TermStore store = new SGDTermStore(database, true, false);

        final List<GroundRuleInfo> groundRules = new ArrayList<GroundRuleInfo>();
        Grounding.setGroundRuleCallback(new Grounding.GroundRuleCallback() {
            public synchronized void call(GroundRule groundRule) {
                groundRules.add(mapGroundRule(database.getAtomStore(), groundRule));
            }
        });

        Grounding.groundAll(rules, store);
        Grounding.setGroundRuleCallback(null);

        store.close();
        database.close();
        dataStore.close();

        return groundRules;
    }

    private GroundRuleInfo mapGroundRule(AtomStore store, GroundRule groundRule) {
        float weight = -1.0f;
        if (groundRule.getRule().isWeighted()) {
            weight = ((WeightedRule)groundRule.getRule()).getWeight();
        }

        if (groundRule instanceof AbstractGroundLogicalRule) {
            return mapGroundRule(store, (AbstractGroundLogicalRule)groundRule, weight);
        } else if (groundRule instanceof AbstractGroundArithmeticRule) {
            return mapGroundRule(store, (AbstractGroundArithmeticRule)groundRule, weight);
        }

        throw new IllegalStateException("Unknown rule type: " + groundRule.getClass());
    }

    private GroundRuleInfo mapGroundRule(AtomStore store, AbstractGroundLogicalRule groundRule, float weight) {
        int atomIndex = 0;
        float[] coefficients = new float[groundRule.size()];
        int[] atoms = new int[groundRule.size()];

        for (GroundAtom atom : groundRule.getPositiveAtoms()) {
            coefficients[atomIndex] = 1.0f;
            atoms[atomIndex] = store.getAtomIndex(atom);
            atomIndex++;
        }

        for (GroundAtom atom : groundRule.getNegativeAtoms()) {
            coefficients[atomIndex] = -1.0f;
            atoms[atomIndex] = store.getAtomIndex(atom);
            atomIndex++;
        }

        return new GroundRuleInfo("|", weight, 0.0f, coefficients, atoms);
    }

    private GroundRuleInfo mapGroundRule(AtomStore store, AbstractGroundArithmeticRule groundRule, float weight) {
        GroundAtom[] rawAtoms = groundRule.getOrderedAtoms();
        int[] atoms = new int[rawAtoms.length];

        for (int i = 0; i < rawAtoms.length; i++) {
            atoms[i] = store.getAtomIndex(rawAtoms[i]);
        }

        return new GroundRuleInfo(groundRule.getComparator().toString(), weight, groundRule.getConstant(),
                groundRule.getCoefficients(), atoms);
    }

    public static final class GroundRuleInfo {
        public String operator;
        public float weight;
        public float constant;
        public float[] coefficients;
        public int[] atoms;

        public GroundRuleInfo(String operator, float weight, float constant, float[] coefficients, int[] atoms) {
            this.operator = operator;
            this.weight = weight;
            this.constant = constant;
            this.coefficients = coefficients;
            this.atoms = atoms;
        }

        public String toString() {
            return String.format(
                    "Rule Type: %s, Weight: %f, Constant: %f, coefficients: [%s], atoms: [%s].",
                    operator, weight, constant,
                    StringUtils.join(", ", coefficients), StringUtils.join(", ", atoms));
        }
    }

    public static void main(String[] args) {
        if (args == null || args.length != 1) {
            System.out.println("USAGE: " + GroundingAPI.class + " <path to JSON config>");
            return;
        }

        GroundingAPI.groundStatic(args[0]);
    }
}
