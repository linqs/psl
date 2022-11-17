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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.databind.node.ValueNode;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A configuration continaer that describes how a runtime should operate.
 * Note that this class just represents what was provided as a configuration,
 * not a fully validated configuration.
 *
 * Any options set in a RuntimeConfig will override options set in psl.properties,
 * but will be overwritten by command-line/one-off options.
 *
 * This config is only meant to be applied for the duration of the relevant runtime.
 */
public class RuntimeConfig {
    public static final String KEY_RULES = "rules";

    public static final String KEY_ALL = "all";
    public static final String KEY_WL = "learn";
    public static final String KEY_EVAL = "eval";

    public static final String KEY_EVALAUTOR = "evaluator";
    public static final String KEY_OPTIONS = "options";

    public RuleSource rules;
    public Map<String, PredicateConfigInfo> predicates;
    public Map<String, String> options;

    public SplitConfigInfo eval;
    public SplitConfigInfo learn;

    public RuntimeConfig() {
        rules = null;
        predicates = new HashMap<String, PredicateConfigInfo>();
        options = new HashMap<String, String>();
    }

    @Override
    public String toString() {
        ObjectMapper mapper = getMapper();

        JsonInclude.Value includeValue = JsonInclude.Value.empty()
                .withValueInclusion(JsonInclude.Include.CUSTOM)
                .withValueFilter(EmptyValueFilter.class);

        mapper.setDefaultPropertyInclusion(includeValue);

        try {
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(this);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    public static RuntimeConfig fromFile(String path) {
        if (path.toLowerCase().endsWith(".json")) {
            return RuntimeConfig.fromJSON(FileUtils.readFileAsString(path));
        } else if (path.toLowerCase().endsWith(".yaml")) {
            return RuntimeConfig.fromJSON(convertYAML(FileUtils.readFileAsString(path)));
        }

        throw new IllegalArgumentException("Expected runtime config file to end  in '.json' or '.yaml'.");
    }

    private static RuntimeConfig fromJSON(String contents) {
        JSONRuntimeConfig baseConfig = parseJSON(contents);

        RuntimeConfig config = new RuntimeConfig();
        config.convertBaseConfig(baseConfig);

        return config;
    }

    private void convertBaseConfig(JSONRuntimeConfig baseConfig) {
        this.options = baseConfig.options;
        this.rules = baseConfig.rules;
        this.learn = baseConfig.learn;
        this.eval = baseConfig.eval;

        predicates = new HashMap<String, PredicateConfigInfo>(baseConfig.predicates.size());
        for (Map.Entry<String, JSONPredicate> entry : baseConfig.predicates.entrySet()) {
            PredicateConfigInfo predicate = entry.getValue().formalize(entry.getKey());
            this.predicates.put(predicate.name, predicate);
        }
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
                .enable(JsonReadFeature.ALLOW_LEADING_DECIMAL_POINT_FOR_NUMBERS)
                .enable(JsonReadFeature.ALLOW_TRAILING_DECIMAL_POINT_FOR_NUMBERS)
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

    public static interface RuleSource {}

    public static class RulePath implements RuleSource {
        public String path;

        public RulePath(String path) {
            this.path = path;
        }
    }

    public static class RuleStrings implements RuleSource {
        public List<String> rules;

        public RuleStrings(List<String> rules) {
            this.rules = rules;
        }
    }

    public static class SplitConfigInfo {
        public RuleSource rules;
        public Map<String, String> options;
    }

    public static class PredicateConfigInfo {
        public String name;

        public int arity;
        public List<String> types;

        @JsonProperty("force-open")
        public boolean forceOpen;

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
        public SplitDataInfo all;
        public SplitDataInfo learn;
        public SplitDataInfo eval;

        public PartitionInfo() {
            all = new SplitDataInfo();
            learn = new SplitDataInfo();
            eval = new SplitDataInfo();
        }

        public int size() {
            return all.size() + learn.size() + eval.size();
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
    }

    public static class EvalInfo {
        public String evaluator;
        public Map<String, String> options;

        public EvalInfo(String evaluator, Map<String, String> options) {
            this.evaluator = evaluator;
            this.options = options;
        }
    }

    private static class JSONRuntimeConfig {
        public RuleSource rules;
        public Map<String, JSONPredicate> predicates;
        public Map<String, String> options;

        public SplitConfigInfo eval;
        public SplitConfigInfo learn;

        /**
         * Convert this config to a RuntimeConfig.
         */
        public void formalize(RuntimeConfig config) {
            config.options = this.options;
            config.rules = this.rules;
            config.learn = this.learn;
            config.eval = this.eval;

            config.predicates = new HashMap<String, PredicateConfigInfo>(this.predicates.size());
            for (Map.Entry<String, JSONPredicate> entry : this.predicates.entrySet()) {
                PredicateConfigInfo predicate = entry.getValue().formalize(entry.getKey());
                config.predicates.put(predicate.name, predicate);
            }
        }
    }

    private static class JSONPredicate {
        public String name;

        public Integer arity;
        public List<String> types;

        @JsonProperty("force-open")
        public Boolean forceOpen;

        public PartitionInfo observations;
        public PartitionInfo targets;
        public PartitionInfo truth;

        public String function;
        public String model;

        public List<EvalInfo> evaluations;

        public Map<String, String> options;

        /**
         * Convert this predicate info into a formal PredicateConfigInfo and do basic validation.
         */
        public PredicateConfigInfo formalize(String rawName) {
            PredicateConfigInfo config = new PredicateConfigInfo();

            // Properties that do not require any validation/modification.
            config.options = options;
            config.observations = observations;
            config.targets = targets;
            config.truth = truth;
            config.function = function;
            config.model = model;
            config.evaluations = evaluations;

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
                return new EvalInfo(((TextNode)root).textValue(), new HashMap<String, String>());
            } else if (root instanceof ObjectNode) {
                return parseEvalDef((ObjectNode)root);
            } else {
                throw new IllegalArgumentException("Expecting evaluation value to be a string (class name) or object, found " + root.getClass() + ".");
            }
        }

        private EvalInfo parseEvalDef(ObjectNode root) {
            if (!root.hasNonNull(KEY_EVALAUTOR)) {
                throw new IllegalArgumentException("Evalautor object missing the '" + KEY_EVALAUTOR + "' key.");
            }

            String evaluator = root.get(KEY_EVALAUTOR).textValue();
            Map<String, String> options = null;

            if (root.hasNonNull(KEY_OPTIONS)) {
                options = parseEvalOptions(root.get(KEY_OPTIONS));
            } else {
                options = new HashMap<String, String>();
            }

            return new EvalInfo(evaluator, options);
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
            } else if (value instanceof RuleStrings) {
                generator.writeObject(((RuleStrings)value).rules);
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

                return new RuleStrings(rules);
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
                    } else if (entry.getKey().equals(KEY_WL)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.learn);
                    } else if (entry.getKey().equals(KEY_EVAL)) {
                        parseDataSpec((ArrayNode)entry.getValue(), partition.eval);
                    } else {
                        throw new IllegalStateException(String.format(
                                "Unknown split type (%s). Expecting one of [%s, %s, %s].",
                                entry.getKey(),
                                KEY_ALL, KEY_WL, KEY_EVAL));
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

            return false;
        }
    }

    public static void main(String[] args) {
        RuntimeConfig config = RuntimeConfig.fromFile(args[0]);
        System.out.println(config);
        RuntimeConfig.fromJSON(config.toString());
    }
}
