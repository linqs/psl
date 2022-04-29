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
package org.linqs.psl.cli;

import org.linqs.psl.database.DataStore;
import org.linqs.psl.database.Partition;
import org.linqs.psl.database.loading.Inserter;
import org.linqs.psl.model.function.ExternalFunction;
import org.linqs.psl.model.predicate.ExternalFunctionalPredicate;
import org.linqs.psl.model.predicate.model.ModelPredicate;
import org.linqs.psl.model.predicate.model.SupportingModel;
import org.linqs.psl.model.predicate.Predicate;
import org.linqs.psl.model.predicate.StandardPredicate;
import org.linqs.psl.model.term.ConstantType;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.Reflection;

import org.apache.commons.configuration2.YAMLConfiguration;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Parse CLI data files and load all the predicates and data.
 */
public class DataLoader {
    public static final String KEY_PREDICATE = "predicates";

    public static final String KEY_PARTITION_OBS = "observations";
    public static final String KEY_PARTITION_TARGETS = "targets";
    public static final String KEY_PARTITION_TRUTH = "truth";

    public static final String PROPERTY_OPEN = "open";
    public static final String PROPERTY_CLOSED = "closed";
    public static final String PROPERTY_TYPES = "types";
    public static final String PROPERTY_BLOCK = "block";
    public static final String PROPERTY_FUNCTION = "implementation";
    public static final String PROPERTY_MODEL_TYPE = "modeltype";
    public static final String PROPERTY_CONFIG = "config";

    public static final Set<String> TOP_LEVEL_PROPS = Collections.unmodifiableSet(new HashSet<String>(Arrays.asList(
            new String[]{KEY_PREDICATE, KEY_PARTITION_OBS, KEY_PARTITION_TARGETS, KEY_PARTITION_TRUTH})));

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    public static Set<StandardPredicate> load(DataStore dataStore, String path, boolean useIntIds)
            throws ConfigurationException, FileNotFoundException {
        YAMLConfiguration yaml = new YAMLConfiguration();
        yaml.read(FileUtils.getInputStreamReader(path));

        // Make sure to get the absolute path so we can take the parent.
        path = (new File(path)).getAbsolutePath();

        // All non-absolute paths should be relative to the data file.
        String relativeDir = (new File(path)).getParentFile().getAbsolutePath();

        validate(yaml);

        // Fetch the predicates.
        Set<StandardPredicate> closedPredicates = parsePredicates(yaml, useIntIds, dataStore, relativeDir);

        // Load the partitions.
        loadPartitions(yaml, dataStore, relativeDir);

        return closedPredicates;
    }

    /**
     * Top level validations of the YAML.
     */
    private static void validate(YAMLConfiguration yaml) {
        Iterator<String> keyIterator = yaml.getKeys();
        while (keyIterator.hasNext()) {
            String key = keyIterator.next();

            String[] parts = key.split("\\.", 2);
            if (parts.length < 2) {
                throw new IllegalArgumentException("Bad key in data file: " + key);
            }

            String prefix = parts[0];
            if (!TOP_LEVEL_PROPS.contains(prefix)) {
                throw new IllegalArgumentException("Unknown top-level key in data file: " + prefix);
            }
        }
    }

    private static void loadPartitions(YAMLConfiguration yaml, DataStore dataStore, String relativeDir) {
        String[] partitions = new String[]{
            KEY_PARTITION_OBS,
            KEY_PARTITION_TARGETS,
            KEY_PARTITION_TRUTH
        };

        for (String partition : partitions) {
            Iterator<String> keyIterator = yaml.getKeys(partition);
            while (keyIterator.hasNext()) {
                String key = keyIterator.next();
                Object value = yaml.getProperty(key);

                String predicate = key.replaceFirst("^" + partition + ".", "");

                if (value instanceof String) {
                    loadData(partition, predicate, dataStore, (String)value, relativeDir);
                } else if (value instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Object> listValues = (List<Object>)value;
                    for (Object listValue : listValues) {
                        if (!(listValue instanceof String)) {
                            throw new IllegalArgumentException(String.format("Expected a string property to key, %s, found: '%s'.", key, listValue.getClass().getName()));
                        }

                        loadData(partition, predicate, dataStore, (String)listValue, relativeDir);
                    }
                } else {
                    throw new IllegalArgumentException(String.format("Key, %s, has an unrecognized type: '%s'.", key, value.getClass().getName()));
                }
            }
        }
    }

