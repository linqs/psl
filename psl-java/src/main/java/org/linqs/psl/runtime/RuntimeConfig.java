/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2023 The Regents of the University of California
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

import org.linqs.psl.config.RuntimeOptions;
import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.Model;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Reflection;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A configuration container that describes how a runtime should operate.
 * Note that this class just represents what was provided as a configuration,
 * not a fully validated configuration.
 *
 * Any options set in a RuntimeConfig will override options set in psl.properties,
 * but will be overwritten by command-line/one-off options.
 *
 * This config is only meant to be applied for the duration of the relevant runtime.
 *
 * This class assumes that there are two phases in PSL: learning (learn) and inference (infer).
 * When using a boolean to indicate phase, assume absence of one is presence of the other.
 */
public class RuntimeConfig {
    public static final String KEY_RULES = "rules";

    public static final String KEY_ALL = "all";
    public static final String KEY_LEARN = "learn";
    public static final String KEY_VALIDATION = "validation";
    public static final String KEY_INFER = "infer";

    public static final String KEY_EVALUATOR = "evaluator";
    public static final String KEY_OPTIONS = "options";
    public static final String KEY_PRIMARY = "primary";

    public RuleSource rules;
    public Map<String, PredicateConfigInfo> predicates;
    public Map<String, String> options;

    public SplitConfigInfo learn;
    public SplitConfigInfo validation;
    public SplitConfigInfo infer;

    /**
     * The path that other relative paths are resolved against.
     */
    @JsonIgnore
    protected String relativeBasePath;

    public RuntimeConfig() {
        rules = new RuleList();
        predicates = new HashMap<String, PredicateConfigInfo>();
        options = new HashMap<String, String>();

        learn = new SplitConfigInfo();
        validation = new SplitConfigInfo();
        infer = new SplitConfigInfo();

        relativeBasePath = ".";
    }

    /**
     * Validate the config, instantiate predicates, and infer any missing options.
     * This is not a simple validation method, and should not be called until the runtime is ready to instantiate predicates.
     * All additional runtime-level options (like int/string types) should already be set before calling,
     * since these options may be used to infer the values for other configurations.
     *
     * Rules will be validated, but not passed back their Rule form.
     *
     * After validation, the options in this config should be applied again (since new options may be set).
     *
     * All relative paths will be resolved to absolute paths using |relativeBasePath|.
     */
    public void validate() {
        boolean runLearn = false;
        boolean runValidation = false;
        boolean runInfer = false;

        // Any top-level learn/infer indicates that those respective steps should be run.
        if (!learn.isEmpty()) {
            runLearn = true;
        }

        if (!validation.isEmpty()) {
            runValidation = true;
        }

        if (!infer.isEmpty()) {
            runInfer = true;
        }

        // Validate each predicate.

        boolean hasPrimaryEval = false;

        for (Map.Entry<String, PredicateConfigInfo> entry : predicates.entrySet()) {
            hasPrimaryEval = validatePredicate(entry.getKey(), entry.getValue(), hasPrimaryEval);

            if (entry.getValue().hasExplicitLearnData()) {
                runLearn = true;
            }

            if (entry.getValue().hasExplicitValidationData()) {
                runValidation = true;
            }

            if (entry.getValue().hasExplicitInferData()) {
                runInfer = true;
            }
        }

        // Parse and validate all rules.
        validateRules(rules);
        validateRules(learn.rules);
        validateRules(validation.rules);
        validateRules(infer.rules);

        // If no learn/infer option was passed in or inferred, then just assume inference.

        if (!RuntimeOptions.LEARN.isSet() && !RuntimeOptions.INFERENCE.isSet() && !runLearn && !runInfer) {
            runInfer = true;
        }

        // Any explicitly set value will always override.
        if (RuntimeOptions.LEARN.isSet()) {
            runLearn = RuntimeOptions.LEARN.getBoolean();
        }

        if (RuntimeOptions.VALIDATION.isSet()) {
            runValidation = RuntimeOptions.VALIDATION.getBoolean();
        }

        if (RuntimeOptions.INFERENCE.isSet()) {
            runInfer = RuntimeOptions.INFERENCE.getBoolean();
        }

        options.put(RuntimeOptions.LEARN.name(), "" + runLearn);
        options.put(RuntimeOptions.VALIDATION.name(), "" + runValidation);
        options.put(RuntimeOptions.INFERENCE.name(), "" + runInfer);
    }

