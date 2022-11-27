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
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.database.rdbms.RDBMSDataStore;
import org.linqs.psl.database.rdbms.driver.DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.H2DatabaseDriver;
import org.linqs.psl.database.rdbms.driver.PostgreSQLDriver;
import org.linqs.psl.database.rdbms.driver.SQLiteDriver;
import org.linqs.psl.evaluation.EvaluationInstance;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.grounding.Grounding;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.predicate.Predicate;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

    private static final String[] PARTITION_NAMES = new String[]{
        PARTITION_NAME_OBSERVATIONS,
        PARTITION_NAME_TARGET,
        PARTITION_NAME_LABELS
    };

    public Runtime() {
        initLogger();
    }

    public void run() {
        run(new RuntimeConfig());
    }

    public void run(String configPath) {
        RuntimeConfig config = RuntimeConfig.fromFile(configPath);
        run(config);
    }

    /**
     * The primary interface into a PSL runtime.
     * Options specified in the config will be applied during the runtime, and reset after.
     */
    public void run(RuntimeConfig config) {
        Config.pushLayer();

        try {
            runInternal(config);
        } finally {
            Config.popLayer();
        }
    }

    private void runInternal(RuntimeConfig config) {
        // Apply any top-level options found in the config.
        for (Map.Entry<String, String> entry : config.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        // Specially check if we need to re-init the logger.
        initLogger();

        if (checkHelp() || checkVersion()) {
            return;
        }

        log.info("PSL Runtime Version {}", Version.getFull());
        config.validate();

        // Apply top-level options again after validation (since options may have been changed or added).
        for (Map.Entry<String, String> entry : config.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        Model model = null;
        if (RuntimeOptions.LEARN.getBoolean()) {
            model = runLearning(config);
        }

        if (RuntimeOptions.INFERENCE.getBoolean()) {
            runInference(config, model);
        }

        cleanup();
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

    private void evaluate(Database targetDatabase, Database truthDatabase, List<EvaluationInstance> evaluations) {
        for (EvaluationInstance evaluation : evaluations) {
            evaluation.compute(targetDatabase, truthDatabase);
            log.info("Evaluation results: {}", evaluation.getOutput());
        }
    }

    private DataStore initDataStore(RuntimeConfig config) {
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

        DataStore dataStore = new RDBMSDataStore(driver);

        for (RuntimeConfig.PredicateConfigInfo predicateInfo : config.predicates.values()) {
            Predicate predicate = Predicate.get(predicateInfo.name);
            if (predicate instanceof StandardPredicate) {
                dataStore.registerPredicate((StandardPredicate)predicate);
            }
        }

        return dataStore;
    }

    private void initLogger() {
        if (RuntimeOptions.LOG_LEVEL.isSet()) {
            Logger.setLevel(RuntimeOptions.LOG_LEVEL.getString());
        }
    }

    private void loadData(DataStore dataStore, RuntimeConfig config, boolean infer) {
        log.debug("Data loading start");

        for (RuntimeConfig.PredicateConfigInfo predicateInfo : config.predicates.values()) {
            if (predicateInfo.dataSize() == 0) {
                continue;
            }

            StandardPredicate predicate = StandardPredicate.get(predicateInfo.name);

            // Align with partition names.
            List<Iterable<String>> paths = Arrays.asList(
                predicateInfo.observations.getDataPaths(infer),
                predicateInfo.targets.getDataPaths(infer),
                predicateInfo.truth.getDataPaths(infer)
            );

            List<Iterable<List<String>>> points = Arrays.asList(
                predicateInfo.observations.getDataPoints(infer),
                predicateInfo.targets.getDataPoints(infer),
                predicateInfo.truth.getDataPoints(infer)
            );


            for (int i = 0; i < PARTITION_NAMES.length; i++) {
                loadDataPaths(dataStore, predicate, PARTITION_NAMES[i], paths.get(i));
                loadDataPoints(dataStore, predicate, PARTITION_NAMES[i], points.get(i));
            }
        }

        log.debug("Data loading complete");
    }

    private void loadDataPaths(DataStore dataStore, StandardPredicate predicate, String partitionName, Iterable<String> paths) {
        Partition partition = dataStore.getPartition(partitionName);
        Inserter inserter = dataStore.getInserter(predicate, partition);

        for (String path : paths) {
            log.debug("Loading data for {} ({} partition) from {}", predicate, partitionName, path);
            inserter.loadDelimitedDataAutomatic(path);
        }
    }

    private void loadDataPoints(DataStore dataStore, StandardPredicate predicate, String partitionName, Iterable<List<String>> points) {
        Iterator<List<String>> iterator = points.iterator();
        if (!iterator.hasNext()) {
            return;
        }

        Partition partition = dataStore.getPartition(partitionName);
        Inserter inserter = dataStore.getInserter(predicate, partition);

        log.debug("Loading embeded data for {} ({} partition)", predicate, partitionName);

        int arity = predicate.getArity();
        Object[] point = new Object[arity];

        int count = 0;

        while (iterator.hasNext()) {
            List<String> rawPoint = iterator.next();

            if (rawPoint.size() < arity || rawPoint.size() > (arity + 1)) {
                throw new IllegalArgumentException(String.format(
                        "Provided data point for predicate %s does not have the correct number of arguments. Expecting %d or %d arguments. Offending data point: %s.",
                        predicate.getName(), arity, arity + 1, rawPoint));
            }

            for (int i = 0; i < arity; i++) {
                point[i] = rawPoint.get(i);
            }

            if (rawPoint.size() == (arity + 1)) {
                inserter.insertValue(Double.parseDouble(rawPoint.get(arity)), point);
            } else {
                inserter.insert(point);
            }

            count++;
        }

        log.trace("Loaded {} rows of embeded data for {} ({} partition)", count, predicate, partitionName);
    }

    private void runInference(RuntimeConfig config, Model model) {
        // Save the config so we can apply inference-only options.
        Config.pushLayer();

        try {
            runInferenceInternal(config, model);
        } finally {
            Config.popLayer();
        }
    }

    private void runInferenceInternal(RuntimeConfig config, Model model) {
        // Apply any inference-only options found in the config.
        for (Map.Entry<String, String> entry : config.infer.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        // If a model was passed in, then it was learned from the common rules (so don't include them).
        // Otherwise, load the common rules.
        if (model == null) {
            model = new Model();
            for (Rule rule : config.rules.getRules()) {
                model.addRule(rule);
            }
        }

        // Add infer-only rules.
        for (Rule rule : config.learn.rules.getRules()) {
            model.addRule(rule);
        }

        if (model.getRules().size() == 0) {
            throw new RuntimeException("No rules found for inference.");
        }

        log.debug("Model:");
        for (Rule rule : model.getRules()) {
            log.debug("   " + rule);
        }

        DataStore dataStore = initDataStore(config);
        loadData(dataStore, config, true);

        Set<StandardPredicate> closedPredicates = config.getClosedPredicates(true);

        Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
        Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
        Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

        Database targetDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        List<EvaluationInstance> evaluations = getEvaluations(config);

        GroundRuleOutputter groundingCallback = null;
        if (RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES.getBoolean()) {
            String path = RuntimeOptions.INFERENCE_OUTPUT_GROUNDRULES_PATH.getString();
            groundingCallback = new GroundRuleOutputter(path);
            Grounding.setGroundRuleCallback(groundingCallback);
        }

        InferenceApplication inferenceApplication = InferenceApplication.getInferenceApplication(
                RuntimeOptions.INFERENCE_METHOD.getString(), model.getRules(), targetDatabase);

        inferenceApplication.inference(RuntimeOptions.INFERENCE_COMMIT.getBoolean(), false, evaluations, truthDatabase);

        if (groundingCallback != null) {
            groundingCallback.close();
            Grounding.setGroundRuleCallback(null);
        }

        String outputDir = RuntimeOptions.INFERENCE_OUTPUT_RESULTS_DIR.getString();
        if (outputDir == null) {
            log.info("Writing inferred predicates to stdout.");
            targetDatabase.outputRandomVariableAtoms();
        } else {
            log.info("Writing inferred predicates to directory: " + outputDir);
            targetDatabase.outputRandomVariableAtoms(outputDir);
        }

        evaluate(targetDatabase, truthDatabase, evaluations);

        inferenceApplication.close();
        targetDatabase.close();
        truthDatabase.close();
        dataStore.close();
    }

    private Model runLearning(RuntimeConfig config) {
        // Save the config so we can apply learn-only options.
        Config.pushLayer();

        try {
            return runLearningInternal(config);
        } finally {
            Config.popLayer();
        }
    }

    private Model runLearningInternal(RuntimeConfig config) {
        // Apply any learn-only options found in the config.
        for (Map.Entry<String, String> entry : config.learn.options.entrySet()) {
            Config.setProperty(entry.getKey(), entry.getValue(), false);
        }

        Model model = new Model();

        // Add all common rules.
        for (Rule rule : config.rules.getRules()) {
            model.addRule(rule);
        }

        // Add learn-only rules.
        for (Rule rule : config.learn.rules.getRules()) {
            model.addRule(rule);
        }

        if (model.getRules().size() == 0) {
            throw new RuntimeException("No rules found for learning.");
        }

        log.debug("Model:");
        for (Rule rule : model.getRules()) {
            log.debug("   " + rule);
        }

        DataStore dataStore = initDataStore(config);
        loadData(dataStore, config, false);

        Set<StandardPredicate> closedPredicates = config.getClosedPredicates(false);

        Partition targetPartition = dataStore.getPartition(PARTITION_NAME_TARGET);
        Partition observationsPartition = dataStore.getPartition(PARTITION_NAME_OBSERVATIONS);
        Partition truthPartition = dataStore.getPartition(PARTITION_NAME_LABELS);

        Database targetDatabase = dataStore.getDatabase(targetPartition, closedPredicates, observationsPartition);
        Database truthDatabase = dataStore.getDatabase(truthPartition, dataStore.getRegisteredPredicates());

        EvaluationInstance primaryEvaluation = null;
        for (EvaluationInstance evaluation : getEvaluations(config)) {
            if (evaluation.isPrimary()) {
                primaryEvaluation = evaluation;
                break;
            }
        }

        WeightLearningApplication learner = WeightLearningApplication.getWLA(
                RuntimeOptions.LEARN_METHOD.getString(), model.getRules(),
                targetDatabase, truthDatabase);
        learner.setEvaluation(primaryEvaluation);
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

    private List<EvaluationInstance> getEvaluations(RuntimeConfig config) {
        boolean hasPrimaryEval = false;

        List<EvaluationInstance> evaluations = new ArrayList<EvaluationInstance>();

        for (RuntimeConfig.PredicateConfigInfo predicateInfo : config.predicates.values()) {
            if (predicateInfo.evaluations.size() == 0) {
                continue;
            }

            Predicate predicate = Predicate.get(predicateInfo.name);
            if (!(predicate instanceof StandardPredicate)) {
                continue;
            }

            for (RuntimeConfig.EvalInfo eval : predicateInfo.evaluations) {
                Evaluator evaluator = null;
                Config.pushLayer();

                try {
                    evaluator = (Evaluator)Reflection.newObject(eval.evaluator);
                } finally {
                    Config.popLayer();
                }

                evaluations.add(new EvaluationInstance((StandardPredicate)predicate, evaluator, eval.primary));
                hasPrimaryEval |= eval.primary;
            }
        }

        if (evaluations.size() == 0) {
            return evaluations;
        }

        if (!hasPrimaryEval) {
            if (evaluations.size() > 1) {
                log.info("Multiple evaluations declared, but no primary evaluation specified. Using the first evaluation instance: {}.", evaluations.get(0));
            }

            evaluations.get(0).setPrimary(true);
        }

        return evaluations;
    }

    public static void main(String[] args) {
        if (args == null || args.length != 1) {
            System.out.println("USAGE: " + Runtime.class + " <path to JSON config>");
            return;
        }

        Runtime runtime = new Runtime();
        runtime.run(args[0]);
    }

    public static enum DatabaseType {
        H2,
        Postgres,
        SQLite,
    }

    public static class GroundRuleOutputter implements Grounding.GroundRuleCallback {
        private volatile boolean headerWritten;
        private PrintWriter out;
        private boolean closeOut;

        public GroundRuleOutputter(String path) {
            headerWritten = false;

            out = new PrintWriter(System.out);
            boolean closeOut = false;

            if (path != null) {
                out = new PrintWriter(FileUtils.getBufferedWriter(path));
                closeOut = true;
            }
        }

        @Override
        public void call(GroundRule groundRule) {
            String row = "";

            if (groundRule instanceof WeightedGroundRule) {
                WeightedGroundRule weightedGroundRule = (WeightedGroundRule)groundRule;
                row = StringUtils.join("\t",
                        "" + weightedGroundRule.getWeight(), "" + weightedGroundRule.isSquared(), groundRule.baseToString());
            } else {
                row = StringUtils.join("\t", ".", "" + false, groundRule.baseToString());
            }

            output(row);
        }

        /**
         * Synchronized since we may be coming from multiple threads.
         */
        private synchronized void output(String row) {
            if (row == null) {
                // Used to block close() until all threads are done.
                return;
            }

            if (!headerWritten) {
                headerWritten = true;

                String header = StringUtils.join("\t", "Weight", "Squared?", "Rule");
                out.println(header);
            }

            out.println(row);
        }

        public void close() {
            // Block.
            output(null);
            out.flush();

            if (closeOut) {
                out.close();
            }
        }
    }
}
