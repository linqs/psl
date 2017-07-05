/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2017 The Regents of the University of California
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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Comparator;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.PropertyConfigurator;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.application.inference.result.FullInferenceResult;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.application.learning.weight.maxlikelihood.VotedPerceptron;
import org.linqs.psl.cli.dataloader.DataLoader;
import org.linqs.psl.cli.dataloader.DataLoaderOutput;
import org.linqs.psl.config.ConfigBundle;
import org.linqs.psl.config.ConfigManager;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.Queries;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.reasoner.admm.ADMMReasonerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Launches PSL from the command line. Supports inference and supervised parameter learning
 */
public class Launcher {
	private static Logger log;

	/* Command line syntax keywords */
	public static final String OPERATION_INFER = "infer";
	public static final String OPERATION_LEARN = "learn";
	public static final String OPTION_MODEL = "model";
	public static final String OPTION_DATA = "data";
	public static final String OPTION_PROPERTIES = "properties";
	public static final String OPTION_LOG4J = "log4j";
	public static final String OPTION_OUTPUT_DIR = "output";

	public static final String MODEL_FILE_EXTENSION = ".psl";

	/* Reserved partition names */
	public static final String PARTITION_NAME_OBSERVATIONS = "observations";
	public static final String PARTITION_NAME_TARGET = "targets";
	public static final String PARTITION_NAME_LABELS = "truth";


