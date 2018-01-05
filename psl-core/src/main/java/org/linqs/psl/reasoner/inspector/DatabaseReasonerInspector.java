/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2018 The Regents of the University of California
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
package org.linqs.psl.reasoner.inspector;

import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.model.predicate.StandardPredicate;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * A ReasonerInspector that is prepared to look at the underlying database.
 *
 * This inspector makes the assuption that there is only one database (RDBMSDatabase) active
 * and there is a partition called "truth".
 */
public abstract class DatabaseReasonerInspector implements ReasonerInspector {
	// TODO(eriq): Use config
	public static final String DEFAULT_TRUTH_PARTITION_NAME = "truth";

	private String truthPartitionName;

	public DatabaseReasonerInspector(ConfigBundle config) {
		truthPartitionName = DEFAULT_TRUTH_PARTITION_NAME;
	}

	/**
	 * Get the only currently opened databse that is being used for reasoning.
	 */
	protected Database getRandomVariableDatabase() {
		Set<RDBMSDataStore> dataStores = RDBMSDataStore.getOpenDataStores();
		if (dataStores.size() != 1) {
			throw new IllegalStateException("Expected exactly one active datastore, found " + dataStores.size());
		}

		Collection<Database> databases = dataStores.iterator().next().getOpenDatabases();
		if (databases.size() != 1) {
			throw new IllegalStateException("Expected exactly one open database, found " + databases.size());
		}

		return databases.iterator().next();
	}

	/**
	 * Given the random variable database, get a matching truth database.
	 */
	protected Database getTruthDatabase(Database rvDatabase) {
		Partition truthPartition = rvDatabase.getDataStore().getPartition(truthPartitionName);

		// Collect all the open predicates.
		Set<StandardPredicate> openPredicates = new HashSet<StandardPredicate>();
		for (StandardPredicate predicate : rvDatabase.getRegisteredPredicates()) {
			if (!rvDatabase.isClosed(predicate)) {
				openPredicates.add(predicate);
			}
		}

		return rvDatabase.getDataStore().getDatabase(truthPartition, openPredicates);
	}
}