    /**
     * Fetch standard predicates that are closed.
     * Should only be called after validation.
     */
    public Set<StandardPredicate> getClosedPredicates(String splitName) {
        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>();

        for (PredicateConfigInfo predicateInfo : predicates.values()) {
            if (predicateInfo.isOpen(splitName)) {
                continue;
            }

            Predicate predicate = Predicate.get(predicateInfo.name);
            if (predicate instanceof StandardPredicate) {
                closedPredicates.add((StandardPredicate)predicate);
            }
        }

        return closedPredicates;
    }

    private void validateRules(RuleSource ruleSource) {
        ruleSource.resolvePaths(relativeBasePath);
        for (Rule rule : ruleSource.getRules()) {
            // Empty.
        }
    }

    private boolean validatePredicate(String name, PredicateConfigInfo info, boolean hasPrimaryEval) {
        if (!name.equals(info.name)) {
            throw new IllegalArgumentException(String.format("Predicate name mismatch: ['%s', '%s'].", name, info.name));
        }

        info.resolvePaths(relativeBasePath);

        // Validate arity

        if (info.types == null) {
            info.types = new ArrayList<String>();
        }

        if (info.types.size() != 0 && info.arity <= 0) {
            info.arity = info.types.size();
        }

        if (info.types.size() != 0 && info.types.size() != info.arity) {
            throw new IllegalArgumentException(String.format(
                    "Arity mismatch on predicate %s." +
                    " Arity declared as property: %d." +
                    " Arity inferred by types: %d.",
                    info.name, info.arity, info.types.size()));
        }

        if (info.arity <= 0) {
            throw new IllegalArgumentException(String.format(
                    "Bad or missing arity on predicate %s." +
                    " Arity should be a positive integer, found %d.",
                    info.name, info.arity));
        }

        // Fill in missing types.

        if (info.types == null || info.types.size() == 0) {
            info.types = new ArrayList<String>(info.arity);

            String defaultType = ConstantType.UniqueStringID.toString();
            if (RuntimeOptions.DB_INT_IDS.getBoolean()) {
                defaultType = ConstantType.UniqueIntID.toString();
            }

            for (int i = 0; i < info.arity; i++) {
                info.types.add(defaultType);
            }
        }

        ConstantType[] types = new ConstantType[info.arity];
        for (int i = 0; i < info.arity; i++) {
            types[i] = ConstantType.valueOf(info.types.get(i));
        }

        // Validate that paths exist.

        for (String path : info.getAllDataPaths()) {
            if (!FileUtils.isFile(path)) {
                throw new IllegalArgumentException(String.format(
                        "Non-existent path found in data for predicate %s." +
                        " Path: '%s'.",
                        info.name, path));
            }
        }

        // Validate embedded data size.

        for (List<String> point : info.getAllDataPoints()) {
            if ((point.size() != info.arity) && (point.size() != info.arity + 1)) {
                throw new IllegalArgumentException(String.format(
                        "Mismatch on embedded data size for predicate %s." +
                        " Expected size %s or %s, found size %s." +
                        " Offending row: %s.",
                        info.name, info.arity, info.arity + 1, point.size(), point));
            }
        }

        // Instantiate the actual predicate.

        if (info.function != null) {
            ExternalFunctionalPredicate.get(name, (ExternalFunction)(Reflection.newObject(info.function)));

            if (info.dataSize() > 0) {
                throw new IllegalArgumentException(String.format(
                        "Predicate (%s) cannot be functional and have data.", name));
            }
        } else {
            getPredicateMethod(info, name, types);
        }

        // Validate the evaluations.

        for (EvalInfo eval : info.evaluations) {
            Object evaluator = Reflection.newObject(eval.evaluator);
            if (!(evaluator instanceof Evaluator)) {
                throw new IllegalArgumentException(String.format(
                        "Predicate (%s) has a listed evaluator that is not a child of %s. Found type: %s.",
                        name, "org.linqs.psl.evaluation.statistics.Evaluator", evaluator.getClass()));
            }

            if (eval.primary) {
                if (hasPrimaryEval) {
                    throw new IllegalArgumentException("Multiple primary evaluations found, at most one is allowed.");
                }

                hasPrimaryEval = true;
            }
        }

        return hasPrimaryEval;
    }