	public void outputResults(CommandLine cmd, Database database,
			Set<StandardPredicate> openPredicates){
		if (cmd.hasOption(OPTION_OUTPUT_DIR)) {
			/*
			 * If an output directory is specified, write a file for each
			 * predicate and suppress the output to STDOUT.
			 */
			String outputDirectoryPath = cmd
					.getOptionValue(OPTION_OUTPUT_DIR);
			File outputDirectory = new File(outputDirectoryPath);
			if (!outputDirectory.exists()) {
				log.debug("creating directory: {}", outputDirectoryPath);
				boolean dirCreated = false;
				try {
					outputDirectory.mkdir();
					dirCreated = true;
				} catch (SecurityException se) {
					log.error("Unable to create directory");
					return;
				}
				if (dirCreated) {
					log.debug("{} created", outputDirectoryPath);
				}
			}
			for (StandardPredicate openPredicate : openPredicates) {
				try {
					File predFile = new File(outputDirectory,
							openPredicate.getName() + ".csv");
					FileWriter predFileWriter = new FileWriter(predFile);
					for (GroundAtom atom : Queries.getAllAtoms(database,
							openPredicate)) {
						for (Constant term : atom.getArguments()) {
							predFileWriter.write(term.toString() + ",");
						}
						predFileWriter.write(Double.toString(atom.getValue()));
						predFileWriter.write("\n");
					}
					predFileWriter.close();
				} catch (IOException e){
					log.error("Exception writing predicate {}", openPredicate);
				}
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


	}


	@SuppressWarnings("deprecation")
	public void run(CommandLine cmd) throws IOException, ConfigurationException, ClassNotFoundException, IllegalAccessException, InstantiationException {

		/*
		 * Initializes log4j
		 */
		if (cmd.hasOption(OPTION_LOG4J)) {
			URL url = new URL(cmd.getOptionValue(OPTION_LOG4J));
			PropertyConfigurator.configure(url);
		}
		else {
			ConsoleAppender appender = new ConsoleAppender();
			appender.setName("psl-cli");
			appender.setThreshold(Priority.INFO);
			appender.setLayout(new PatternLayout("%-4r [%t] %-5p %c %x - %m%n"));
			appender.setTarget(ConsoleAppender.SYSTEM_OUT);
			appender.activateOptions();
			BasicConfigurator.configure(appender);
		}
		log = LoggerFactory.getLogger(Launcher.class);
		/*
		 * Loads configuration
		 */
		ConfigManager cm = ConfigManager.getManager();
		if (cmd.hasOption(OPTION_PROPERTIES)) {
			String propertiesPath = cmd.getOptionValue(OPTION_PROPERTIES);
			cm.loadResource(propertiesPath);
		}
		ConfigBundle cb = cm.getBundle("cli");
		//TODO: Delete the following command when it becomes default behavior
		cb.setProperty(RDBMSDataStore.USE_STRING_ID_KEY, true);

		/*
		 * Sets up DataStore
		 */

		String defaultPath = System.getProperty("java.io.tmpdir");
		String dbpath = cb.getString("dbpath", defaultPath + File.separator + "cli");
		DataStore data = new RDBMSDataStore(new H2DatabaseDriver(Type.Disk,
				cb.getString("dbpath", dbpath), true), cb);

		/*
		 * Loads data
		 */

		log.info("data:: loading:: ::starting");
		File dataFile = new File(cmd.getOptionValue(OPTION_DATA));
		InputStream dataFileInputStream = new FileInputStream(dataFile);

		DataLoaderOutput dataLoaderOutput = DataLoader.load(data, dataFileInputStream);
		Set<StandardPredicate> closedPredicates = dataLoaderOutput.getClosedPredicates();
		log.info("data:: loading:: ::done");

		/*
		 * Loads model
		 */
		log.info("model:: loading:: ::starting");
		File modelFile = new File(cmd.getOptionValue(OPTION_MODEL));
		FileReader modelFileReader = new FileReader(modelFile);

		Model model = ModelLoader.load(data, modelFileReader);
		log.debug(model.toString());
		log.info("model:: loading:: ::done");

		/*
		 * Create database, application, etc.
		 */
		Partition targetPartition = data.getPartition(PARTITION_NAME_TARGET);
		Partition observationsPartition = data.getPartition(PARTITION_NAME_OBSERVATIONS);
		Partition truthPartition = data.getPartition(PARTITION_NAME_LABELS);
		Database database = data.getDatabase(targetPartition, closedPredicates, observationsPartition);

		// Inference
		if (cmd.hasOption(OPERATION_INFER)) {
			log.info("operation::infer ::starting");

			cb.setProperty(MPEInference.REASONER_KEY, new ADMMReasonerFactory());
			MPEInference mpe = new MPEInference(model, database, cb);
			FullInferenceResult result = mpe.mpeInference();
			log.info("operation::infer inference:: ::done");

			// List of open predicates
			Set<StandardPredicate> openPredicates = data.getRegisteredPredicates();
			openPredicates.removeAll(closedPredicates);

			outputResults(cmd, database, openPredicates);

			log.info("operation::infer ::done");

		} else if (cmd.hasOption(OPERATION_LEARN)) {
			log.info("operation::learn ::starting");
			Database tr_database = data.getDatabase(truthPartition, data.getRegisteredPredicates());
			VotedPerceptron vp =  new MaxLikelihoodMPE(model, database, tr_database, cb);
			vp.learn();
			log.info("operation::learn learning:: ::done");

			String modelFilename = cmd.getOptionValue(OPTION_MODEL);
			String learnedFilename;
			int prefixPos = modelFilename.lastIndexOf(MODEL_FILE_EXTENSION);
			if(prefixPos == -1){
				log.error("Model filename {} does not end in {} - improvising",
						modelFilename, MODEL_FILE_EXTENSION);
				learnedFilename = modelFilename + MODEL_FILE_EXTENSION;
			} else {
				learnedFilename = modelFilename.substring(0, prefixPos) + "-learned"
						+ MODEL_FILE_EXTENSION;
			}
			log.info("Writing learned model to {}", learnedFilename);
			File learnedFile = new File(learnedFilename);
			FileWriter learnedFileWriter = new FileWriter(learnedFile);
			//TODO: fix this so we don't need cleanModel!
			String outModel = Pattern.compile("\\( | \\)").matcher(model.asString()).replaceAll("");
			learnedFileWriter.write(outModel);
			learnedFileWriter.close();

			log.info("operation::learn ::done");
			//throw new IllegalArgumentException("Operation not supported: " + OPERATION_LEARN);
			// Learning
		} else {
			throw new IllegalArgumentException("No valid operation provided.");
		}
		database.close();
		data.close();
	}

	public static void main(String[] args) {
		try {

			/*
			 * Parses command line
			 */

			Options options = new Options();

			OptionGroup mainCommand = new OptionGroup();
			mainCommand.addOption(new Option(OPERATION_INFER, "Run MAP inference"));
			mainCommand.addOption(new Option(OPERATION_LEARN, "Run weight learning"));
			mainCommand.setRequired(true);
			options.addOptionGroup(mainCommand);

			options.addOption(Option.builder(OPTION_MODEL)
					.required()
					.desc("Path to PSL model file")
					.hasArg()
					.argName("path")
					.build());

			options.addOption(Option.builder(OPTION_DATA)
					.required()
					.desc("Path to PSL data file")
					.hasArg()
					.argName("path")
					.build());

			options.addOption(Option.builder(OPTION_LOG4J)
					.desc("Optional log4j properties file path")
					.hasArg()
					.argName("path")
					.build());

			options.addOption(Option.builder(OPTION_PROPERTIES)
					.desc("Optional PSL properties file path")
					.hasArg()
					.argName("path")
					.build());

			options.addOption(Option.builder(OPTION_OUTPUT_DIR)
					.desc("Optional path for writing results to filesystem (default is STDOUT)")
					.hasArg()
					.argName("path")
					.build());

			HelpFormatter hf = new HelpFormatter();
			/* Hacks the option ordering */
			hf.setOptionComparator(new Comparator<Option>() {
				@Override
				public int compare(Option o1, Option o2) {
					if (!o1.hasArg() && o2.hasArg()) {
						return -1;
					}
					else if (o1.hasArg() && !o2.hasArg()) {
						return 1;
					}
					else if (o1.isRequired() && !o2.isRequired()) {
						return -1;
					}
					else if (!o1.isRequired() && o2.isRequired()) {
						return 1;
					}
					else {
						return o1.toString().compareTo(o2.toString());
					}
				}
			});

			try {

				/*
				 * Runs PSL
				 */
				CommandLineParser parser = new DefaultParser();
				CommandLine cmd = parser.parse(options, args);
				new Launcher().run(cmd);
			}
			catch (ParseException e) {
				System.err.println("Command line error: " + e.getMessage());
				hf.printHelp("psl", options, true);
			}
		}
		catch (Exception e) {
			System.err.println("Unexpected exception!");
			e.printStackTrace(System.err);
		}
	}
}
