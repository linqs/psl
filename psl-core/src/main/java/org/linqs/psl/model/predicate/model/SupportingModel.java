/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2021 The Regents of the University of California
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
package org.linqs.psl.model.predicate.model;

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A model that is used by a ModelPredicate.
 */
public abstract class SupportingModel {
    private static final Logger log = LoggerFactory.getLogger(SupportingModel.class);

    public static final String DELIM = "\t";

    public static final String CONFIG_FEATURES = "features";
    public static final String CONFIG_LABELS = "labels";
    public static final String CONFIG_MODEL = "model";
    public static final String CONFIG_OBSERVATIONS = "observations";

    /**
     * The indexes of this predicate that compose the entity ID.
     */
    protected int[] entityArgumentIndexes;

    /**
     * The indexes of this predicate that compose the label ID.
     */
    protected int[] labelArgumentIndexes;

    /**
     * Map the ID for an entity to an index.
     * Note that multiple atoms can have the same entity ID.
     */
    protected Map<String, Integer> entityIndexMapping;

    /**
     * Map the ID for a label to its index in the output layer.
     */
    protected Map<String, Integer> labelIndexMapping;

    private int numFeatures;

    /**
     * Labels manually set by the reasoner to use for fitting.
     * Includes both observed and unobsered data points.
     * [entities x labels]
     */
    protected float[][] manualLabels;

    /**
     * Labels that are observed.
     * {entity index: labels}
     */
    protected Map<Integer, float[]> observedLabels;

    public void close() {
        if (entityIndexMapping != null) {
            entityIndexMapping.clear();
            entityIndexMapping = null;
        }

        if (labelIndexMapping != null) {
            labelIndexMapping.clear();
            labelIndexMapping = null;
        }

        if (observedLabels != null) {
            observedLabels.clear();
            observedLabels = null;
        }

        entityArgumentIndexes = null;
        labelArgumentIndexes = null;
        numFeatures = -1;
        manualLabels = null;
    }

    public SupportingModel() {
        entityIndexMapping = null;
        labelIndexMapping = null;
        numFeatures = -1;

        manualLabels = null;
        observedLabels = new HashMap<Integer, float[]>();

        entityArgumentIndexes = StringUtils.splitInt(Options.MODEL_PREDICATE_ENTITY_ARGS.getString(), ",");
        labelArgumentIndexes = StringUtils.splitInt(Options.MODEL_PREDICATE_LABEL_ARGS.getString(), ",");
    }

    /**
     * Load the model from some configuration (that may include paths).
     */
    public abstract void load(Map<String, String> config, String relativeDir);

    /**
     * Get the value for the specified atom.
     * The atom is specified by both the actual atom, or the entity/label index.
     */
    public abstract float getValue(RandomVariableAtom atom, int entityIndex, int labelIndex);

    /**
     * Run the model and store the result internally.
     */
    public abstract void run();

    /**
     * Fit the model using values set through setLabel().
     */
    public abstract void fit();

    /**
     * Fit the model for the first time.
     * This will typically involve using only observed values.
     */
    public abstract void initialFit();

    public int numFeatures() {
        return numFeatures;
    }

    public int numLabels() {
        return labelIndexMapping.size();
    }

    public float getValue(RandomVariableAtom atom) {
        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return 0.0f;
        }

