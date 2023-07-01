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
package org.linqs.psl.runtime;

import org.linqs.psl.config.Config;
import org.linqs.psl.config.Options;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.database.AtomStore;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.atom.ObservedAtom;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.WeightedRule;
import org.linqs.psl.model.rule.arithmetic.AbstractGroundArithmeticRule;
import org.linqs.psl.model.rule.logical.AbstractGroundLogicalRule;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.model.term.Term;
import org.linqs.psl.reasoner.term.TermStore;
import org.linqs.psl.reasoner.term.DummyTermStore;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.StringUtils;
import org.linqs.psl.util.Version;

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A interface to PSL's grounding functionality.
 */
public final class GroundingAPI extends Runtime {
    private static final Logger log = Logger.getLogger(GroundingAPI.class);

    public static GroundProgram groundStatic(String configPath) {
        GroundingAPI api = new GroundingAPI();
        return api.ground(configPath);
    }

    public static GroundProgram groundStatic(RuntimeConfig config) {
        GroundingAPI api = new GroundingAPI();
        return api.ground(config);
    }

    /**
     * A static interface specifically meant for methods that provide serialized input and want serialized output
     * (both in the form of JSON).
     */
    public static String serializedGround(String jsonConfig, String basePath) {
        RuntimeConfig config = RuntimeConfig.fromJSON(jsonConfig, basePath);
        GroundProgram program = groundStatic(config);
        return program.toJSON();
    }

    public GroundProgram ground(String configPath) {
        RuntimeConfig config = RuntimeConfig.fromFile(configPath);
        return ground(config);
    }

    public GroundProgram ground(RuntimeConfig config) {
        Config.pushLayer();

        try {
            return groundInternal(config);
        } finally {
            Config.popLayer();
            cleanup();
        }
    }

