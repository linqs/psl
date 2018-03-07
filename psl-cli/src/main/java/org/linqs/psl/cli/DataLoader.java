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
package org.linqs.psl.cli;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * @author jay
 */
public class DataLoader {
	private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

	@SuppressWarnings("unchecked")
	private static Set<StandardPredicate> definePredicates(DataStore datastore, Map yamlMap, boolean useIntIds) {
		if (!yamlMap.containsKey("predicates")) {
			throw new IllegalArgumentException("No 'predicates' block defined in data specification");
		}

		Set<StandardPredicate> closed = new HashSet<StandardPredicate>();

		for (Entry<String, String> predicateSpec : ((Map<String,String>)yamlMap.get("predicates")).entrySet()) {
			// parse the predicate/args part
			String[] predicateParts = predicateSpec.getKey().split("/", 2);
			if (predicateParts.length < 2) {
				throw new IllegalArgumentException("Improperly specified predicate " + predicateSpec.getKey());
			}

			String predicateStr = predicateParts[0];
			int arity = Integer.parseInt(predicateParts[1]);
			log.debug("Found predicate {} with arity {}", predicateStr, arity);

			// create a predicate and add it to the datastore
			ConstantType[] args = new ConstantType[arity];
			for (int i = 0; i < arity; i++) {
				if (useIntIds) {
					args[i] = ConstantType.UniqueIntID;
				} else {
					args[i] = ConstantType.UniqueStringID;
				}
			}
			StandardPredicate predicate = StandardPredicate.get(predicateStr, args);
			datastore.registerPredicate(predicate);

			// check if closed
			if (predicateSpec.getValue().equalsIgnoreCase("closed")) {
				closed.add(predicate);
			}
		}

		return closed;
	}

	@SuppressWarnings("unchecked")
	private static void loadDataFiles(DataStore datastore, Map yamlMap) {
		for (String partitionName : ((Map<String,Object>) yamlMap).keySet()) {
			// Skip special partition predicates
			if (partitionName.equalsIgnoreCase("predicates")) {
				continue;
			}

			// Skip special information that PSL does not care about.
			if (partitionName.equalsIgnoreCase("PredicateDetails")) {
				continue;
			}

			// Find files to load into this partition.
			Partition partition = datastore.getPartition(partitionName);

			for (Entry<String,Object> loadSpec : ((Map<String,Object>)yamlMap.get(partitionName)).entrySet()) {
				log.debug("Loading data for {} ({} partition)", loadSpec.getKey(), partitionName);

				StandardPredicate predicate = StandardPredicate.get(loadSpec.getKey());
				Inserter insert = datastore.getInserter(predicate, partition);

				if (loadSpec.getValue() instanceof String) {
					insert.loadDelimitedDataAutomatic((String)loadSpec.getValue());
				} else if (loadSpec.getValue() instanceof List) {
					for (String filename : ((List<String>)loadSpec.getValue())) {
						insert.loadDelimitedDataAutomatic(filename);
					}
				} else {
					throw new IllegalArgumentException("Unknown specification when loading " + partitionName);
				}
			}
		}
	}

	/**
	 * Loads a YAML-formatted data specification into a datastore
	 * The YAML input should use key-value formatting where keys
	 * correspond to predicate specification or data partitions
	 *
	 * @param datastore the datastore where data will be loaded
	 * @param inputStream YAML-formatted input for predicate and data definitions
	 * @return The set of closed predicates.
	 */
	public static Set<StandardPredicate> load(DataStore datastore, InputStream inputStream, boolean useIntIds) {
		Yaml yaml = new Yaml();
		Map yamlParse = (Map)yaml.load(inputStream);
		Set<StandardPredicate> closedPredicates = definePredicates(datastore, yamlParse, useIntIds);
		loadDataFiles(datastore, yamlParse);

		return closedPredicates;
	}
}