    public void getPredicateMethod(PredicateConfigInfo info, Object... parameters){
        Method method = null;
        Class<?>[] paramtersClass = new Class[parameters.length];

        for (int i = 0; i < parameters.length; i++) {
            paramtersClass[i] = parameters[i].getClass();
        }

        try {
            method = info.type.getMethod("get", paramtersClass);
        } catch(NoSuchMethodException ex) {
            throw new IllegalArgumentException(String.format(
                "Predicate (%s) with type (%s) does not have a static method with the name %s.",
                info.name, info.type, "get"));
        }

        try {
            method.invoke(null, parameters);
        } catch(IllegalArgumentException | IllegalAccessException | InvocationTargetException ex) {
            throw new IllegalArgumentException(String.format(
                "Predicate (%s) with type (%s) contains illegal arguments on static method with name %s." +
                " Found arguments: %s.",
                info.name, info.type, "get", Arrays.toString(parameters)), ex);
        }
    }

    @Override
    public String toString() {
        return toString(false);
    }

    public String toString(boolean skipEmptyValues) {
        ObjectMapper mapper = getMapper();

        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

        if (skipEmptyValues) {
            JsonInclude.Value includeValue = JsonInclude.Value.empty()
                    .withValueInclusion(JsonInclude.Include.CUSTOM)
                    .withValueFilter(EmptyValueFilter.class);

            mapper.setDefaultPropertyInclusion(includeValue);
        }

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withObjectIndenter(new DefaultIndenter("    ", "\n"));

        try {
            return mapper.writer(printer).writeValueAsString(this);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == null || !(other instanceof RuntimeConfig)) {
            return false;
        }

        RuntimeConfig otherConfig = (RuntimeConfig)other;
        return
                this.rules.equals(otherConfig.rules)
                && this.predicates.equals(otherConfig.predicates)
                && this.options.equals(otherConfig.options)
                && this.learn.equals(otherConfig.learn)
                && this.validation.equals(otherConfig.validation)
                && this.infer.equals(otherConfig.infer);
    }

    public static RuntimeConfig fromFile(String path) {
        RuntimeConfig config = null;

        if (path.toLowerCase().endsWith(".json")) {
            config = RuntimeConfig.fromJSON(FileUtils.readFileAsString(path));
        } else if (path.toLowerCase().endsWith(".yaml")) {
            config = RuntimeConfig.fromJSON(convertYAML(FileUtils.readFileAsString(path)));
        } else {
            throw new IllegalArgumentException("Expected runtime config file to end  in '.json' or '.yaml'.");
        }

        Path parent = Paths.get(path).normalize().getParent();
        if (parent != null) {
            config.relativeBasePath = parent.toString();
        }

        return config;
    }

    public static RuntimeConfig fromJSON(String contents) {
        return fromJSON(contents, ".");
    }

    public static RuntimeConfig fromJSON(String contents, String relativeBasePath) {
        JSONRuntimeConfig baseConfig = parseJSON(contents);
        RuntimeConfig config = baseConfig.formalize();
        config.relativeBasePath = relativeBasePath;
        return config;
    }

    private static JSONRuntimeConfig parseJSON(String contents) {
        JSONRuntimeConfig baseConfig = null;

        ObjectMapper mapper = getMapper();

        try {
            baseConfig = mapper.readValue(contents, JSONRuntimeConfig.class);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }

        return baseConfig;
    }

    private static ObjectMapper getMapper() {
        ObjectMapper mapper = JsonMapper.builder()
                .enable(JsonReadFeature.ALLOW_JAVA_COMMENTS)
                .enable(JsonReadFeature.ALLOW_YAML_COMMENTS)
                .enable(JsonReadFeature.ALLOW_SINGLE_QUOTES)
                .enable(JsonReadFeature.ALLOW_LEADING_ZEROS_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_LEADING_PLUS_SIGN_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();

        SimpleModule module = new SimpleModule();

        module.addDeserializer(PartitionInfo.class, new PartitionDeserializer());

        module.addDeserializer(RuleSource.class, new RuleDeserializer());
        module.addSerializer(RuleSource.class, new RuleSerializer());

        module.addDeserializer(EvalInfo.class, new EvalDeserializer());

        module.addSerializer(SplitDataInfo.class, new SplitSerializer());

        mapper.registerModule(module);

        return mapper;
    }

    private static String convertYAML(String contents) {
        try {
            ObjectMapper yamlReader = new ObjectMapper(new YAMLFactory());
            Object obj = yamlReader.readValue(contents, Object.class);

            ObjectMapper jsonWriter = new ObjectMapper();
            return jsonWriter.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static interface RuleSource {
        /**
         * Get all the rules represented by this source.
         * Do NOT call until the owning RuntimeConfig has been validated.
         * Predicates need to be instantiated and paths resolved.
         */
        public Iterable<Rule> getRules();

        public void resolvePaths(String relativeBasePath);

        public int size();
    }

    public static class RulePath implements RuleSource {
        public String path;

        public RulePath(String path) {
            this.path = path;
        }

        @Override
        public int size() {
            return (path == null) ? 0 : 1;
        }

        @Override
        public Iterable<Rule> getRules() {
            Model model = null;
            try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
                model = ModelLoader.load(reader);
            } catch (Exception ex) {
                throw new RuntimeException("Failed to load model from file: " + path, ex);
            }

            return model.getRules();
        }

        @Override
        public void resolvePaths(String relativeBasePath) {
            path = FileUtils.makePath(relativeBasePath, path);
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof RulePath)) {
                return false;
            }

            return this.path.equals(((RulePath)other).path);
        }
    }