    private static void loadData(String partitionName, String predicateName, DataStore dataStore, String path, String relativeDir) {
        Predicate rawPredicate = Predicate.get(predicateName);
        if (rawPredicate == null) {
            throw new IllegalArgumentException(String.format("Non-existent predicate (%s) declared in the %s partition without first being defined in the 'predicates' section of the data file.", predicateName, partitionName));
        }

        if (rawPredicate instanceof ExternalFunctionalPredicate) {
            throw new IllegalArgumentException(String.format("Cannot load data into a function predicate (%s). See %s partition in the data file.", predicateName, partitionName));
        }

        Partition partition = dataStore.getPartition(partitionName);
        StandardPredicate predicate = StandardPredicate.get(predicateName);

        log.debug("Loading data for {} ({} partition)", predicateName, partitionName);
        Inserter insert = dataStore.getInserter(predicate, partition);
        insert.loadDelimitedDataAutomatic(makePath(relativeDir, path));
    }

    private static Set<StandardPredicate> parsePredicates(YAMLConfiguration yaml, boolean useIntIds, DataStore dataStore, String relativeDir) {
        Set<StandardPredicate> closedPredicates = new HashSet<StandardPredicate>();

        boolean foundPredicate = false;
        Iterator<String> keyIterator = yaml.getKeys(KEY_PREDICATE);
        while (keyIterator.hasNext()) {
            foundPredicate = true;

            String key = keyIterator.next();
            Object rawValue = yaml.getProperty(key);

            String predicateName = key.replaceFirst("^" + KEY_PREDICATE + ".", "");

            List<Object> values = null;
            if (rawValue instanceof String) {
                values = new ArrayList<Object>();
                values.add((String)rawValue);
            } else if (rawValue instanceof List) {
                @SuppressWarnings("unchecked")
                List<Object> ignoreCompilerWarning = (List<Object>)rawValue;
                values = ignoreCompilerWarning;
            } else {
                throw new IllegalStateException(String.format("Predicate, %s, has an unknown value type: %s.", predicateName, rawValue.getClass().getName()));
            }

            parsePredicate(predicateName, values, useIntIds, dataStore, closedPredicates, relativeDir);
        }

        if (!foundPredicate) {
            throw new IllegalStateException(String.format("Found no predicates. Predicates must be defined under the '%s' key.", KEY_PREDICATE));
        }

        return closedPredicates;
    }

