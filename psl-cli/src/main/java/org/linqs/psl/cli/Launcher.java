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

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.inference.MPEInference;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.application.learning.weight.maxlikelihood.MaxLikelihoodMPE;
import org.linqs.psl.config.Config;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.util.Reflection;
import org.linqs.psl.util.Version;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.Priority;
import org.apache.log4j.PropertyConfigurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Launches PSL from the command line.
 * Supports inference and supervised parameter learning.
 */
public class Launcher {
	// Command line options.
	public static final String OPTION_HELP = "h";
	public static final String OPTION_HELP_LONG = "help";
	public static final String OPERATION_INFER = "i";
	public static final String OPERATION_INFER_LONG = "infer";
	public static final String OPERATION_LEARN = "l";
	public static final String OPERATION_LEARN_LONG = "learn";

	public static final String OPTION_DATA = "d";
	public static final String OPTION_DATA_LONG = "data";
	public static final String OPTION_DB_H2_PATH = "h2path";
	public static final String OPTION_DB_POSTGRESQL_NAME = "postgres";
	public static final String OPTION_EVAL = "e";
	public static final String OPTION_EVAL_LONG = "eval";
	public static final String OPTION_INT_IDS = "int";
	public static final String OPTION_INT_IDS_LONG = "int-ids";
	public static final String OPTION_LOG4J = "4j";
	public static final String OPTION_LOG4J_LONG = "log4j";
	public static final String OPTION_MODEL = "m";
	public static final String OPTION_MODEL_LONG = "model";
	public static final String OPTION_OUTPUT_DIR = "o";
	public static final String OPTION_OUTPUT_DIR_LONG = "output";
	public static final String OPTION_PROPERTIES = "D";
	public static final String OPTION_PROPERTIES_FILE = "p";
	public static final String OPTION_PROPERTIES_FILE_LONG = "properties";
	public static final String OPTION_VERSION = "v";
	public static final String OPTION_VERSION_LONG = "version";

	public static final String MODEL_FILE_EXTENSION = ".psl";
	public static final String DEFAULT_H2_DB_PATH =
			Paths.get(System.getProperty("java.io.tmpdir"),
			"cli_" + System.getProperty("user.name") + "@" + getHostname()).toString();
	public static final String DEFAULT_POSTGRES_DB_NAME = "psl_cli";
	public static final String DEFAULT_IA = MPEInference.class.getName();
	public static final String DEFAULT_WLA = MaxLikelihoodMPE.class.getName();

	// Reserved partition names.
	public static final String PARTITION_NAME_OBSERVATIONS = "observations";
	public static final String PARTITION_NAME_TARGET = "targets";
	public static final String PARTITION_NAME_LABELS = "truth";

	private CommandLine options;
	private Logger log;

	private Launcher(CommandLine options) {
		this.options = options;
		this.log = initLogger();
		initConfig();
	}

	/**
	 * Initializes log4j.
	 */
	private Logger initLogger() {
		Properties props = new Properties();

		if (options.hasOption(OPTION_LOG4J)) {
			try {
				props.load(new FileReader(options.getOptionValue(OPTION_LOG4J)));
			} catch (IOException ex) {
				throw new RuntimeException("Failed to read logger configuration from a file.", ex);
			}
		} else {
			// Setup a default logger.
			props.setProperty("log4j.rootLogger", "INFO, A1");
			props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
			props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
			props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");
		}

		// Load any options specified directly on the command line (override standing options).
		for (Map.Entry<Object, Object> entry : options.getOptionProperties("D").entrySet()) {
			String key = entry.getKey().toString();

			if (!key.startsWith("log4j.")) {
				continue;
			}

			props.setProperty(key, entry.getValue().toString());
		}

		// Log4j is pretty picky about it's thresholds, so we will specially set one option.
		if (props.containsKey("log4j.threshold")) {
			props.setProperty("log4j.rootLogger", props.getProperty("log4j.threshold") + ", A1");
		}

		PropertyConfigurator.configure(props);
		return LoggerFactory.getLogger(Launcher.class);
	}

