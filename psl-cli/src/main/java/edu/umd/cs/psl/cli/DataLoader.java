/**
 * 
 */
package edu.umd.cs.psl.cli;

import java.io.*;
import java.util.*;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.model.predicate.StandardPredicate;

import org.yaml.snakeyaml.Yaml;

/**
 * @author jay
 *
 */
public class DataLoader {
	public class DLOutput {
		protected Set<StandardPredicate> closedPredicates;

		protected DLOutput(Set<StandardPredicate> closedPredicates){
			this.closedPredicates = closedPredicates;
		}
		public Set<StandardPredicate> getClosedPredicates(){
			return closedPredicates;
		}
	}
	
	private static void stubWarning(String warn){
		System.out.println(warn);
	}
	
	private static FileInputStream openInputFile(String inputPath){
		File inFile = new File(inputPath);
		try {
			return new FileInputStream(inFile);
		} catch (FileNotFoundException e) {
			stubWarning("Data specification file "+inputPath+" does not exist");
			return null;
		}
	}
	
	private static void definePredicates(DataStore datastore, Map yamlMap){
		
	}
	
	public static DLOutput load(DataStore datastore, String inputPath){
		//FileInputStream inStream = openInputFile(inputPath);
		Yaml yaml = new Yaml();
		Map yamlParse = (Map)yaml.load(openInputFile(inputPath));
		System.out.println(yamlParse.toString());
		
		return null;
	}
	
	
}
