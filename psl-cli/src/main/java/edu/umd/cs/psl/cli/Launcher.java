/*
 * This file is part of the PSL software.
 * Copyright 2011-2013 University of Maryland
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
package edu.umd.cs.psl.cli;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Set;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.application.inference.MPEInference;
import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import edu.umd.cs.psl.evaluation.result.FullInferenceResult;
import edu.umd.cs.psl.model.Model;
import edu.umd.cs.psl.model.argument.GroundTerm;
import edu.umd.cs.psl.model.atom.GroundAtom;
import edu.umd.cs.psl.model.predicate.StandardPredicate;
import edu.umd.cs.psl.reasoner.admm.ADMMReasonerFactory;
import edu.umd.cs.psl.util.database.Queries;

/**
 *
 */
public class Launcher {

	public static String OPERATION_INFER = "infer";
	public static String OPERATION_LEARN = "learn";
	public static String OPTION_PROPERTIES = "propertiesPath";
	public static String OPTION_OUTPUT_DIR = "outputDirectoryPath";

	Logger log = LoggerFactory.getLogger(Launcher.class);

	public void run(String[] args) throws Exception {
		ConfigManager cm = ConfigManager.getManager();
		ConfigBundle cb = cm.getBundle("cli");
		String defaultPath = System.getProperty("java.io.tmpdir");
		String dbpath = cb.getString("dbpath", defaultPath + File.separator
				+ "cli");
		DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk,
				cb.getString("dbpath", dbpath), true), cb);

		Options options = new Options();
		options.addOption(OPTION_PROPERTIES, false, "Properties file path.");
		options.addOption("OPTION_OUTPUT_DIR", false, "Output directory path.");
		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);

		// Optional PSL properties
		if (cmd.hasOption(OPTION_PROPERTIES)) {
			String propertiesPath = cmd.getOptionValue(OPTION_PROPERTIES);
			cm.loadResource(propertiesPath);
		}

		String operation = args[0];

		/*
		 * Load data.
		 */

		String dataPath = args[2];
		File dataFile = new File(dataPath);
		FileInputStream dataFileInputStream = new FileInputStream(dataFile);

		DataLoaderOutputDummy dataLoaderOutput = DataLoaderDummy.load(data,
				dataFileInputStream);
		Set<StandardPredicate> closedPredicates = dataLoaderOutput.closedPredicates;

		/*
		 * Load model.
		 */

		String modelPath = args[1];
		File modelFile = new File(modelPath);
		FileInputStream modelFileInputStream = new FileInputStream(modelFile);

		Model model = ModelLoaderDummy.load(data, modelFileInputStream);

		/*
		 * Create database, application, etc.
		 */

		Partition targetPartition = data
				.getPartition(DataLoaderDummy.PARTITION_NAME_TARGET);
		Partition observationsPartition = data
				.getPartition(DataLoaderDummy.PARTITION_NAME_OBSERVATIONS);
		Database database = data.getDatabase(targetPartition,
				observationsPartition);

		// Inference
		if (operation == OPERATION_INFER) {
			cb.setProperty(MPEInference.REASONER_KEY, new ADMMReasonerFactory());
			MPEInference mpe = new MPEInference(model, database, cb);
			FullInferenceResult result = mpe.mpeInference();

			// List of open predicates
			Set<StandardPredicate> openPredicates = data
					.getRegisteredPredicates();
			openPredicates.remove(closedPredicates);

			if (cmd.hasOption(OPTION_OUTPUT_DIR)) {
				/*
				 * If an output directory is specified, write a file for each
				 * predicate and suppress the output to STDOUT.
				 */
				String outputDirectoryPath = cmd
						.getOptionValue(OPTION_OUTPUT_DIR);
				File outputDirectory = new File(outputDirectoryPath);
				if (!outputDirectory.exists()) {
					System.out.println("creating directory: "
							+ outputDirectoryPath);
					boolean resulttmp = false;
					try {
						outputDirectory.mkdir();
						resulttmp = true;
					} catch (SecurityException se) {
						// handle it
					}
					if (resulttmp) {
						System.out.println("DIR created");
					}
				}
				for (StandardPredicate openPredicate : openPredicates) {
					File predFile = new File(outputDirectory,
							openPredicate.getName()+".csv");
					FileWriter predFileWriter = new FileWriter(predFile);
					for (GroundAtom atom : Queries.getAllAtoms(database,
							openPredicate)) {
						for (GroundTerm term : atom.getArguments()) {
							predFileWriter.write(term.toString()+",");
						}
						predFileWriter.write(Double.toString(atom.getValue()));
						predFileWriter.write("\n");
					}
					predFileWriter.close();
				}
			} else {
				for (StandardPredicate openPredicate : openPredicates) {
					for (GroundAtom atom : Queries.getAllAtoms(database,
							openPredicate)) {
						System.out.println(atom.toString() + " = "
								+ atom.getValue());
					}
				}
			}
		} else if (operation == OPERATION_LEARN) {
			throw new Exception("Operation not supported: "+OPERATION_LEARN);
			// Learning
		}
	}

	/**
	 * @throws Exception 
	 * 
	 */
	public static void main(String[] args) throws Exception {
		new Launcher().run(args);
	}
}
