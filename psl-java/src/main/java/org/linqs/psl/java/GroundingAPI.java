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
package org.linqs.psl.java;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.atom.AtomManager;
import org.linqs.psl.database.atom.PersistedAtomManager;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.parser.CommandLineLoader;
import org.linqs.psl.parser.ModelLoader;

import org.apache.log4j.PropertyConfigurator;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * A static-only class that gives easy access to PSL's grounding functionality.
 *
 * TODO(eriq): There is a crazy amount of improvements that can be made here,
 * we will just leave this comment instead of pointing out everything.
 * Things like forced options and copied code (e.g. from the Launcher).
 */
public final class GroundingAPI {
    public static final String PARTITION_OBS = "observed";
    public static final String PARTITION_UNOBS = "unobserved";

    // Static only.
    public GroundingAPI() {}

    public static List<GroundRule> ground(List<String> predicateNames, List<Integer> predicateArities,
            List<String> ruleStrings,
            Map<String, List<List<String>>> observedData, Map<String, List<List<String>>> unobservedData) {
        initLogger("ERROR");

        assert(predicateNames.size() == predicateArities.size());

        DataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, CommandLineLoader.DEFAULT_H2_DB_PATH, true));

        registerPredicates(predicateNames, predicateArities, dataStore);

        List<Rule> rules = new ArrayList<Rule>(ruleStrings.size());
        for (String ruleString : ruleStrings) {
            rules.add(ModelLoader.loadRule(ruleString));
        }

        Set<StandardPredicate> closedPredicates = loadData(dataStore, observedData, unobservedData);

        Partition targetPartition = dataStore.getPartition(PARTITION_UNOBS);
        Partition observationsPartition = dataStore.getPartition(PARTITION_OBS);
        Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);

        AtomManager atomManager = new PersistedAtomManager(database);
        GroundRuleStore groundRuleStore = new MemoryGroundRuleStore();

        long groundCount = Grounding.groundAll(rules, atomManager, groundRuleStore);

        return (List<GroundRule>)groundRuleStore.getGroundRules();
    }

    private static void registerPredicates(List<String> predicateNames, List<Integer> predicateArities, DataStore dataStore) {
        for (int i = 0; i < predicateNames.size(); i++) {
            ConstantType[] types = new ConstantType[predicateArities.get(i).intValue()];
            for (int j = 0; j < types.length; j++) {
                types[j] = ConstantType.UniqueStringID;
            }

            StandardPredicate predicate = StandardPredicate.get(predicateNames.get(i), types);
            dataStore.registerPredicate(predicate);
        }
    }

    private static Set<StandardPredicate> loadData(DataStore dataStore,
            Map<String, List<List<String>>> observedData, Map<String, List<List<String>>> unobservedData) {
        loadPartition(dataStore, PARTITION_OBS, observedData);
        loadPartition(dataStore, PARTITION_UNOBS, unobservedData);

        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>();
        for (String predicateName : observedData.keySet()) {
            if (unobservedData.containsKey(predicateName)) {
                continue;
            }

            closedPredicates.add(StandardPredicate.get(predicateName));
        }

        return closedPredicates;
    }

    private static void loadPartition(DataStore dataStore, String partitionName,
            Map<String, List<List<String>>> data) {
        Partition partition = dataStore.getPartition(partitionName);

        for (String predicateName : data.keySet()) {
            StandardPredicate predicate = StandardPredicate.get(predicateName);
            Inserter inserter = dataStore.getInserter(predicate, partition);

            List<Object> insertBuffer = new ArrayList<Object>(predicate.getArity());
            for (int i = 0; i < predicate.getArity(); i++) {
                insertBuffer.add(null);
            }

            for (List<String> row : data.get(predicateName)) {
                for (int i = 0; i < predicate.getArity(); i++) {
                    insertBuffer.set(i, row.get(i));
                }

                if (row.size() == predicate.getArity()) {
                    inserter.insert(insertBuffer);
                } else {
                    double truthValue = Double.parseDouble(row.get(row.size() - 1));
                    inserter.insertValue(truthValue, insertBuffer);
                }
            }
        }
    }

    // Init a defualt logger with the given level.
    public static void initLogger(String logLevel) {
        Properties props = new Properties();

        props.setProperty("log4j.rootLogger", String.format("%s, A1", logLevel));
        props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
        props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
        props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");

        PropertyConfigurator.configure(props);
    }
}