    private static void parsePredicate(String name, List<Object> properties, boolean useIntIds, DataStore dataStore, Set<StandardPredicate> closedPredicates, String relativeDir) {
        int arity = -1;
        Boolean isClosed = null;
        boolean isBlock = false;
        String externalFunctionImplementation = null;
        String modelType = null;
        Map<String, String> config = new HashMap<String, String>();
        List<ConstantType> types = new ArrayList<ConstantType>();

        if (name.contains("/")) {
            String[] parts = name.split("/");
            if (parts.length != 2) {
                throw new IllegalArgumentException("Predicate names may not contain a slash. Offending name: '" + name + "'.");
            }

            name = parts[0];
            arity = Integer.parseInt(parts[1]);
        }

        for (Object property : properties) {
            if (property instanceof String) {
                String stringProperty = (String)property;
                if (stringProperty.equals(PROPERTY_OPEN)) {
                    isClosed = Boolean.valueOf(false);
                } else if (stringProperty.equals(PROPERTY_CLOSED)) {
                    isClosed = Boolean.valueOf(true);
                } else if (stringProperty.equals(PROPERTY_BLOCK)) {
                    isBlock = true;
                } else {
                    throw new IllegalStateException(String.format("Predicate, %s, has an unknown property: '%s'.", name, stringProperty));
                }
            } else if (property instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mapProperty = (Map<String, Object>)property;
                for (Map.Entry<String, Object> propEntry : mapProperty.entrySet()) {
                    String key = propEntry.getKey();
                    Object rawValue = propEntry.getValue();

                    if (key.equals(PROPERTY_TYPES)) {
                        @SuppressWarnings("unchecked")
                        List<String> rawTypes = (List<String>)(rawValue);

                        for (String rawType : rawTypes) {
                            types.add(ConstantType.valueOf(rawType));
                        }

                        if (arity != -1 && arity != types.size()) {
                            throw new IllegalArgumentException(String.format("Size mismatch on predicate %s. Declared arity: %d. Length of supplied types: %d.", name, arity, types.size()));
                        }

                        arity = types.size();
                    } else if (key.equals(PROPERTY_FUNCTION)) {
                        if (!(rawValue instanceof String)) {
                            throw new IllegalStateException(String.format("Predicate, %s, has a function with an unknown type (%s). The value should be the target class name (as a string).", name, rawValue.getClass().getName()));
                        }

                        externalFunctionImplementation = (String)rawValue;
                    } else if (key.equals(PROPERTY_MODEL_TYPE)) {
                        if (!(rawValue instanceof String)) {
                            throw new IllegalStateException(String.format("Predicate, %s, has a model type key with an unknown type (%s), should be a string.", name, rawValue.getClass().getName()));
                        }

                        modelType = (String)rawValue;
                    } else if (key.equals(PROPERTY_CONFIG)) {
                        if (!(rawValue instanceof Map)) {
                            throw new IllegalStateException(String.format("Predicate, %s, has a config key with an unknown type (%s), should be a Map<String, String>.", name, rawValue.getClass().getName()));
                        }

                        @SuppressWarnings("unchecked")
                        Map<String, Object> rawMap = (Map<String, Object>)rawValue;

                        for (Map.Entry<String, Object> entry : rawMap.entrySet()) {
                            config.put(entry.getKey(), entry.getValue().toString());
                        }
                    } else {
                        throw new IllegalStateException(String.format("Predicate, %s, has an unknown property: '%s'.", name, key));
                    }
                }
            } else {
                throw new IllegalStateException(String.format("Property of predicate, %s, has an unknown type: %s.", name, property.getClass().getName()));
            }
        }

        // If this is a functional predicate, then instantiate it and bail out early.
        if (externalFunctionImplementation != null) {
            if (isBlock) {
                throw new IllegalArgumentException(String.format("Functional predicates (%s) cannot be blocks."));
            }

            ExternalFunctionalPredicate.get(name, (ExternalFunction)(Reflection.newObject(externalFunctionImplementation)));
            return;
        }

        if (arity == -1) {
            throw new IllegalArgumentException(String.format("Could not find arity for predicate: %s", name));
        }

        if (isClosed == null) {
            throw new IllegalArgumentException(String.format("Closed/open not specified for predicate: %s", name));
        }

        if (types.size() == 0) {
            for (int i = 0; i < arity; i++) {
                if (useIntIds) {
                    types.add(ConstantType.UniqueIntID);
                } else {
                    types.add(ConstantType.UniqueStringID);
                }
            }
        }

        StandardPredicate predicate = null;
        if (modelType != null) {
            String modelClassName = Reflection.resolveClassName(modelType);
            if (modelClassName == null) {
                // Try one more time, but assuming the given name was short and we know the package.
                // This is because supporting models may be outside of psl-core and thus not resolvable.
                modelClassName = Reflection.resolveClassName("org.linqs.psl.model.predicate.model." + modelType);
                if (modelClassName == null) {
                    throw new IllegalArgumentException("Unable to resolve the model type: " + modelType);
                }
            }

            SupportingModel model = (SupportingModel)Reflection.newObject(modelClassName);

            predicate = ModelPredicate.get(name, model, types.toArray(new ConstantType[0]));
            ((ModelPredicate)predicate).loadModel(config, relativeDir);
        } else {
            predicate = StandardPredicate.get(name, types.toArray(new ConstantType[0]));
        }

        predicate.setBlock(isBlock);
        dataStore.registerPredicate(predicate);

        if (isClosed.booleanValue()) {
            closedPredicates.add(predicate);

            if (predicate instanceof ModelPredicate) {
                throw new IllegalArgumentException(String.format("Model predicates (%s) cannot be closed.", predicate));
            }
        }
    }

    /**
     * Construct a path to the given file relative to the data file.
     * If the given path is absolute, then don't change it.
     */
    private static String makePath(String relativeDir, String basePath) {
        if (Paths.get(basePath).isAbsolute()) {
            return basePath;
        }

        return Paths.get(relativeDir, basePath).toString();
    }
}