        return getValue(atom, indexes.entityIndex, indexes.labelIndex);
    }

    public void resetLabels() {
        for (int i = 0; i < manualLabels.length; i++) {
            if (observedLabels.containsKey(Integer.valueOf(i))) {
                float[] labels = observedLabels.get(Integer.valueOf(i));
                for (int j = 0; j < labels.length; j++) {
                    manualLabels[i][j] = labels[j];
                }
            } else {
                for (int j = 0; j < manualLabels[i].length; j++) {
                    manualLabels[i][j] = 0.0f;
                }
            }
        }
    }

    public float getLabel(RandomVariableAtom atom) {
        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return 0.0f;
        }

        return manualLabels[indexes.entityIndex][indexes.labelIndex];
    }

    public void setLabel(RandomVariableAtom atom, float labelValue) {
        AtomIndexes indexes = getAtomIndexes(atom);
        if (indexes == null) {
            return;
        }

        manualLabels[indexes.entityIndex][indexes.labelIndex] = labelValue;
    }

    /**
     * Load the file that defines the order that labels will appear.
     * The mapping will be loaded directly into |labelIndexMapping|.
     * Although not invoked directly by this class, this is made available to supporting models.
     */
    protected void loadLabels(String path) {
        log.debug("Loading labels for {} from {}", this, path);

        labelIndexMapping = new HashMap<String, Integer>();

        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
            String line = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(DELIM);

                if (parts.length != labelArgumentIndexes.length) {
                    throw new RuntimeException(
                            String.format("Incorrectly sized label line (%d). Expected: %d values, found: %d",
                            lineNumber, labelArgumentIndexes.length, parts.length));
                }

                labelIndexMapping.put(line, Integer.valueOf(numLabels()));
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse labels file: " + path, ex);
        }
    }

    /**
     * Load the file that maps entities to features.
     * The mapping of entity to index will be placed in |entityIndexMapping|,
     * |numFeatures| will be set, and the actual features are returned.
     * Although not invoked directly by this class, this is made available to supporting models.
     */
    protected float[][] loadFeatures(String path) {
        log.debug("Loading features for {} from {}", this, path);

        entityIndexMapping = new HashMap<String, Integer>();

        int width = -1;

        List<float[]> rawFeatures = new ArrayList<float[]>();

        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
            String line = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(DELIM);

                if (width == -1) {
                    width = parts.length;
                    numFeatures = width - entityArgumentIndexes.length;

                    if (numFeatures <= 0) {
                        throw new RuntimeException(String.format(
                                "Line too short (%d). Expected at least %d values, found %d.",
                                lineNumber, entityArgumentIndexes.length + 1, width));
                    }
                } else if (parts.length != width) {
                    throw new RuntimeException(String.format(
                            "Incorrectly sized line (%d). Expected: %d values, found: %d.",
                            lineNumber, width, parts.length));
                }

                // Pull the features out of text.
                float[] features = new float[numFeatures];
                for (int i = 0; i < numFeatures; i++) {
                    features[i] = Float.parseFloat(parts[i + entityArgumentIndexes.length]);
                }

                rawFeatures.add(features);
                entityIndexMapping.put(getAtomIdentifier(parts, entityArgumentIndexes), entityIndexMapping.size());
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse features file: " + path, ex);
        }

        float[][] arrayFeatures = new float[rawFeatures.size()][numFeatures];
        for (int i = 0; i < rawFeatures.size(); i++) {
            for (int j = 0; j < numFeatures; j++) {
                arrayFeatures[i][j] = rawFeatures.get(i)[j];
            }
        }

        log.debug("Loaded features for {} [{} x {}]", this, arrayFeatures.length, numFeatures);

        return arrayFeatures;
    }

    /**
     * Load observations.
     * Observations will be in the form of the atom arguments and either a float or nothing (indicating a 1.0).
     * This is the same format as a standard PSL data file.
     * The observations will be loaded directly into |observedLabels|.
     * Although not invoked directly by this class, this is made available to supporting models.
     * Labels should already be loaded before calling this.
     */
    protected void loadObservations(String path) {
        log.debug("Loading observations for {} from {}", this, path);

        int minWidth = entityArgumentIndexes.length + labelArgumentIndexes.length;

        try (BufferedReader reader = FileUtils.getBufferedReader(path)) {
            String line = null;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(DELIM);

                if (parts.length < minWidth || parts.length > (minWidth + 1)) {
                    throw new RuntimeException(
                            String.format("Incorrectly sized observation line (%d). Expected: %d (or %d) values, found: %d",
                            lineNumber, minWidth, minWidth + 1, parts.length));
                }

                Integer entityIndex = entityIndexMapping.get(getAtomIdentifier(parts, entityArgumentIndexes));
                if (entityIndex == null) {
                    throw new RuntimeException(
                            String.format("Unknown entity seen in observations. Line: %d, File: %s.",
                            lineNumber, path));
                }

                Integer labelIndex = labelIndexMapping.get(getAtomIdentifier(parts, labelArgumentIndexes));
                if (labelIndex == null) {
                    throw new RuntimeException(
                            String.format("Unknown label seen in observations. Line: %d, File: %s.",
                            lineNumber, path));
                }

                float value = 1.0f;
                if (parts.length == (minWidth + 1)) {
                    value = Float.parseFloat(parts[minWidth]);
                }

                if (!observedLabels.containsKey(entityIndex)) {
                    observedLabels.put(entityIndex, new float[numLabels()]);
                }

                observedLabels.get(entityIndex)[labelIndex.intValue()] = value;
            }
        } catch (IOException ex) {
            throw new RuntimeException("Unable to parse observations file: " + path, ex);
        }
    }

    protected AtomIndexes getAtomIndexes(RandomVariableAtom atom) {
        int entityIndex = getEntityIndex(atom);
        if (entityIndex < 0) {
            log.warn("Could not locate entity for atom: {}", atom);
            return null;
        }

        int labelIndex = getLabelIndex(atom);
        if (labelIndex < 0) {
            log.warn("Could not locate label for atom: {}", atom);
            return null;
        }

        return new AtomIndexes(entityIndex, labelIndex);
    }

    protected int getEntityIndex(RandomVariableAtom atom) {
        String key = getAtomIdentifier(atom, entityArgumentIndexes);

        Integer index = entityIndexMapping.get(key);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    protected int getLabelIndex(RandomVariableAtom atom) {
        String key = getAtomIdentifier(atom, labelArgumentIndexes);

        Integer index = labelIndexMapping.get(key);
        if (index == null) {
            return -1;
        }

        return index.intValue();
    }

    protected String getAtomIdentifier(String[] stringArgs, int[] argumentIndexes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < argumentIndexes.length; i++) {
            if (i != 0) {
                builder.append(DELIM);
            }

            builder.append(stringArgs[argumentIndexes[i]]);
        }

        return builder.toString();
    }

    protected String getAtomIdentifier(RandomVariableAtom atom, int[] argumentIndexes) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < argumentIndexes.length; i++) {
            if (i != 0) {
                builder.append(DELIM);
            }

            builder.append(atom.getArguments()[argumentIndexes[i]].rawToString());
        }

        return builder.toString();
    }

    protected static final class AtomIndexes {
        public int entityIndex;
        public int labelIndex;

        public AtomIndexes(int entityIndex, int labelIndex) {
            this.entityIndex = entityIndex;
            this.labelIndex = labelIndex;
        }
    }
}
