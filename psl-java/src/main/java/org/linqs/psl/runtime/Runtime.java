/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2022 The Regents of the University of California
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
package org.linqs.psl.runtime;

import org.linqs.psl.application.inference.InferenceApplication;
import org.linqs.psl.application.learning.weight.WeightLearningApplication;
import org.linqs.psl.config.Config;
import org.linqs.psl.config.Option;
import org.linqs.psl.config.Options;
import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Database;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.database.rdbms.driver.SQLiteDriver;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.grounding.GroundRuleStore;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.GroundRule;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.rule.UnweightedGroundRule;
import org.linqs.psl.model.rule.WeightedGroundRule;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Logger;
import org.linqs.psl.util.Parallel;
import org.linqs.psl.util.Reflection;
import org.linqs.psl.util.StringUtils;
import org.linqs.psl.util.Version;

import org.apache.commons.configuration2.ex.ConfigurationException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

/**
 * Launches PSL from the command line.
 * Supports inference and supervised parameter learning.
 */
public class Runtime {
    private static final Logger log = Logger.getLogger(Runtime.class);

    public static final String PARTITION_NAME_OBSERVATIONS = "observations";
    public static final String PARTITION_NAME_TARGET = "targets";
    public static final String PARTITION_NAME_LABELS = "truth";

    /**
     * Create a new runtime with no additional arguments.
     * Arguments may have already been set in the config.
     */
    public Runtime() {
        this(null);
    }

    /**
     * Create a new runtime with the additional supplied arguments.
     */
    public Runtime(String[] args) {
        parseOptions(args);

        initConfig();
        initLogger();
    }

    /**
     * The primary interface into a PSL runtime.
     */
    public void run() {
        if (checkHelp() || checkVersion()) {
            return;
        }

        log.info("PSL Runtime Version {}", Version.getFull());
        checkConfig();

        Model model = null;
        if (RuntimeOptions.LEARN.getBoolean()) {
            model = runLearning();
        }

        if (RuntimeOptions.INFERENCE.getBoolean()) {
            runInference(model);
        }

        cleanup();
    }

    private void checkConfig() {
        boolean hasLearn = RuntimeOptions.LEARN.getBoolean();
        boolean hasInference = RuntimeOptions.INFERENCE.getBoolean();

        if (!hasInference && !hasLearn) {
            throw new IllegalStateException("Neither inference nor learning was specified.");
        }

        if (hasLearn && !RuntimeOptions.LEARN_DATA_PATH.isSet()) {
            throw new IllegalStateException("No learn data specified.");
        }

        if (hasLearn && !RuntimeOptions.LEARN_MODEL_PATH.isSet()) {
            throw new IllegalStateException("No learn model specified.");
        }

        if (!hasLearn && !RuntimeOptions.INFERENCE_MODEL_PATH.isSet()) {
            throw new IllegalStateException("No inference model (or learning) specified.");
        }

        if (hasInference && !RuntimeOptions.INFERENCE_DATA_PATH.isSet()) {
            throw new IllegalStateException("No infernece data specified.");
        }
    }

    private boolean checkHelp() {
        if (!RuntimeOptions.HELP.getBoolean()) {
            return false;
        }

        System.out.println("PSL Runtime Version " + Version.getFull());
        System.out.println("Options used by the PSL runtime:");

        List<Option> options = Options.fetchClassOptions(RuntimeOptions.class);
        Collections.sort(options);

        for (Option option : options) {
            System.out.println("    " + option.toString());
        }

        return true;
    }

    private boolean checkVersion() {
        if (!RuntimeOptions.VERSION.getBoolean()) {
            return false;
        }

        System.out.println("PSL Version " + Version.getFull());
        return true;
    }

    private void cleanup() {
        Parallel.close();
    }

    private void evaluate(DataStore dataStore, Database targetDatabase, Database truthDatabase,
            Set<StandardPredicate> closedPredicates, List<Evaluator> evaluators) {
        // Set of open predicates
        Set<StandardPredicate> openPredicates = dataStore.getRegisteredPredicates();
        openPredicates.removeAll(closedPredicates);

        for (Evaluator evaluator : evaluators) {
            log.debug("Starting evaluation with class: {}.", evaluator.getClass());

            for (StandardPredicate targetPredicate : openPredicates) {
                // Before we run evaluation, ensure that the truth database actually has instances of the target predicate.
                if (truthDatabase.countAllGroundAtoms(targetPredicate) == 0) {
                    log.debug("Skipping evaluation for {} since there are no ground truth atoms", targetPredicate);
                    continue;
                }

                evaluator.compute(targetDatabase, truthDatabase, targetPredicate, true);
                log.info("Evaluation results for {} -- {}", targetPredicate.getName(), evaluator.getAllStats());
            }
        }
    }