	/**
	 * Initialize log4j with a default logger.
	 * Only to be used with short CLI runs: --version or --help.
	 */
	private static void initDefaultLogger() {
		Properties props = new Properties();

		props.setProperty("log4j.rootLogger", "INFO, A1");
		props.setProperty("log4j.appender.A1", "org.apache.log4j.ConsoleAppender");
		props.setProperty("log4j.appender.A1.layout", "org.apache.log4j.PatternLayout");
		props.setProperty("log4j.appender.A1.layout.ConversionPattern", "%-4r [%t] %-5p %c %x - %m%n");

		PropertyConfigurator.configure(props);
	}

	/**
	 * Loads configuration.
	 */
	private void initConfig() {
		// Load a properties file that was specified on the command line.
		if (options.hasOption(OPTION_PROPERTIES_FILE)) {
			String propertiesPath = options.getOptionValue(OPTION_PROPERTIES_FILE);
			Config.loadResource(propertiesPath);
		}

		// Load any options specified directly on the command line (override standing options).
		for (Map.Entry<Object, Object> entry : options.getOptionProperties("D").entrySet()) {
			String key = entry.getKey().toString();
			Config.setProperty(key, entry.getValue());
		}
	}

	/**
	 * Set up the DataStore.
	 */
	private DataStore initDataStore() {
		String dbPath = DEFAULT_H2_DB_PATH;
		boolean useH2 = true;

		if (options.hasOption(OPTION_DB_H2_PATH)) {
			dbPath = options.getOptionValue(OPTION_DB_H2_PATH);
		} else if (options.hasOption(OPTION_DB_POSTGRESQL_NAME)) {
			dbPath = options.getOptionValue(OPTION_DB_POSTGRESQL_NAME, DEFAULT_POSTGRES_DB_NAME);
			useH2 = false;
		}

		DatabaseDriver driver = null;
		if (useH2) {
			driver = new H2DatabaseDriver(Type.Disk, dbPath, true);
		} else {
			driver = new PostgreSQLDriver(dbPath, true);
		}

		return new RDBMSDataStore(driver);
	}

	private Set<StandardPredicate> loadData(DataStore dataStore) {
		log.info("Loading data");

		Set<StandardPredicate> closedPredicates;
		try {
			String path = options.getOptionValue(OPTION_DATA);
			closedPredicates = DataLoader.load(dataStore, path, options.hasOption(OPTION_INT_IDS));
		} catch (ConfigurationException | FileNotFoundException ex) {
			throw new RuntimeException("Failed to load data.", ex);
		}

		log.info("Data loading complete");

		return closedPredicates;
	}

	private void runInference(Model model, DataStore dataStore, Set<StandardPredicate> closedPredicates, String inferenceName) {
		log.info("Starting inference with class: {}", inferenceName);

		// Create database.
		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
		Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);

		InferenceApplication inferenceApplication =
				InferenceApplication.getInferenceApplication(inferenceName, model, database);
		inferenceApplication.inference();

		log.info("Inference Complete");

		// Output the results.
		outputResults(database, dataStore, closedPredicates);

