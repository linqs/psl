/**
 * 
 */
package edu.umd.cs.psl.cli;

import java.io.FileInputStream;

import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.model.Model;

/**
 *
 */
public class ModelLoaderDummy {
	public static Model load(DataStore data, FileInputStream input) {
		return new Model();
	}
}
