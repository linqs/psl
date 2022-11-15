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

import org.linqs.psl.evaluation.statistics.Evaluator;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.rule.Rule;
import org.linqs.psl.parser.ModelLoader;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.Logger;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A configuration that describes how a runtime should operate.
 * Note that this class just represents what was provided as a configuration,
 * not a fully validated configuration.
 *
 * Any options set in a RuntimeConfig will override options set in psl.properties,
 * but will be overwritten by command-line/one-off options.
 *
 * This config is only meant to be applied for the duration of the relevant runtime.
 */
public class RuntimeConfig {
    // TEST
    private static final Logger log = Logger.getLogger(RuntimeConfig.class);

    public static final String KEY_RULES = "rules";

    public static final String KEY_ALL = "all";
    public static final String KEY_WL = "wl";
    public static final String KEY_EVAL = "eval";

    private List<String> rules;
    private List<PredicateConfigInfo> predicates;
    private Map<String, String> options;

    /**
     * Only static constructors are to be called.
     */
    private RuntimeConfig(String contents) {
        JSONRuntimeConfig baseConfig = parseJSON(contents);
        convertBaseConfig(baseConfig);
    }

    public static RuntimeConfig fromFile(String path) {
        if (path.toLowerCase().endsWith(".json")) {
            return new RuntimeConfig(FileUtils.readFileAsString(path));
        } else if (path.toLowerCase().endsWith(".yaml")) {
            return new RuntimeConfig(convertYAML(FileUtils.readFileAsString(path)));
        }

        throw new IllegalArgumentException("Expected runtime config file to end  in '.json' or '.yaml'.");
    }

    private void convertBaseConfig(JSONRuntimeConfig baseConfig) {
        this.options = baseConfig.options;
        this.rules = baseConfig.rules;

        predicates = new ArrayList<PredicateConfigInfo>(baseConfig.predicates.size());
        for (Map.Entry<String, JSONPredicate> entry : baseConfig.predicates.entrySet()) {
            predicates.add(entry.getValue().formalize(entry.getKey()));
        }
    }

    private JSONRuntimeConfig parseJSON(String contents) {
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
                .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_TRAILING_COMMA)
                .build();

        SimpleModule module = new SimpleModule();
        module.addDeserializer(JSONPartition.class, new JSONPartition());
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

    private static class PredicateConfigInfo {
        public String name;
        public int arity;

        public PartitionInfo observations;
        public PartitionInfo targets;
        public PartitionInfo truth;

        public String function;
        public String model;

        public List<EvalInfo> evaluations;
        public Map<String, String> options;

        public int size() {
            return observations.size() + targets.size() + truth.size();
        }
    }

    public static class PartitionInfo {
        public SplitInfo all;
        public SplitInfo wl;
        public SplitInfo eval;

        public int size() {
            return all.size() + wl.size() + eval.size();
        }
    }

    public static class SplitInfo {
        public Boolean forceOpen;
        public List<String> paths;
        public List<List<String>> data;

        public SplitInfo(Boolean forceOpen, List<String> paths, List<List<String>> data) {
            this.forceOpen = forceOpen;
            this.paths = paths;
            this.data = data;
        }

        public int size() {
            return paths.size() + data.size();
        }

        public boolean isClosed() {
            if (forceOpen != null) {
                return !forceOpen.booleanValue();
            }

            return paths.isEmpty() && data.isEmpty();
        }
    }

    public static class EvalInfo {
        public String evaluator;
        public Map<String, String> options;

        public EvalInfo(String evaluator, Map<String, String> options) {
            this.evaluator = evaluator;
            this.options = options;
        }
    }

    // Classes only for initial deserialization.
    // Once deserialized, the Objects here will be more thoroughly parsed.

    private static class JSONRuntimeConfig {
        public List<String> rules;
        public Map<String, JSONPredicate> predicates;
        public Map<String, String> options;
    }

    private static class JSONPredicate {
        public Integer arity;
        public Boolean open;
        public List<String> types;
        public Map<String, String> options;

        public String implementation;
        public String modeltype;

        public JSONPartition observations;
        public JSONPartition targets;
        public JSONPartition truth;

        @JsonDeserialize(using = EvalDeserializer.class)
        public List<EvalInfo> evaluation;

        public PredicateConfigInfo formalize(String rawName) {
            PredicateConfigInfo config = new PredicateConfigInfo();
            config.options = options;

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

            if (types != null) {
                if (config.arity != -1 && types.size() != config.arity) {
                    throw new IllegalArgumentException(String.format(
                            "Arity mismatch on predicate %s." +
                            " Declared arity: %d." +
                            " Length of supplied types: %d.",
                            config.name, config.arity, types.size()));
                }

                config.arity = types.size();
            }

            if (config.arity == -1) {
                throw new IllegalArgumentException(String.format("Could not find arity for predicate: %s.", config.name));
            }

            if (observations != null) {
                config.observations = observations.formalize(open);
            }

            if (targets != null) {
                config.targets = targets.formalize(open);
            }

            if (truth != null) {
                config.truth = truth.formalize(open);
            }

            config.function = implementation;
            config.model = modeltype;
            config.evaluations = evaluation;

            return config;
        }
    }