    public static class RuleList implements RuleSource {
        public List<String> rules;

        public RuleList() {
            rules = new ArrayList<String>();
        }

        public RuleList(List<String> rules) {
            this.rules = rules;
        }

        public RuleList(String... rules) {
            this();

            for (String rule : rules) {
                this.rules.add(rule);
            }
        }

        @Override
        public int size() {
            return (rules == null) ? 0 : rules.size();
        }

        @Override
        public Iterable<Rule> getRules() {
            List<Rule> parsedRules = new ArrayList<Rule>(rules.size());

            for (String rule : rules) {
                try {
                    parsedRules.add(ModelLoader.loadRule(rule));
                } catch (Exception ex) {
                    throw new RuntimeException("Failed to parse rule: " + rule, ex);
                }
            }

            return parsedRules;
        }

        @Override
        public void resolvePaths(String relativeBasePath) {}

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof RuleList)) {
                return false;
            }

            RuleList otherList = (RuleList)other;
            return this.rules.containsAll(otherList.rules) && otherList.rules.containsAll(this.rules);
        }
    }

    public static class SplitConfigInfo {
        public RuleSource rules;
        public Map<String, String> options;

        public SplitConfigInfo() {
            rules = new RuleList();
            options = new HashMap<String, String>();
        }

        public boolean isEmpty() {
            return rules.size() == 0 && options.size() == 0;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof SplitConfigInfo)) {
                return false;
            }

            SplitConfigInfo otherInfo = (SplitConfigInfo)other;
            return this.rules.equals(otherInfo.rules) && this.options.equals(otherInfo.options);
        }
    }

    public static class PredicateConfigInfo {
        public String name;
        public Class<? extends StandardPredicate> type;

        public int arity;
        public List<String> types;

        @JsonProperty("force-open")
        public boolean forceOpen;

        public PartitionInfo observations;
        public PartitionInfo targets;
        public PartitionInfo truth;

        // May be null.
        public String function;

        public List<EvalInfo> evaluations;

        public Map<String, String> options;

        public PredicateConfigInfo() {
            this(null, -1);
        }

        public PredicateConfigInfo(String name, int arity) {
            this.name = name;

            this.arity = arity;
            types = new ArrayList<String>();

            forceOpen = false;

            observations = new PartitionInfo();
            targets = new PartitionInfo();
            truth = new PartitionInfo();

            function = null;

            evaluations = new ArrayList<EvalInfo>();

            options = new HashMap<String, String>();
        }

        public void setType(String name) {
            @SuppressWarnings("unchecked")
            Class<? extends StandardPredicate> suppressWarnings = (Class<? extends StandardPredicate>)Reflection.getClass(name);
            this.type = suppressWarnings;
        }

        public int dataSize() {
            return observations.size() + targets.size() + truth.size();
        }

        /**
         * Is there any data for this predicate explicitly defined to be learn-only?
         */
        public boolean hasExplicitLearnData() {
            return (observations.learn.size() > 0)
                    || (targets.learn.size() > 0)
                    || (truth.learn.size() > 0);
        }

        /**
         * Is there any data for this predicate explicitly defined to be learn-only?
         */
        public boolean hasExplicitValidationData() {
            return (observations.validation.size() > 0)
                    || (targets.validation.size() > 0)
                    || (truth.validation.size() > 0);
        }

        /**
         * Is there any data for this predicate explicitly defined to be infer-only?
         */
        public boolean hasExplicitInferData() {
            return (observations.infer.size() > 0)
                    || (targets.infer.size() > 0)
                    || (truth.infer.size() > 0);
        }

        public void resolvePaths(String relativeBasePath) {
            observations.resolvePaths(relativeBasePath);
            targets.resolvePaths(relativeBasePath);
            truth.resolvePaths(relativeBasePath);
        }

        /**
         * Check if this predicate is open.
         * !useInfer == learn.
         */
        public boolean isOpen(String splitName) {
            return forceOpen
                || (targets.all.size() > 0)
                || (RuntimeConfig.KEY_INFER.equals(splitName) && targets.infer.size() > 0)
                || (RuntimeConfig.KEY_VALIDATION.equals(splitName) && targets.validation.size() > 0)
                || (RuntimeConfig.KEY_LEARN.equals(splitName) && targets.learn.size() > 0);
        }

        /**
         * Get all data paths represented by this predicate.
         */
        public Iterable<String> getAllDataPaths() {
            return IteratorUtils.join(
                    observations.getDataPaths(RuntimeConfig.KEY_LEARN),
                    observations.getDataPaths(RuntimeConfig.KEY_VALIDATION),
                    observations.getDataPaths(RuntimeConfig.KEY_INFER),
                            targets.getDataPaths(RuntimeConfig.KEY_LEARN),
                            targets.getDataPaths(RuntimeConfig.KEY_VALIDATION),
                    targets.getDataPaths(RuntimeConfig.KEY_INFER),
                            truth.getDataPaths(RuntimeConfig.KEY_LEARN),
                            truth.getDataPaths(RuntimeConfig.KEY_VALIDATION),
                    truth.getDataPaths(RuntimeConfig.KEY_INFER));
        }

        /**
         * Get all data points represented by this predicate.
         */
        public Iterable<List<String>> getAllDataPoints() {
            return IteratorUtils.join(
                    observations.getDataPoints(RuntimeConfig.KEY_LEARN),
                    observations.getDataPoints(RuntimeConfig.KEY_VALIDATION),
                    observations.getDataPoints(RuntimeConfig.KEY_INFER),
                            targets.getDataPoints(RuntimeConfig.KEY_LEARN),
                            targets.getDataPoints(RuntimeConfig.KEY_VALIDATION),
                    targets.getDataPoints(RuntimeConfig.KEY_INFER),
                            truth.getDataPoints(RuntimeConfig.KEY_LEARN),
                            truth.getDataPoints(RuntimeConfig.KEY_VALIDATION),
                    truth.getDataPoints(RuntimeConfig.KEY_INFER));
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof PredicateConfigInfo)) {
                return false;
            }

            PredicateConfigInfo otherInfo = (PredicateConfigInfo)other;
            return
                    this.name.equals(otherInfo.name)
                    && (this.arity == otherInfo.arity)
                    && this.types.equals(otherInfo.types)
                    && (this.forceOpen == otherInfo.forceOpen)
                    && this.observations.equals(otherInfo.observations)
                    && this.targets.equals(otherInfo.targets)
                    && this.truth.equals(otherInfo.truth)
                    && ((this.function == null) ? (otherInfo.function == null) : this.function.equals(otherInfo.function))
                    && this.evaluations.equals(otherInfo.evaluations)
                    && this.options.equals(otherInfo.options);
        }
    }

    public static class PartitionInfo {
        public SplitDataInfo all;
        public SplitDataInfo learn;
        public SplitDataInfo validation;
        public SplitDataInfo infer;

        public PartitionInfo() {
            all = new SplitDataInfo();
            learn = new SplitDataInfo();
            validation = new SplitDataInfo();
            infer = new SplitDataInfo();
        }

        public int size() {
            return all.size() + learn.size() + validation.size() + infer.size();
        }

        public void resolvePaths(String relativeBasePath) {
            all.resolvePaths(relativeBasePath);
            learn.resolvePaths(relativeBasePath);
            validation.resolvePaths(relativeBasePath);
            infer.resolvePaths(relativeBasePath);
        }

        /**
         * Get all the paths to data files.
         */
        public Iterable<String> getDataPaths(String splitName) {
            Iterable<String> allPaths = all.paths;

            switch (splitName) {
                case RuntimeConfig.KEY_LEARN:
                    allPaths = IteratorUtils.join(allPaths, learn.paths);
                    break;
                case RuntimeConfig.KEY_VALIDATION:
                    allPaths = IteratorUtils.join(allPaths, validation.paths);
                    break;
                case RuntimeConfig.KEY_INFER:
                    allPaths = IteratorUtils.join(allPaths, infer.paths);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown split name: " + splitName);
            }

            return allPaths;
        }

        /**
         * Get all the embedded data points.
         */
        public Iterable<List<String>> getDataPoints(String splitName) {
            Iterable<List<String>> allPoints = all.data;

            switch (splitName) {
                case RuntimeConfig.KEY_LEARN:
                    allPoints = IteratorUtils.join(allPoints, learn.data);
                    break;
                case RuntimeConfig.KEY_VALIDATION:
                    allPoints = IteratorUtils.join(allPoints, validation.data);
                    break;
                case RuntimeConfig.KEY_INFER:
                    allPoints = IteratorUtils.join(allPoints, infer.data);
                    break;
                default:
                    throw new IllegalArgumentException("Unknown split name: " + splitName);
            }

            return allPoints;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof PartitionInfo)) {
                return false;
            }

            PartitionInfo otherInfo = (PartitionInfo)other;
            return this.all.equals(otherInfo.all) && this.learn.equals(otherInfo.learn) && this.validation.equals(otherInfo.validation) && this.infer.equals(otherInfo.infer);
        }
    }

    public static class SplitDataInfo {
        public List<String> paths;
        public List<List<String>> data;

        public SplitDataInfo() {
            paths = new ArrayList<String>();
            data = new ArrayList<List<String>>();
        }

        public int size() {
            return paths.size() + data.size();
        }

        public void resolvePaths(String relativeBasePath) {
            for (int i = 0; i < paths.size(); i++) {
                paths.set(i, FileUtils.makePath(relativeBasePath, paths.get(i)));
            }
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof SplitDataInfo)) {
                return false;
            }

            SplitDataInfo otherInfo = (SplitDataInfo)other;
            return this.paths.equals(otherInfo.paths) && this.data.equals(otherInfo.data);
        }
    }

    public static class EvalInfo {
        public String evaluator;
        public Map<String, String> options;
        public boolean primary;

        public EvalInfo(String evaluator) {
            this(evaluator, new HashMap<String, String>(), false);
        }

        public EvalInfo(String evaluator, Map<String, String> options, boolean primary) {
            this.evaluator = evaluator;
            this.options = options;
            this.primary = primary;
        }

        @Override
        public boolean equals(Object other) {
            if (other == null || !(other instanceof EvalInfo)) {
                return false;
            }

            EvalInfo otherInfo = (EvalInfo)other;
            return this.evaluator.equals(otherInfo.evaluator) && this.options.equals(otherInfo.options) && this.primary == otherInfo.primary;
        }
    }

    /**
     * A representation for the config only used for parsing.
     */
    private static class JSONRuntimeConfig {
        public RuleSource rules;
        public Map<String, JSONPredicate> predicates;
        public Map<String, String> options;

        public SplitConfigInfo learn;
        public SplitConfigInfo validation;
        public SplitConfigInfo infer;

        /**
         * Convert this config to a RuntimeConfig.
         */
        public RuntimeConfig formalize() {
            RuntimeConfig config = new RuntimeConfig();

            config.options = (this.options == null) ? new HashMap<String, String>() : this.options;
            config.rules = (this.rules == null) ? new RuleList() : this.rules;
            config.learn = (this.learn == null) ? new SplitConfigInfo() : this.learn;
            config.validation = (this.validation == null) ? new SplitConfigInfo() : this.validation;
            config.infer = (this.infer == null) ? new SplitConfigInfo() : this.infer;

            if (this.predicates == null) {
                this.predicates = new HashMap<String, JSONPredicate>();
            }

            config.predicates = new HashMap<String, PredicateConfigInfo>(this.predicates.size());
            for (Map.Entry<String, JSONPredicate> entry : this.predicates.entrySet()) {
                PredicateConfigInfo predicate = entry.getValue().formalize(entry.getKey());
                config.predicates.put(predicate.name, predicate);
            }

            return config;
        }
    }

    /**
     * A representation for predicates only used for parsing.
     */
    private static class JSONPredicate {
        public String name;
        public String type;

        public Integer arity;
        public List<String> types;

        @JsonProperty("force-open")
        public Boolean forceOpen;

        public PartitionInfo observations;
        public PartitionInfo targets;
        public PartitionInfo truth;

        public String function;

        public List<EvalInfo> evaluations;

        public Map<String, String> options;

        /**
         * Convert this predicate info into a formal PredicateConfigInfo and do basic validation.
         */
        public PredicateConfigInfo formalize(String rawName) {
            PredicateConfigInfo config = new PredicateConfigInfo();

            // Properties that do not require any validation/modification.
            config.function = function;

            if (type == null) {
                config.type = StandardPredicate.class;
            } else {
                @SuppressWarnings("unchecked")
                Class<? extends StandardPredicate> suppressWarnings = (Class<? extends StandardPredicate>)Reflection.getClass(type);
                config.type = suppressWarnings;
            }

            config.options = (options == null) ? new HashMap<String, String>() : options;
            config.types = (types == null) ? new ArrayList<String>() : types;
            config.evaluations = (evaluations == null) ? new ArrayList<EvalInfo>() : evaluations;

            config.observations = (observations == null) ? new PartitionInfo() : observations;
            config.targets = (targets == null) ? new PartitionInfo() : targets;
            config.truth = (truth == null) ? new PartitionInfo() : truth;

            config.forceOpen = false;
            if (forceOpen != null && forceOpen.booleanValue()) {
                config.forceOpen = true;
            }

            config.arity = -1;

            if (arity != null) {
                config.arity = arity.intValue();
            }

            if (rawName.contains("/")) {
                String[] parts = rawName.split("/");
                if (parts.length != 2) {
                    throw new IllegalArgumentException("Predicate names may not contain a slash. Offending name: '" + rawName + "'.");
                }

                config.name = parts[0];
                int parsedArity = Integer.parseInt(parts[1]);

                if (config.arity != -1 && config.arity != parsedArity) {
                    throw new IllegalArgumentException(String.format(
                            "Arity mismatch on predicate %s." +
                            " Arity declared as property: %d." +
                            " Arity declared on predicate name: %d.",
                            config.name, config.arity, parsedArity));
                }

                config.arity = parsedArity;
            } else {
                config.name = rawName;
            }

            return config;
        }
    }

    private static class EvalDeserializer extends StdDeserializer<EvalInfo> {
        public EvalDeserializer() {
            this(null);
        }

        public EvalDeserializer(Class cls) {
            super(cls);
        }

        @Override
        public EvalInfo deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode root = jsonParser.getCodec().readTree(jsonParser);
            if (root instanceof TextNode) {
                return new EvalInfo(((TextNode)root).textValue(), new HashMap<String, String>(), false);
            } else if (root instanceof ObjectNode) {
                return parseEvalDef((ObjectNode)root);
            } else {
                throw new IllegalArgumentException("Expecting evaluation value to be a string (class name) or object, found " + root.getClass() + ".");
            }
        }

        private EvalInfo parseEvalDef(ObjectNode root) {
            if (!root.hasNonNull(KEY_EVALUATOR)) {
                throw new IllegalArgumentException("Evalautor object missing the '" + KEY_EVALUATOR + "' key.");
            }

            String evaluator = root.get(KEY_EVALUATOR).textValue();
            Map<String, String> options = null;
            boolean primary = false;

            if (root.hasNonNull(KEY_OPTIONS)) {
                options = parseEvalOptions(root.get(KEY_OPTIONS));
            } else {
                options = new HashMap<String, String>();
            }

            if (root.hasNonNull(KEY_PRIMARY)) {
                primary = root.get(KEY_PRIMARY).booleanValue();
            }

            return new EvalInfo(evaluator, options, primary);
        }

        private Map<String, String> parseEvalOptions(JsonNode root) {
            if (!(root instanceof ObjectNode)) {
                throw new IllegalArgumentException("Expecting evaluation options to be an object, found " + root.getClass() + ".");
            }

            ObjectMapper mapper = getMapper();
            JavaType mapType = mapper.getTypeFactory().constructMapLikeType(Map.class, String.class, String.class);

            try {
                Map rawMap = mapper.treeToValue(root, Map.class);
                return mapper.convertValue(rawMap, mapType);
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    private static class SplitSerializer extends StdSerializer<SplitDataInfo> {
        public SplitSerializer() {
            this(null);
        }

        public SplitSerializer(Class<SplitDataInfo> cls) {
            super(cls);
        }

        @Override
        public void serialize(SplitDataInfo value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            List<Object> values = new ArrayList<Object>(value.paths.size() + value.data.size());
            values.addAll(value.paths);
            values.addAll(value.data);

            generator.writeObject(values);
        }
    }

    private static class RuleSerializer extends StdSerializer<RuleSource> {
        public RuleSerializer() {
            this(null);
        }

        public RuleSerializer(Class<RuleSource> cls) {
            super(cls);
        }

        @Override
        public void serialize(RuleSource value, JsonGenerator generator, SerializerProvider provider) throws IOException {
            if (value instanceof RulePath) {
                generator.writeString(((RulePath)value).path);
            } else if (value instanceof RuleList) {
                generator.writeObject(((RuleList)value).rules);
            } else {
                throw new IllegalStateException("Unknown RuleSource subtype: " + value.getClass());
            }
        }
    }

    private static class RuleDeserializer extends StdDeserializer<RuleSource> {
        public RuleDeserializer() {
            this(null);
        }

        public RuleDeserializer(Class<RuleSource> cls) {
            super(cls);
        }

        @Override
        public RuleSource deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode root = jsonParser.getCodec().readTree(jsonParser);

            if (root instanceof ArrayNode) {
                List<String> rules = new ArrayList<String>();
                for (JsonNode ruleNode : (ArrayNode)root) {
                    if (!(ruleNode instanceof TextNode)) {
                        throw new IllegalArgumentException(
                                "Expecting rule array to only contain strings, found " + ruleNode.getClass() + ".");
                    }

                    rules.add(((TextNode)ruleNode).textValue());
                }

                return new RuleList(rules);
            } else if (root instanceof TextNode) {
                return new RulePath(((TextNode)root).textValue());
            }

            throw new IllegalArgumentException("Expecting rule value to be an array or string (path), found " + root.getClass() + ".");
        }
    }

    private static class PartitionDeserializer extends StdDeserializer<PartitionInfo> {
        public PartitionDeserializer() {
            this(null);
        }

        public PartitionDeserializer(Class<PartitionInfo> cls) {
            super(cls);
        }

        @Override
        public PartitionInfo deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JsonNode root = jsonParser.getCodec().readTree(jsonParser);

            PartitionInfo partition = new PartitionInfo();

            if (root instanceof ArrayNode) {
                parseDataSpec((ArrayNode)root, partition.all);
            } else if (root instanceof ObjectNode) {
                for (Map.Entry<String, JsonNode> entry : IteratorUtils.newIterable(((ObjectNode)root).fields())) {
                    if (!(entry.getValue() instanceof ArrayNode)) {
                        throw new IllegalStateException("Expecting split value to be an array, found " + entry.getValue().getClass() + ".");
                    }

                    if (entry.getKey().equals(KEY_ALL)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.all);
                    } else if (entry.getKey().equals(KEY_LEARN)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.learn);
                    } else if (entry.getKey().equals(KEY_VALIDATION)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.validation);
                    } else if (entry.getKey().equals(KEY_INFER)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.infer);
                    } else {
                        throw new IllegalStateException(String.format(
                                "Unknown split type (%s). Expecting one of [%s, %s, %s, %s].",
                                entry.getKey(),
                                KEY_ALL, KEY_LEARN, KEY_VALIDATION, KEY_INFER));
                    }
                }
            } else {
                throw new IllegalArgumentException("Expecting partition value to be an array or object, found " + root.getClass() + ".");
            }

            return partition;
        }

        private void parseDataSpec(ArrayNode root, SplitDataInfo split) {
            for (JsonNode element : root) {
                if (element instanceof TextNode) {
                    split.paths.add(((TextNode)element).textValue());
                } else if (element instanceof ArrayNode) {
                    List<String> values = new ArrayList<String>(((ArrayNode)element).size());
                    for (JsonNode value : element) {
                        if (!(value instanceof ValueNode)) {
                            throw new IllegalStateException("Literal data should only be simple types, found " + value.getClass() + ".");
                        }

                        values.add(value.asText());
                    }
                    split.data.add(values);
                } else {
                    throw new IllegalStateException("Data specifications must be strings (file paths) or arrays (literal data), found " + element.getClass() + ".");
                }
            }
        }
    }

    /**
     * when preting pretty, skip null/empty values.
     */
    private static class EmptyValueFilter {
        @Override
        public boolean equals(Object value) {
            if (value == null) {
                return true;
            }

            if ((value instanceof Collection) && (((Collection)value).size() == 0)) {
                return true;
            }

            if ((value instanceof Map) && (((Map)value).size() == 0)) {
                return true;
            }

            if ((value instanceof SplitDataInfo) && (((SplitDataInfo)value).size() == 0)) {
                return true;
            }

            if ((value instanceof PartitionInfo) && (((PartitionInfo)value).size() == 0)) {
                return true;
            }

            return false;
        }
    }

    public static void main(String[] args) {
        RuntimeConfig config = RuntimeConfig.fromFile(args[0]);
        System.out.println(config.toString(true));
        RuntimeConfig.fromJSON(config.toString());
    }
}
