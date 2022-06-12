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

    public static List<GroundRule> ground(
            String[] ruleStrings,
            String[] predicateNames, int[] predicateArities,
            String[][][] observedData, String[][][] unobservedData) {
        initLogger("WARNING");

        assert(predicateNames.length == observedData.length);
        assert(predicateNames.length == unobservedData.length);

        DataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, CommandLineLoader.DEFAULT_H2_DB_PATH, true));

        registerPredicates(predicateNames, predicateArities, dataStore);

        List<Rule> rules = new ArrayList<Rule>(ruleStrings.length);
        for (String ruleString : ruleStrings) {
            rules.add(ModelLoader.loadRule(ruleString));
        }

        Set<StandardPredicate> closedPredicates = loadData(dataStore, predicateNames, observedData, unobservedData);

        Partition targetPartition = dataStore.getPartition(PARTITION_UNOBS);
        Partition observationsPartition = dataStore.getPartition(PARTITION_OBS);
        Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);

        AtomManager atomManager = new PersistedAtomManager(database);
        GroundRuleStore groundRuleStore = new MemoryGroundRuleStore();

        long groundCount = Grounding.groundAll(rules, atomManager, groundRuleStore);

        return (List<GroundRule>)groundRuleStore.getGroundRules();
    }

    private static void registerPredicates(String[] predicateNames, int[] predicateArities, DataStore dataStore) {
        for (int i = 0; i < predicateNames.length; i++) {
            ConstantType[] types = new ConstantType[predicateArities[i]];
            for (int j = 0; j < types.length; j++) {
                types[j] = ConstantType.UniqueStringID;
            }

            StandardPredicate predicate = StandardPredicate.get(predicateNames[i], types);
            dataStore.registerPredicate(predicate);
        }
    }

    private static Set<StandardPredicate> loadData(
            DataStore dataStore, String[] predicateNames,
            String[][][] observedData, String[][][] unobservedData) {
        Set<StandardPredicate> observedPredicates = loadPartition(dataStore, PARTITION_OBS, predicateNames, observedData);
        Set<StandardPredicate> unobservedPredicates = loadPartition(dataStore, PARTITION_UNOBS, predicateNames, unobservedData);

        observedPredicates.removeAll(unobservedPredicates);

        return observedPredicates;
    }

    private static Set<StandardPredicate> loadPartition(DataStore dataStore, String partitionName,
            String[] predicateNames, String[][][] data) {
        Set<StandardPredicate> usedPredicates = new HashSet<StandardPredicate>(predicateNames.length);
        Partition partition = dataStore.getPartition(partitionName);

        for (int predicateIndex = 0; predicateIndex < predicateNames.length; predicateIndex++) {
            if (data[predicateIndex].length == 0) {
                continue;
            }

            StandardPredicate predicate = StandardPredicate.get(predicateNames[predicateIndex]);
            usedPredicates.add(predicate);

            Inserter inserter = dataStore.getInserter(predicate, partition);

            List<Object> insertBuffer = new ArrayList<Object>(predicate.getArity());
            for (int i = 0; i < predicate.getArity(); i++) {
                insertBuffer.add(null);
            }

            for (String[] row : data[predicateIndex]) {
                for (int i = 0; i < predicate.getArity(); i++) {
                    insertBuffer.set(i, row[i]);
                }

                if (row.length == predicate.getArity()) {
                    inserter.insert(insertBuffer);
                } else {
                    double truthValue = Double.parseDouble(row[predicate.getArity()]);
                    inserter.insertValue(truthValue, insertBuffer);
                }
            }
        }

        return usedPredicates;
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