    /**
     * Loads any additional configuration.
     */
    private void initConfig() {
        String propertiesPath = RuntimeOptions.PROPERTIES_PATH.getString();
        if (propertiesPath != null) {
            Config.loadResource(propertiesPath);
        }
    }

    private DataStore initDataStore() {
        DatabaseDriver driver = null;
        String path = null;

        switch (DatabaseType.valueOf(RuntimeOptions.DB_TYPE.getString())) {
            case H2:
                path = RuntimeOptions.DB_H2_PATH.getString();
                H2DatabaseDriver.Type type = H2DatabaseDriver.Type.Disk;
                if (RuntimeOptions.DB_H2_INMEMORY.getBoolean()) {
                    type = H2DatabaseDriver.Type.Memory;
                }

                driver = new H2DatabaseDriver(type, path, true);
                break;
            case Postgres:
                driver = new PostgreSQLDriver(RuntimeOptions.DB_PG_NAME.getString(), true);
                break;
            case SQLite:
                path = RuntimeOptions.DB_SQLITE_PATH.getString();
                boolean inMemory = RuntimeOptions.DB_SQLITE_INMEMORY.getBoolean();
                driver = new SQLiteDriver(inMemory, path, true);
                break;
            default:
                throw new IllegalStateException("Unknown database type: " + RuntimeOptions.DB_TYPE.getString());
        }

        return new RDBMSDataStore(driver);
    }

    private void initLogger() {
        if (RuntimeOptions.LOG_LEVEL.isSet()) {
            Logger.setLevel(RuntimeOptions.LOG_LEVEL.getString());
        }
    }

    private Set<StandardPredicate> loadDataFile(DataStore dataStore, String path) {
        log.debug("Loading data");

        Set<StandardPredicate> closedPredicates;
        try {
            closedPredicates = DataLoader.load(dataStore, path, RuntimeOptions.DB_INT_IDS.getBoolean());
        } catch (ConfigurationException | FileNotFoundException ex) {
            throw new RuntimeException("Failed to load data from file: " + path, ex);
        }

        log.debug("Data loading complete");

        return closedPredicates;
    }

    private Model loadModelFile(String path) {
        log.debug("Loading model from {}", path);

        Model model = null;

        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
            model = ModelLoader.load(reader);
        } catch (IOException ex) {
            throw new RuntimeException("Failed to load model from file: " + path, ex);
        }

        log.debug("Loaded Model:");
        for (Rule rule : model.getRules()) {
            log.debug("   " + rule);
        }

