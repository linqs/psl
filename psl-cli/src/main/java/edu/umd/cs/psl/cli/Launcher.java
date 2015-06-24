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

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import edu.umd.cs.psl.config.ConfigBundle;
import edu.umd.cs.psl.config.ConfigManager;
import edu.umd.cs.psl.database.DataStore;
import edu.umd.cs.psl.database.Database;
import edu.umd.cs.psl.database.Partition;
import edu.umd.cs.psl.database.rdbms.RDBMSDataStore;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver;
import edu.umd.cs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import edu.umd.cs.psl.model.Model;

/**
 *
 */
public class Launcher {
	Logger log = LoggerFactory.getLogger(Launcher.class);

	public void run(String[] args) throws ConfigurationException,
			ParseException, FileNotFoundException {
		ConfigManager cm = ConfigManager.getManager();
		ConfigBundle cb = cm.getBundle("cli");
		String defaultPath = System.getProperty("java.io.tmpdir");
		String dbpath = cb.getString("dbpath", defaultPath + File.separator
				+ "cli");
		DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk,
				cb.getString("dbpath", dbpath), true), cb);

		Options options = new Options();
		options.addOption("dataPath", false, "Data file path.");

		CommandLineParser parser = new DefaultParser();
		CommandLine cmd = parser.parse(options, args);
		
		/*
		 * Load data.
		 */
		
		String dataPath = cmd.getOptionValue("dataPath");
		File dataFile = new File(dataPath);
		FileInputStream dataFileInputStream = new FileInputStream(dataFile);

		DataLoaderOutputDummy dataLoaderOutput = DataLoaderDummy.load(data,
				dataFileInputStream);

		/*
		 * Load model.
		 */
		
		String modelPath = cmd.getOptionValue("modelPath");
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

	}

	/**
	 * @throws ConfigurationException
	 * @throws ParseException
	 * @throws FileNotFoundException
	 * 
	 */
	public static void main(String[] args) throws ConfigurationException,
			ParseException, FileNotFoundException {
		new Launcher().run(args);
	}
}
