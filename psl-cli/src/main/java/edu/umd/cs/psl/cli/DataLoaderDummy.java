/**
 * 
 */
package edu.umd.cs.psl.cli;

import java.io.FileInputStream;

import edu.umd.cs.psl.database.DataStore;

/**
 * 
 *
 */
public class DataLoaderDummy {
	public static String PARTITION_NAME_OBSERVATIONS = "observations";
	public static String PARTITION_NAME_TARGET = "target";
	
	public static DataLoaderOutputDummy load(DataStore datastore, FileInputStream filePath) {
		return new DataLoaderOutputDummy();
	}
}
