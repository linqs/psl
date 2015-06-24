/**
 * 
 */
package edu.umd.cs.psl.cli;

import java.io.*;
import java.util.*;
import java.util.Map.Entry;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.loading.Inserter;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.model.argument.ArgumentType;
import edu.umd.cs.psl.model.predicate.PredicateFactory;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.ui.loading.InserterUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

/**
 * @author jay
 *
 */
public class DataLoader {
	private static final Logger log = LoggerFactory.getLogger(DataLoader.class);
	
	private static void stubWarning(String warn){
		System.out.println(warn);
	}
	
	private static FileInputStream openInputFile(String inputPath) throws FileNotFoundException{
		File inFile = new File(inputPath);
		return new FileInputStream(inFile);
	}
	
	private static Set<StandardPredicate> definePredicates(DataStore datastore, Map yamlMap) throws Exception{
		if(!yamlMap.containsKey("predicates")){
			throw new Exception("No 'predicates' block defined in data specification");			
		}
		Set<StandardPredicate> closed = new HashSet<StandardPredicate>();
		PredicateFactory pf = PredicateFactory.getFactory();
		for (Entry<String, String> predicateSpec : ((Map<String,String>)yamlMap.get("predicates")).entrySet()){
			
			//parse the predicate/args part
			String[] predicateParts = predicateSpec.getKey().split("/",2);
			if(predicateParts.length < 2){
				throw new Exception("Improperly specified predicate "+predicateSpec.getKey());
			}
			String predicateStr = predicateParts[0];
			int arity = Integer.parseInt(predicateParts[1]);
			log.debug("Found predicate {} with arity {}",predicateStr, arity);
			
			//create a predicate and add it to the datastore
			ArgumentType[] args = new ArgumentType[arity];
			for(int i = 0; i < arity; i++){
				args[i] = ArgumentType.UniqueID;
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
	
	public static void loadDataFiles(DataStore datastore, Map yamlMap) throws Exception{
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
					throw new Exception("Unknown specification when loading "+partitionName);
				}
			}
		}
	}
	
	public static DataLoaderOutput load(DataStore datastore, InputStream inputStream) throws Exception{
		//FileInputStream inStream = openInputFile(inputPath);
		Yaml yaml = new Yaml();
		Map yamlParse = (Map)yaml.load(inputStream);
		System.out.println(yamlParse.toString());
		Set closedPredicates = definePredicates(datastore, yamlParse);
		loadDataFiles(datastore, yamlParse);
		
		return new DataLoaderOutput(closedPredicates);
	}
	
}