		database.close();
	}

	private void outputResults(Database database, DataStore dataStore, Set<StandardPredicate> closedPredicates) {
		// Set of open predicates
		Set<StandardPredicate> openPredicates = dataStore.getRegisteredPredicates();
		openPredicates.removeAll(closedPredicates);

		// If we are just writing to the console, use a more human-readable format.
		if (!options.hasOption(OPTION_OUTPUT_DIR)) {
			for (StandardPredicate openPredicate : openPredicates) {
				for (GroundAtom atom : database.getAllGroundRandomVariableAtoms(openPredicate)) {
					System.out.println(atom.toString() + " = " + atom.getValue());
				}
			}

			return;
		}

		// If we have an output directory, then write a different file for each predicate.
		String outputDirectoryPath = options.getOptionValue(OPTION_OUTPUT_DIR);
		File outputDirectory = new File(outputDirectoryPath);

		// mkdir -p
		outputDirectory.mkdirs();

		for (StandardPredicate openPredicate : openPredicates) {
			try {
				FileWriter predFileWriter = new FileWriter(new File(outputDirectory, openPredicate.getName() + ".txt"));

				for (GroundAtom atom : database.getAllGroundRandomVariableAtoms(openPredicate)) {
					for (Constant term : atom.getArguments()) {
						predFileWriter.write(term.toString() + "\t");
					}
					predFileWriter.write(Double.toString(atom.getValue()));
					predFileWriter.write("\n");
				}

				predFileWriter.close();
			} catch (IOException ex) {
				log.error("Exception writing predicate {}", openPredicate);
			}
		}
	}

	private void learnWeights(Model model, DataStore dataStore, Set<StandardPredicate> closedPredicates, String wlaName)
			throws IOException {
		log.info("Starting weight learning with learner: " + wlaName);

		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
		Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

		Database randomVariableDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
		Database observedTruthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		WeightLearningApplication learner = WeightLearningApplication.getWLA(wlaName, model.getRules(),
				randomVariableDatabase, observedTruthDatabase);
		learner.learn();
		learner.close();

		randomVariableDatabase.close();
		observedTruthDatabase.close();

		log.info("Weight learning complete");

		String modelFilename = options.getOptionValue(OPTION_MODEL);

		String learnedFilename;
		int prefixPos = modelFilename.lastIndexOf(MODEL_FILE_EXTENSION);
		if (prefixPos == -1) {
			learnedFilename = modelFilename + MODEL_FILE_EXTENSION;
		} else {
			learnedFilename = modelFilename.substring(0, prefixPos) + "-learned" + MODEL_FILE_EXTENSION;
		}
		log.info("Writing learned model to {}", learnedFilename);

		FileWriter learnedFileWriter = new FileWriter(new File(learnedFilename));
		String outModel = model.asString();

		// Remove excess parens.
		outModel = outModel.replaceAll("\\( | \\)", "");

		learnedFileWriter.write(outModel);
		learnedFileWriter.close();
	}

	private void evaluation(DataStore dataStore, Set<StandardPredicate> closedPredicates, String evalClassName) {
		log.info("Starting evaluation with class: {}.", evalClassName);

		// Set of open predicates
		Set<StandardPredicate> openPredicates = dataStore.getRegisteredPredicates();
		openPredicates.removeAll(closedPredicates);

		// Create database.
		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
		Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

		Database predictionDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
		Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		Evaluator evaluator = (Evaluator)Reflection.newObject(evalClassName);

		for (StandardPredicate targetPredicate : openPredicates) {
			// Before we run evaluation, ensure that the truth database actaully has instances of the target predicate.
			if (truthDatabase.countAllGroundAtoms(targetPredicate) == 0) {
				log.info("Skipping evaluation for {} since there are no ground truth atoms", targetPredicate);
				continue;
			}

			evaluator.compute(predictionDatabase, truthDatabase, targetPredicate);
			log.info("Evaluation results for {} -- {}", targetPredicate.getName(), evaluator.getAllStats());
		}

		predictionDatabase.close();
		truthDatabase.close();

		log.info("Evaluation complete.");
	}

	private void run()
			throws IOException, ClassNotFoundException, IllegalAccessException, InstantiationException {
		DataStore dataStore = initDataStore();

		// Loads data
		Set<StandardPredicate> closedPredicates = loadData(dataStore);

		// Loads model
		log.info("Loading model");
		File modelFile = new File(options.getOptionValue(OPTION_MODEL));
		Model model = ModelLoader.load(dataStore, new FileReader(modelFile));
		log.debug(model.toString());
		log.info("Model loading complete");

		// Inference
		if (options.hasOption(OPERATION_INFER)) {
			runInference(model, dataStore, closedPredicates, options.getOptionValue(OPERATION_INFER, DEFAULT_IA));
		} else if (options.hasOption(OPERATION_LEARN)) {
			learnWeights(model, dataStore, closedPredicates, options.getOptionValue(OPERATION_LEARN, DEFAULT_WLA));
		} else {
			throw new IllegalArgumentException("No valid operation provided.");
		}

		// Evaluation
		if (options.hasOption(OPTION_EVAL)) {
			evaluation(dataStore, closedPredicates, options.getOptionValue(OPTION_EVAL));
		}

		dataStore.close();
	}

	private static String getHostname() {
		String hostname = "unknown";

		try {
			hostname = InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException ex) {
			// log.warn("Hostname can not be resolved, using '" + hostname + "'.");
		}

		return hostname;
	}

	private static Options setupOptions() {
		Options options = new Options();

		OptionGroup mainCommand = new OptionGroup();

		mainCommand.addOption(Option.builder(OPERATION_INFER)
				.longOpt(OPERATION_INFER_LONG)
				.desc("Run MAP inference." +
						" You can optionally supply a fully qualified name for an inference application" +
						" (defaults to " + DEFAULT_IA + ").")
				.hasArg()
				.argName("inferenceMethod")
				.optionalArg(true)
				.build());

		mainCommand.addOption(Option.builder(OPERATION_LEARN)
				.longOpt(OPERATION_LEARN_LONG)
				.desc("Run weight learning." +
						" You can optionally supply a fully qualified name for a weight learner" +
						" (defaults to " + DEFAULT_WLA + ").")
				.hasArg()
				.argName("learner")
				.optionalArg(true)
				.build());

		// Make sure that help and version are in the main group so a successful run can use them.

		mainCommand.addOption(Option.builder(OPTION_HELP)
				.longOpt(OPTION_HELP_LONG)
				.desc("Print this help message and exit")
				.build());

		mainCommand.addOption(Option.builder(OPTION_VERSION)
				.longOpt(OPTION_VERSION_LONG)
				.desc("Print the PSL version and exit")
				.build());

		mainCommand.setRequired(true);
		options.addOptionGroup(mainCommand);

		options.addOption(Option.builder(OPTION_DATA)
				.longOpt(OPTION_DATA_LONG)
				.desc("Path to PSL data file")
				.hasArg()
				.argName("path")
				.build());

		options.addOption(Option.builder()
				.longOpt(OPTION_DB_H2_PATH)
				.desc("Path for H2 database file (defaults to 'cli_<user name>@<host name>' ('" + DEFAULT_H2_DB_PATH + "'))." +
						" Not compatible with the '--" + OPTION_DB_POSTGRESQL_NAME + "' option.")
				.hasArg()
				.argName("path")
				.build());

		options.addOption(Option.builder()
				.longOpt(OPTION_DB_POSTGRESQL_NAME)
				.desc("Name for the PostgreSQL database to use (defaults to " + DEFAULT_POSTGRES_DB_NAME + ")." +
						" Not compatible with the '--" + OPTION_DB_H2_PATH + "' option." +
						" Currently only local databases without credentials are supported.")
				.hasArg()
				.argName("name")
				.optionalArg(true)
				.build());

		options.addOption(Option.builder(OPTION_EVAL)
				.longOpt(OPTION_EVAL_LONG)
				.desc("Run the named evaluator (" + Evaluator.class.getName() + ") on any open predicate with a 'truth' partition.")
				.hasArg()
				.argName("evaluator")
				.build());

		options.addOption(Option.builder(OPTION_INT_IDS)
				.longOpt(OPTION_INT_IDS_LONG)
				.desc("Use integer identifiers (UniqueIntID) instead of string identifiers (UniqueStringID).")
				.build());

		options.addOption(Option.builder(OPTION_LOG4J)
				.longOpt(OPTION_LOG4J_LONG)
				.desc("Optional log4j properties file path")
				.hasArg()
				.argName("path")
				.build());

		options.addOption(Option.builder(OPTION_MODEL)
				.longOpt(OPTION_MODEL_LONG)
				.desc("Path to PSL model file")
				.hasArg()
				.argName("path")
				.build());

		options.addOption(Option.builder(OPTION_OUTPUT_DIR)
				.longOpt(OPTION_OUTPUT_DIR_LONG)
				.desc("Optional path for writing results to filesystem (default is STDOUT)")
				.hasArg()
				.argName("path")
				.build());

		options.addOption(Option.builder(OPTION_PROPERTIES_FILE)
				.longOpt(OPTION_PROPERTIES_FILE_LONG)
				.desc("Optional PSL properties file path")
				.hasArg()
				.argName("path")
				.build());

		options.addOption(Option.builder(OPTION_PROPERTIES)
				.argName("name=value")
				.desc("Directly specify PSL properties (overrides options set via --" + OPTION_PROPERTIES_FILE_LONG + ")." +
						" See https://github.com/linqs/psl/wiki/Configuration-Options for a list of available options." +
						" Log4j properties (properties starting with 'log4j') will be passed to the logger." +
						" 'log4j.threshold=DEBUG', for example, will be passed to log4j and set the global logging threshold.")
				.hasArg()
				.numberOfArgs(2)
				.valueSeparator('=')
				.build());

		return options;
	}

	private static HelpFormatter getHelpFormatter() {
		HelpFormatter helpFormatter = new HelpFormatter();

		// Hack the option ordering to put argumentions without options first and then required options first.
		// infer and learn go first, then required, then just normal.
		helpFormatter.setOptionComparator(new Comparator<Option>() {
			@Override
			public int compare(Option o1, Option o2) {
				String name1 = o1.getOpt();
				if (name1 == null) {
					name1 = o1.getLongOpt();
				}

				String name2 = o2.getOpt();
				if (name2 == null) {
					name2 = o2.getLongOpt();
				}

				if (name1.equals(OPERATION_INFER)) {
					return -1;
				}

				if (name2.equals(OPERATION_INFER)) {
					return 1;
				}

				if (name1.equals(OPERATION_LEARN)) {
					return -1;
				}

				if (name2.equals(OPERATION_LEARN)) {
					return 1;
				}

				if (o1.isRequired() && !o2.isRequired()) {
					return -1;
				}

				if (!o1.isRequired() && o2.isRequired()) {
					return 1;
				}

				return name1.compareTo(name2);
			}
		});

		helpFormatter.setWidth(100);

		return helpFormatter;
	}

	/**
	 * Parse the options on the command line.
	 * Will exit on error, but Will return null if the CLI should not be run (like if we are doing a help/version run).
	 */
	private static CommandLine parseOptions(String[] args) {
		Options options = setupOptions();
		CommandLineParser parser = new DefaultParser();
		CommandLine commandLineOptions = null;

		try {
			commandLineOptions = parser.parse(options, args);
		} catch (ParseException ex) {
			System.err.println("Command line error: " + ex.getMessage());
			getHelpFormatter().printHelp("psl", options, true);
			System.exit(1);
		}

		if (commandLineOptions.hasOption(OPTION_HELP)) {
			initDefaultLogger();
			getHelpFormatter().printHelp("psl", options, true);
			return null;
		}

		if (commandLineOptions.hasOption(OPTION_VERSION)) {
			initDefaultLogger();
			System.out.println("PSL CLI Version " + Version.get());
			return null;
		}

		// Data and model are required.
		// (We don't enforce them earlier so we can have successful runs with help and version.)

		if (!commandLineOptions.hasOption(OPTION_DATA)) {
			System.out.println(String.format("Missing required option: --%s/-%s.", OPTION_DATA_LONG, OPTION_DATA));
			getHelpFormatter().printHelp("psl", options, true);
			System.exit(1);
		}

		if (!commandLineOptions.hasOption(OPTION_MODEL)) {
			System.out.println(String.format("Missing required option: --%s/-%s.", OPTION_MODEL_LONG, OPTION_MODEL));
			getHelpFormatter().printHelp("psl", options, true);
			System.exit(1);
		}

		// Can't have both an H2 and Postgres database.
		if (commandLineOptions.hasOption(OPTION_DB_H2_PATH) && commandLineOptions.hasOption(OPTION_DB_POSTGRESQL_NAME)) {
			System.err.println("Command line error: Options '--" + OPTION_DB_H2_PATH + "' and '--" + OPTION_DB_POSTGRESQL_NAME + "' are not compatible.");
			getHelpFormatter().printHelp("psl", options, true);
			System.exit(2);
		}

		return commandLineOptions;
	}

	public static void main(String[] args) {
		main(args, false);
	}

	public static void main(String[] args, boolean rethrow) {
		try {
			CommandLine commandLineOptions = parseOptions(args);
			if (commandLineOptions == null) {
				return;
			}

			Launcher pslLauncher = new Launcher(commandLineOptions);
			pslLauncher.run();
		} catch (Exception ex) {
			if (rethrow) {
				throw new RuntimeException("Failed to run CLI.", ex);
			} else {
				System.err.println("Unexpected exception!");
				ex.printStackTrace(System.err);
				System.exit(1);
			}
		}
	}
}
