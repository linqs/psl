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
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver.Type;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.evaluation.statistics.ContinuousPredictionComparator;
import org.linqs.psl.evaluation.statistics.ContinuousPredictionStatistics;
import org.linqs.psl.evaluation.statistics.DiscretePredictionComparator;
import org.linqs.psl.evaluation.statistics.DiscretePredictionStatistics;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.atom.GroundAtom;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.Constant;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.reasoner.admm.ADMMReasonerFactory;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
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
	public static final String OPTION_EVAL_CONTINUOUS = "ec";
	public static final String OPTION_EVAL_CONTINUOUS_LONG = "eval-continuous";
	public static final String OPTION_EVAL_DISCRETE = "ed";
	public static final String OPTION_EVAL_DISCRETE_LONG = "eval-discrete";
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

	public static final String CONFIG_PREFIX = "cli";
	public static final String MODEL_FILE_EXTENSION = ".psl";
	public static final String DEFAULT_H2_DB_PATH =
			Paths.get(System.getProperty("java.io.tmpdir"),
			"cli_" + System.getProperty("user.name") + "@" + getHostname()).toString();
	public static final String DEFAULT_POSTGRES_DB_NAME = "psl_cli";
	public static final String DEFAULT_DISCRETE_THRESHOLD = "0.5";

	// Reserved partition names.
	public static final String PARTITION_NAME_OBSERVATIONS = "observations";
	public static final String PARTITION_NAME_TARGET = "targets";
	public static final String PARTITION_NAME_LABELS = "truth";

	private CommandLine options;
	private ConfigBundle config;
	private Logger log;

	private Launcher(CommandLine options) {
		this.options = options;
		this.log = initLogger();
		this.config = initConfig();
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

			// If the key is prefixed woth CONFIG_PREFIX, then add another key without the prefix.
			// The user may have been confused.
			if (key.startsWith(CONFIG_PREFIX + ".")) {
				key = key.replaceFirst(CONFIG_PREFIX + ".", "");
			}

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
	 * Loads configuration.
	 */
	private ConfigBundle initConfig() {
		ConfigManager cm = null;

		try {
			cm = ConfigManager.getManager();

			// Load a properties file that was specified on the command line.
			if (options.hasOption(OPTION_PROPERTIES_FILE)) {
				String propertiesPath = options.getOptionValue(OPTION_PROPERTIES_FILE);
				cm.loadResource(propertiesPath);
			}
		} catch (ConfigurationException ex) {
			throw new RuntimeException("Failed to initialize configuration for CLI.", ex);
		}

		ConfigBundle bundle = cm.getBundle(CONFIG_PREFIX);

		// Load any options specified directly on the command line (override standing options).
		for (Map.Entry<Object, Object> entry : options.getOptionProperties("D").entrySet()) {
			String key = entry.getKey().toString();
			bundle.setProperty(key, entry.getValue());

			// If the key is prefixed woth CONFIG_PREFIX, then add another key without the prefix.
			// The user may have been confused.
			if (key.startsWith(CONFIG_PREFIX + ".")) {
				bundle.setProperty(key.replaceFirst(CONFIG_PREFIX + ".", ""), entry.getValue());
			}
		}

		return bundle;
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

		return new RDBMSDataStore(driver, config);
	}

	private Set<StandardPredicate> loadData(DataStore dataStore) {
		log.info("Loading data");

		Set<StandardPredicate> closedPredicates;
		try {
			File dataFile = new File(options.getOptionValue(OPTION_DATA));
			DataLoaderOutput dataLoaderOutput = DataLoader.load(dataStore, new FileInputStream(dataFile), options.hasOption(OPTION_INT_IDS));
			closedPredicates = dataLoaderOutput.getClosedPredicates();
		} catch (FileNotFoundException ex) {
			throw new RuntimeException("Failed to load data.", ex);
		}

		log.info("Data loading complete");

		return closedPredicates;
	}

	private void runInference(Model model, DataStore dataStore, Set<StandardPredicate> closedPredicates)
			throws ClassNotFoundException, IllegalAccessException, InstantiationException {
		log.info("Starting inference");

		// Create database.
		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
		Database database = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);

		MPEInference mpe = new MPEInference(model, database, config);
		FullInferenceResult result = mpe.mpeInference();

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
				for (GroundAtom atom : Queries.getAllAtoms(database, openPredicate)) {
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

				for (GroundAtom atom : Queries.getAllAtoms(database, openPredicate)) {
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

	private void learnWeights(Model model, DataStore dataStore, Set<StandardPredicate> closedPredicates)
			throws ClassNotFoundException, IOException, IllegalAccessException, InstantiationException {
		log.info("Starting weight learning");

		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
		Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

		Database randomVariableDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
		Database observedTruthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		VotedPerceptron vp = new MaxLikelihoodMPE(model, randomVariableDatabase, observedTruthDatabase, config);
		vp.learn();

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

	private void continuousEval(DataStore dataStore, Set<StandardPredicate> closedPredicates) {
		log.info("Starting continuous evaluation");

		// Set of open predicates
		Set<StandardPredicate> openPredicates = dataStore.getRegisteredPredicates();
		openPredicates.removeAll(closedPredicates);

		// Create database.
		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

		Database predictionDatabase = dataStore.getDatabase(targetPartition, closedPredicates);
		Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		ContinuousPredictionComparator comparator = new ContinuousPredictionComparator(predictionDatabase, truthDatabase);

		for (StandardPredicate targetPredicate : openPredicates) {
			// Before we run evaluation, ensure that the truth database actaully has instances of the target predicate.
			if (Queries.countAllGroundAtoms(truthDatabase, targetPredicate) == 0) {
				log.info("Skipping continuous evaluation for {} since there are no ground truth atoms", targetPredicate);
				continue;
			}

			ContinuousPredictionStatistics stats = comparator.compare(targetPredicate);
			double mae = stats.getMAE();
			double mse = stats.getMSE();

			log.info("Continuous evaluation results for {} -- MAE: {}, MSE: {}", targetPredicate.getName(), mae, mse);
		}

		predictionDatabase.close();
		truthDatabase.close();

		log.info("Continuous evaluation complete");
	}

	private void discreteEval(DataStore dataStore, Set<StandardPredicate> closedPredicates, double threshold) {
		log.info("Starting discrete evaluation");

		// Set of open predicates
		Set<StandardPredicate> openPredicates = dataStore.getRegisteredPredicates();
		openPredicates.removeAll(closedPredicates);

		// Create database.
		Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
		Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

		Database predictionDatabase = dataStore.getDatabase(targetPartition, closedPredicates);
		Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

		DiscretePredictionComparator comparator = new DiscretePredictionComparator(predictionDatabase, truthDatabase, threshold);

		for (StandardPredicate targetPredicate : openPredicates) {
			// Before we run evaluation, ensure that the truth database actaully has instances of the target predicate.
			if (Queries.countAllGroundAtoms(truthDatabase, targetPredicate) == 0) {
				log.info("Skipping discrete evaluation for {} since there are no ground truth atoms", targetPredicate);
				continue;
			}

			DiscretePredictionStatistics stats = comparator.compare(targetPredicate);

			double accuracy = stats.getAccuracy();
			double error = stats.getError();
			double positivePrecision = stats.getPrecision(DiscretePredictionStatistics.BinaryClass.POSITIVE);
			double positiveRecall = stats.getRecall(DiscretePredictionStatistics.BinaryClass.POSITIVE);
			double negativePrecision = stats.getPrecision(DiscretePredictionStatistics.BinaryClass.NEGATIVE);
			double negativeRecall = stats.getRecall(DiscretePredictionStatistics.BinaryClass.NEGATIVE);

			log.info("Discrete evaluation results for {} --" +
					" Accuracy: {}, Error: {}," +
					" Positive Class Precision: {}, Positive Class Recall: {}," +
					" Negative Class Precision: {}, Negative Class Recall: {},",
					targetPredicate.getName(),
					accuracy, error, positivePrecision, positiveRecall, negativePrecision, negativeRecall);
		}

		predictionDatabase.close();
		truthDatabase.close();

		log.info("Discrete evaluation complete");
	}

	private void run()
			throws IOException, ConfigurationException, ClassNotFoundException, IllegalAccessException, InstantiationException {
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
			runInference(model, dataStore, closedPredicates);
		} else if (options.hasOption(OPERATION_LEARN)) {
			learnWeights(model, dataStore, closedPredicates);
		} else {
			throw new IllegalArgumentException("No valid operation provided.");
		}

		// Evaluation
		if (options.hasOption(OPTION_EVAL_CONTINUOUS)) {
			continuousEval(dataStore, closedPredicates);
		}

		if (options.hasOption(OPTION_EVAL_DISCRETE)) {
			String stringThreshold = options.getOptionValue(OPTION_EVAL_DISCRETE, DEFAULT_DISCRETE_THRESHOLD);
			discreteEval(dataStore, closedPredicates, Double.valueOf(stringThreshold));
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
		mainCommand.addOption(new Option(OPERATION_INFER, OPERATION_INFER_LONG, false, "Run MAP inference"));
		mainCommand.addOption(new Option(OPERATION_LEARN, OPERATION_LEARN_LONG, false, "Run weight learning"));
		mainCommand.setRequired(true);
		options.addOptionGroup(mainCommand);

		options.addOption(Option.builder(OPTION_DATA)
				.longOpt(OPTION_DATA_LONG)
				.required()
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

		options.addOption(Option.builder(OPTION_EVAL_CONTINUOUS)
				.longOpt(OPTION_EVAL_CONTINUOUS_LONG)
				.desc("Run evlaution using continuous comparison on any open predicate with a 'truth' partition.")
				.build());

		options.addOption(Option.builder(OPTION_EVAL_DISCRETE)
				.longOpt(OPTION_EVAL_DISCRETE_LONG)
				.desc("Run evlaution using discrete comparison on any open predicate with a 'truth' partition." +
						" You can optionally supply 'threshold' (defaults to " + DEFAULT_DISCRETE_THRESHOLD + ")." +
						" Every truth value over the threshold is considered true.")
				.hasArg()
				.argName("threshold")
				.optionalArg(true)
				.build());

		options.addOption(Option.builder(OPTION_HELP)
				.longOpt(OPTION_HELP_LONG)
				.desc("Print this help message and exit")
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
				.required()
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
			getHelpFormatter().printHelp("psl", options, true);
			System.exit(0);
		}

		if (commandLineOptions.hasOption(OPTION_DB_H2_PATH) && commandLineOptions.hasOption(OPTION_DB_POSTGRESQL_NAME)) {
			System.err.println("Command line error: Options '--" + OPTION_DB_H2_PATH + "' and '--" + OPTION_DB_POSTGRESQL_NAME + "' are not compatible.");
			getHelpFormatter().printHelp("psl", options, true);
			System.exit(2);
		}

		if (commandLineOptions.hasOption(OPTION_EVAL_DISCRETE)) {
			String stringThreshold = commandLineOptions.getOptionValue(OPTION_EVAL_DISCRETE, DEFAULT_DISCRETE_THRESHOLD);
			try {
				double threshold = Double.valueOf(stringThreshold);
				if (threshold < 0 || threshold > 1) {
					throw new NumberFormatException();
				}
			} catch (NumberFormatException ex) {
				System.err.println("Command line error: The optional argument to '-" + OPTION_EVAL_DISCRETE + "' must be a double in [0, 1].");
				getHelpFormatter().printHelp("psl", options, true);
				System.exit(3);
			}
		}

		return commandLineOptions;
	}

	public static void main(String[] args) {
		try {
			CommandLine commandLineOptions = parseOptions(args);
			Launcher pslLauncher = new Launcher(commandLineOptions);
			pslLauncher.run();
		} catch (Exception ex) {
			System.err.println("Unexpected exception!");
			ex.printStackTrace(System.err);
		}
	}
}