    private GroundProgram groundInternal(RuntimeConfig config) {
        // Apply any top-level options found in the config.
        for (Map.Entry<String, String> entry : config.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        // Specially check if we need to re-init the logger.
        initLogger();

        log.info("PSL Grounding API Version {}", Version.getFull());
        config.validate();

        // Ensure that all atoms are stored (unless overwritten).
        Options.ATOM_STORE_STORE_ALL_ATOMS.set(true);

        // Apply top-level options again after validation (since options may have been changed or added).
        for (Map.Entry<String, String> entry : config.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        List<Rule> rules = new ArrayList<Rule>();
        for (Rule rule : config.rules.getRules()) {
            rules.add(rule);
        }

        DataStore dataStore = initDataStore(config);
        loadData(dataStore, config, RuntimeConfig.KEY_INFER);

        Set<StandardPredicate> closedPredicates = config.getClosedPredicates(RuntimeConfig.KEY_INFER);

        Partition targetPartition = dataStore.getPartition(Runtime.PARTITION_NAME_TARGET);
        Partition observationsPartition = dataStore.getPartition(Runtime.PARTITION_NAME_OBSERVATIONS);

        Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        AtomStore atomStore = database.getAtomStore();
        TermStore store = new DummyTermStore(database.getAtomStore());

        final List<GroundRuleInfo> groundRules = new ArrayList<GroundRuleInfo>();

        Map<Integer, AtomInfo> groundAtoms = null;
        if (!RuntimeOptions.OUTPUT_ALL_ATOMS.getBoolean()) {
            groundAtoms = new HashMap<Integer, AtomInfo>();
        }

        final Map<Integer, AtomInfo> finalGroundAtoms = groundAtoms;
        Grounding.setGroundRuleCallback(new Grounding.GroundRuleCallback() {
            public synchronized void call(GroundRule groundRule) {
                groundRules.add(mapGroundRule(rules.indexOf(groundRule.getRule()), atomStore, groundRule, finalGroundAtoms));
            }
        });

        Grounding.groundAll(rules, store, database);
        Grounding.setGroundRuleCallback(null);

        if (groundAtoms == null) {
            groundAtoms = new HashMap<Integer, AtomInfo>(atomStore.size());
            for (GroundAtom groundAtom : atomStore) {
                groundAtoms.put(Integer.valueOf(groundAtom.getIndex()), new AtomInfo(groundAtom));
            }
        }

        store.close();
        database.close();
        dataStore.close();

        return new GroundProgram(groundAtoms, groundRules);
    }

    private GroundRuleInfo mapGroundRule(int ruleIndex, AtomStore store, GroundRule groundRule, Map<Integer, AtomInfo> usedAtoms) {
        float weight = -1.0f;
        if (groundRule.getRule().isWeighted()) {
            weight = ((WeightedRule)groundRule.getRule()).getWeight();
        }

        if (groundRule instanceof AbstractGroundLogicalRule) {
            return mapGroundRule(ruleIndex, store, (AbstractGroundLogicalRule)groundRule, weight, usedAtoms);
        } else if (groundRule instanceof AbstractGroundArithmeticRule) {
            return mapGroundRule(ruleIndex, store, (AbstractGroundArithmeticRule)groundRule, weight, usedAtoms);
        }

        throw new IllegalStateException("Unknown rule type: " + groundRule.getClass());
    }

    private GroundRuleInfo mapGroundRule(int ruleIndex, AtomStore store, AbstractGroundLogicalRule groundRule, float weight,
            Map<Integer, AtomInfo> usedAtoms) {
        int currentAtom = 0;
        float[] coefficients = new float[groundRule.size()];
        int[] atoms = new int[groundRule.size()];

        // Remember: the negated DNF is tracked, so invert all coefficients.

        for (GroundAtom atom : groundRule.getPositiveAtoms()) {
            coefficients[currentAtom] = -1.0f;

            int atomIndex = store.getAtomIndex(atom);
            atoms[currentAtom] = atomIndex;
            currentAtom++;

            if (usedAtoms != null) {
                Integer key = Integer.valueOf(atomIndex);
                if (!usedAtoms.containsKey(key)) {
                    usedAtoms.put(key, new AtomInfo(atom));
                }
            }
        }

        for (GroundAtom atom : groundRule.getNegativeAtoms()) {
            coefficients[currentAtom] = 1.0f;

            int atomIndex = store.getAtomIndex(atom);
            atoms[currentAtom] = atomIndex;
            currentAtom++;

            if (usedAtoms != null) {
                Integer key = Integer.valueOf(atomIndex);
                if (!usedAtoms.containsKey(key)) {
                    usedAtoms.put(key, new AtomInfo(atom));
                }
            }
        }

        return new GroundRuleInfo(ruleIndex, "|", weight, 0.0f, coefficients, atoms);
    }

    private GroundRuleInfo mapGroundRule(int ruleIndex, AtomStore store, AbstractGroundArithmeticRule groundRule, float weight,
            Map<Integer, AtomInfo> usedAtoms) {
        GroundAtom[] rawAtoms = groundRule.getOrderedAtoms();
        int[] atoms = new int[rawAtoms.length];

        for (int i = 0; i < rawAtoms.length; i++) {
            int atomIndex = store.getAtomIndex(rawAtoms[i]);
            atoms[i] = atomIndex;

            if (usedAtoms != null) {
                Integer key = Integer.valueOf(atomIndex);
                if (!usedAtoms.containsKey(key)) {
                    usedAtoms.put(key, new AtomInfo(rawAtoms[i]));
                }
            }
        }

        return new GroundRuleInfo(ruleIndex, groundRule.getComparator().toString(), weight, groundRule.getConstant(),
                groundRule.getCoefficients(), atoms);
    }

    public static final class GroundProgram {
        public Map<Integer, AtomInfo> atoms;
        public List<GroundRuleInfo> groundRules;

        public GroundProgram(Map<Integer, AtomInfo> atoms, List<GroundRuleInfo> groundRules) {
            this.atoms = atoms;
            this.groundRules = groundRules;
        }

        @Override
        public String toString() {
            return toJSON();
        }

        public String toJSON() {
            ObjectMapper mapper = new ObjectMapper();

            mapper.enable(SerializationFeature.INDENT_OUTPUT);
            mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

            DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("    ", "\n"));

            try {
                return mapper.writer(printer).writeValueAsString(this);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    public static final class AtomInfo {
        public String predicate;
        public String[] arguments;
        public float value;
        public boolean observed;

        public AtomInfo(GroundAtom atom) {
            predicate = atom.getPredicate().getName();
            value = atom.getValue();
            observed = (atom instanceof ObservedAtom);

            arguments = new String[atom.getArity()];
            Term[] terms = atom.getArguments();
            for (int i = 0; i < terms.length; i++) {
                arguments[i] = ((Constant)terms[i]).rawToString();
            }
        }
    }

    public static final class GroundRuleInfo {
        public int ruleIndex;
        public String operator;
        public float weight;
        public float constant;
        public float[] coefficients;
        public int[] atoms;

        public GroundRuleInfo(int ruleIndex, String operator, float weight, float constant, float[] coefficients, int[] atoms) {
            this.ruleIndex = ruleIndex;
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

        GroundProgram program = GroundingAPI.groundStatic(args[0]);
        System.out.println(program);
    }
}
