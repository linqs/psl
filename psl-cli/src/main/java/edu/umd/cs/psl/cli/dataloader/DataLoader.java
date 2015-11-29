/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2015 The Regents of the University of California
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
package edu.umd.cs.psl.cli.dataloader;

import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.model.term.ConstantType;
import edu.umd.cs.psl.ui.loading.InserterUtils;

/**
 * @author jay
 *
 */
public class DataLoader {
	private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

	private static Set<StandardPredicate> definePredicates(DataStore datastore, Map yamlMap) {
		if(!yamlMap.containsKey("predicates")){
			throw new IllegalArgumentException("No 'predicates' block defined in data specification");			
		}
		Set<StandardPredicate> closed = new HashSet<StandardPredicate>();
		PredicateFactory pf = PredicateFactory.getFactory();
		for (Entry<String, String> predicateSpec : ((Map<String,String>)yamlMap.get("predicates")).entrySet()){

			//parse the predicate/args part
			String[] predicateParts = predicateSpec.getKey().split("/",2);
			if(predicateParts.length < 2){
				throw new IllegalArgumentException("Improperly specified predicate "+predicateSpec.getKey());
			}
			String predicateStr = predicateParts[0];
			int arity = Integer.parseInt(predicateParts[1]);
			log.debug("Found predicate {} with arity {}",predicateStr, arity);

			//create a predicate and add it to the datastore
			ConstantType[] args = new ConstantType[arity];
			for(int i = 0; i < arity; i++){
				args[i] = ConstantType.UniqueID;
			}
			StandardPredicate predicate = pf.createStandardPredicate(predicateStr, args);
			datastore.registerPredicate(predicate);

			//check if closed
			if(predicateSpec.getValue().equals("closed")){
				closed.add(predicate);
			}

		}
		return closed;
	}

	private static void loadDataFiles(DataStore datastore, Map yamlMap) {
		for (String partitionName : ((Map<String,Object>) yamlMap).keySet()){
			//skip special partition predicates
			if(partitionName.equals("predicates")){
				continue;
			}

			PredicateFactory pf = PredicateFactory.getFactory();

			//find files to load into this partition
			Partition p = datastore.getPartition(partitionName); 

			for( Entry<String,Object> loadSpec : ((Map<String,Object>) yamlMap.get(partitionName)).entrySet() ) {
				StandardPredicate predicate = (StandardPredicate) pf.getPredicate(loadSpec.getKey());
				Inserter insert = datastore.getInserter(predicate, p);
				if(loadSpec.getValue() instanceof String){
					InserterUtils.loadDelimitedDataAutomatic(predicate, insert, (String) loadSpec.getValue());
				} else if (loadSpec.getValue() instanceof List){
					for(String filename : ((List<String>)loadSpec.getValue())){
						InserterUtils.loadDelimitedDataAutomatic(predicate, insert, filename);
					}
				} else {
					throw new IllegalArgumentException("Unknown specification when loading "+partitionName);
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
	 * @return DataLoaderOutput with data loading results, including closed predicates
	 * @throws Exception
	 */
	public static DataLoaderOutput load(DataStore datastore, InputStream inputStream) {
		Yaml yaml = new Yaml();
		Map yamlParse = (Map) yaml.load(inputStream);
		Set<StandardPredicate> closedPredicates = definePredicates(datastore, yamlParse);
		loadDataFiles(datastore, yamlParse);

		return new DataLoaderOutput(closedPredicates);
	}

}

