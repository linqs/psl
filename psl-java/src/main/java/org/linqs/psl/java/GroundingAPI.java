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
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.grounding.MemoryGroundRuleStore;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.parser.CommandLineLoader;

import java.util.List;
import java.util.Set;

/**
 * A static-only class that gives easy access to PSL's grounding functionality.
 *
 * TODO(eriq): There is a crazy amount of improvements that can be made here,
 * we will just leave this comment instead of pointing out everything.
 * Things like forced options and copied code (e.g. from the Launcher).
 */
public final class GroundingAPI {
    // Static only.
    public GroundingAPI() {}

    public static List<GroundRule> ground(List<Rule> rules, List<List<String>> observedData, List<List<String>> unobservedData) {
        DataStore dataStore = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk, CommandLineLoader.DEFAULT_H2_DB_PATH, true));

        Set<StandardPredicate> closedPredicates = loadData(dataStore, observedData, unobservedData);

        Partition targetPartition = dataStore.getPartition("targets");
        Partition observationsPartition = dataStore.getPartition("observations");
        Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);

        AtomManager atomManager = new PersistedAtomManager(database);
        GroundRuleStore groundRuleStore = new MemoryGroundRuleStore();

        long groundCount = Grounding.groundAll(rules, atomManager, groundRuleStore);

        return (List<GroundRule>)groundRuleStore.getGroundRules();
    }

    private static Set<StandardPredicate> loadData(DataStore dataStore,
            List<List<String>> observedData, List<List<String>> unobservedData) {
        // TEST
        return null;
    }

    // TEST
    public static void test() {
        System.out.println("TEST");
    }
}