    private static class JSONPartition extends StdDeserializer<JSONPartition> {
        public List<List<String>> allData;
        public List<String> allFiles;

        public List<List<String>> evalData;
        public List<String> evalFiles;

        public List<List<String>> wlData;
        public List<String> wlFiles;

        public JSONPartition() {
            this(null);
        }

        public JSONPartition(Class cls) {
            super(cls);

            allData = new ArrayList<List<String>>();
            allFiles = new ArrayList<String>();

            evalData = new ArrayList<List<String>>();
            evalFiles = new ArrayList<String>();

            wlData = new ArrayList<List<String>>();
            wlFiles = new ArrayList<String>();
        }

        public PartitionInfo formalize(Boolean forceOpen) {
            PartitionInfo info = new PartitionInfo();

            info.all = new SplitInfo(forceOpen, allFiles, allData);
            info.wl = new SplitInfo(forceOpen, wlFiles, wlData);
            info.eval = new SplitInfo(forceOpen, evalFiles, evalData);

            return info;
        }

        @Override
        public JSONPartition deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            JSONPartition partition = new JSONPartition();

            JsonNode root = jsonParser.getCodec().readTree(jsonParser);

            if (root instanceof ArrayNode) {
                parseDataSpec((ArrayNode)root, partition.allData, partition.allFiles);
            } else if (root instanceof ObjectNode) {
                for (Map.Entry<String, JsonNode> entry : IteratorUtils.newIterable(((ObjectNode)root).fields())) {
                    if (!(entry.getValue() instanceof ArrayNode)) {
                        throw new IllegalStateException("Expecting split value to be an array, found " + entry.getValue().getClass() + ".");
                    }

                    if (entry.getKey().equals(KEY_ALL)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.allData, partition.allFiles);
                    } else if (entry.getKey().equals(KEY_WL)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.wlData, partition.wlFiles);
                    } else if (entry.getKey().equals(KEY_EVAL)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.evalData, partition.evalFiles);
                    } else {
                        throw new IllegalStateException(String.format(
                                "Unknown split type (%s). Expecting one of [%s, %s, %s].",
                                entry.getKey(),
                                KEY_ALL, KEY_WL, KEY_EVAL));
                    }
                }
            } else {
                throw new IllegalStateException("Expecting partition value to be an array or map, found " + root.getClass() + ".");
            }

            return partition;
        }

        private void parseDataSpec(ArrayNode root, List<List<String>> data, List<String> files) {
            for (JsonNode element : root) {
                if (element instanceof TextNode) {
                    files.add(((TextNode)element).textValue());
                } else if (element instanceof ArrayNode) {
                    List<String> values = new ArrayList<String>(((ArrayNode)element).size());
                    for (JsonNode value : element) {
                        if (!(value instanceof ValueNode)) {
                            throw new IllegalStateException("Literal data should only be simple types, found " + value.getClass() + ".");
                        }

                        values.add(value.asText());
                    }
                    data.add(values);
                } else {
                    throw new IllegalStateException("Data specifications must be strings (file paths) or arrays (literal data), found " + element.getClass() + ".");
                }
            }
        }
    }

    private static class EvalDeserializer extends StdDeserializer<List<EvalInfo>> {
        public EvalDeserializer() {
            this(null);
        }

        public EvalDeserializer(Class cls) {
            super(cls);
        }

        @Override
        public List<EvalInfo> deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            List<EvalInfo> evals = new ArrayList<EvalInfo>();

            JsonNode root = jsonParser.getCodec().readTree(jsonParser);
            if (root instanceof ArrayNode) {
                for (JsonNode evalDef : (ArrayNode)root) {
                    parseEvalDef(evalDef, evals);
                }
            } else if (root instanceof ObjectNode) {
                parseEvalDef(root, evals);
            } else {
                throw new IllegalStateException("Expecting evaluation value to be an array or map, found " + root.getClass() + ".");
            }

            return evals;
        }

        private void parseEvalDef(JsonNode root, List<EvalInfo> evals) {
            if (!(root instanceof ObjectNode)) {
                throw new IllegalStateException("Expecting evaluation definition to be a map, found " + root.getClass() + ".");
            }

            for (Map.Entry<String, JsonNode> entry : IteratorUtils.newIterable(((ObjectNode)root).fields())) {
                Map<String, String> options = parseEvalOptions(entry.getValue());
                if (options != null) {
                    evals.add(new EvalInfo(entry.getKey(), options));
                }
            }
        }

        private Map<String, String> parseEvalOptions(JsonNode root) {
            if (root.isBoolean() && !root.booleanValue()) {
                return null;
            }

            if (root.isBoolean()) {
                return new HashMap<String, String>();
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

    // TEST
    public static void main(String[] args) {
        RuntimeConfig.fromFile(args[0]);
    }
}