        return model;
    }

    private void outputGroundRules(GroundRuleStore groundRuleStore, String path, boolean includeSatisfaction) {
        // Some inference/learning application will not have ground rule stores (if they stream).
        if (groundRuleStore == null) {
            return;
        }

        PrintWriter out = new PrintWriter(System.out);
        boolean closeOut = false;

        if (path != null) {
            out = new PrintWriter(FileUtils.getBufferedWriter(path));
            closeOut = true;
        }

        // Write a header.
        String header = StringUtils.join("\t", "Weight", "Squared?", "Rule");
        if (includeSatisfaction) {
            header = StringUtils.join("\t", header, "Satisfaction");
        }
        out.println(header);

        for (GroundRule groundRule : groundRuleStore.getGroundRules()) {
            String row = "";
            double satisfaction = 0.0;

            if (groundRule instanceof WeightedGroundRule) {
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
                row = StringUtils.join("\t",
                        "" + weightedGroundRule.getWeight(), "" + weightedGroundRule.isSquared(), groundRule.baseToString());
                satisfaction = 1.0 - weightedGroundRule.getIncompatibility();
            } else {
                UnweightedGroundRule unweightedGroundRule = (UnweightedGroundRule)groundRule;
                row = StringUtils.join("\t", ".", "" + false, groundRule.baseToString());
                satisfaction = 1.0 - unweightedGroundRule.getInfeasibility();
            }

            if (includeSatisfaction) {
                row = StringUtils.join("\t", row, "" + satisfaction);
            }

            out.println(row);
        }

        if (closeOut) {
            out.close();
        }
    }

    private void parseOptions(String[] args) {
        if (args == null) {
            return;
        }

        for (String arg : args) {
            String optionName = arg;
            String optionValue = null;

            if (arg.contains("=")) {
                String[] parts = arg.split("=");
                optionName = parts[0];
                optionValue = parts[1];
            }

            Config.setProperty(optionName, optionValue);
        }
    }

    private void runInference(Model model) {
        DataStore dataStore = initDataStore();
        Set<StandardPredicate> closedPredicates = loadDataFile(dataStore, RuntimeOptions.INFERENCE_DATA_PATH.getString());

        if (RuntimeOptions.INFERENCE_MODEL_PATH.isSet()) {
            model = loadModelFile(RuntimeOptions.INFERENCE_MODEL_PATH.getString());
        } else {
            log.debug("Using trained model:");
            for (Rule rule : model.getRules()) {
                log.debug("   " + rule);
            }
        }

        Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
        Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
        Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

        Database targetDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        List<Evaluator> evaluators = new ArrayList<Evaluator>();
        String evaluatorNames = RuntimeOptions.INFERENCE_EVAL.getString();
        if (evaluatorNames != null) {
            for (String evaluatorName : evaluatorNames.split(",")) {
                evaluators.add((Evaluator)Reflection.newObject(evaluatorName));
            }
        }

        InferenceApplication inferenceApplication = InferenceApplication.getInferenceApplication(
                RuntimeOptions.INFERENCE_METHOD.getString(), model.getRules(), targetDatabase);

        if (RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES.getBoolean()) {
            String path = RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES_PATH.getString();
            outputGroundRules(inferenceApplication.getGroundRuleStore(), path, false);
        }

        inferenceApplication.inference(RuntimeOptions.INFERENCE_COMMIT.getBoolean(), false, evaluators, truthDatabase);

        if (RuntimeOptions.INFERENCE_OUTPUT_SATISFACTIONS.getBoolean()) {
            String path = RuntimeOptions.INFERENCE_OUTPUT_SATISFACTIONS_PATH.getString();
            outputGroundRules(inferenceApplication.getGroundRuleStore(), path, true);
        }

        String outputDir = RuntimeOptions.INFERENCE_OUTPUT_RESULTS_DIR.getString();
        if (outputDir == null) {
            log.info("Writing inferred predicates to stdout.");
            targetDatabase.outputRandomVariableAtoms();
        } else {
            log.info("Writing inferred predicates to directory: " + outputDir);
            targetDatabase.outputRandomVariableAtoms(outputDir);
        }

        evaluate(dataStore, targetDatabase, truthDatabase, closedPredicates, evaluators);

        inferenceApplication.close();
        targetDatabase.close();
        truthDatabase.close();
        dataStore.close();
    }

    private Model runLearning() {
        DataStore dataStore = initDataStore();
        Set<StandardPredicate> closedPredicates = loadDataFile(dataStore, RuntimeOptions.LEARN_DATA_PATH.getString());
        Model model = loadModelFile(RuntimeOptions.LEARN_MODEL_PATH.getString());

        Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
        Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
        Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

        Database targetDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        WeightLearningApplication learner = WeightLearningApplication.getWLA(
                RuntimeOptions.LEARN_METHOD.getString(), model.getRules(),
                targetDatabase, truthDatabase);
        learner.learn();

        learner.close();
        targetDatabase.close();
        truthDatabase.close();
        dataStore.close();

        log.info("Learned Model:");
        for (Rule rule : model.getRules()) {
            log.info("   " + rule);
        }

        String outModelPath = RuntimeOptions.LEARN_OUTPUT_MODEL_PATH.getString();
        if (outModelPath != null) {
            log.debug("Writing learned model to {}.", outModelPath);

            // Remove excess parens from the string model.
            String outModel = model.asString();
            outModel = outModel.replaceAll("\\( | \\)", "");

            try (BufferedWriter learnedFileWriter = FileUtils.getBufferedWriter(outModelPath)) {
                learnedFileWriter.write(outModel);
            } catch (IOException ex) {
                log.error("Failed to write learned model:" + System.lineSeparator() + outModel);
                throw new RuntimeException("Failed to write learned model to: " + outModelPath, ex);
            }
        }

        return model;
    }

    public static void main(String[] args) {
        Runtime runtime = new Runtime(args);
        runtime.run();
    }

    public static enum DatabaseType {
        H2,
        Postgres,
        SQLite,
    }
}
