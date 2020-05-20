/*
 * This file is part of the PSL software.
 * Copyright 2011-2015 University of Maryland
 * Copyright 2013-2020 The Regents of the University of California
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

import org.linqs.psl.config.NeuralOptions;
import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.StringUtils;

import org.deeplearning4j.datasets.iterator.INDArrayDataSetIterator;
import org.deeplearning4j.nn.conf.layers.LossLayer;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.MultiLayerConfigurationAccess;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.modelimport.keras.KerasModelImport;
import org.deeplearning4j.nn.modelimport.keras.exceptions.InvalidKerasConfigurationException;
import org.deeplearning4j.nn.modelimport.keras.exceptions.UnsupportedKerasConfigurationException;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.lossfunctions.LossFunctions;
import org.nd4j.linalg.primitives.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * A supporting model that is back by a neural network.
 */
public class NeuralModel extends SupportingModel {
    private static final Logger log = LoggerFactory.getLogger(NeuralModel.class);

    /**
     * All the features indexed by the entity ID.
     * [entities x features].
     */
    private INDArray features;

    private MultiLayerNetwork model;
    private int iterationCount;

    /**
     * The most recent output from the model.
     * This is the full output for all entities.
     * [entities x labels]
     */
    private INDArray output;

    private int epochs;
    private int batchSize;
    private double newLearningRate;
    private String lossFunction;

    private float lowerBinarizeRank;
    private float upperBinarizeRank;
    private boolean binarizeWithRank;

    private float lowerBinarizeThreshold;
    private float upperBinarizeThreshold;
    private boolean binarizeWithThreshold;

    private boolean normalizeLabels;

    public NeuralModel() {
        features = null;

        model = null;
        iterationCount = -1;

        output = null;

        epochs = Options.MODEL_PREDICATE_ITERATIONS.getInt();
        batchSize = Options.MODEL_PREDICATE_BATCH_SIZE.getInt();
        newLearningRate = NeuralOptions.NEURAL_LEARNING_RATE.getDouble();
        lossFunction = NeuralOptions.NEURAL_LOSS_FUNCTION.getString();

        lowerBinarizeRank = NeuralOptions.NEURAL_BIN_RANK_LOWER.getFloat();
        upperBinarizeRank = NeuralOptions.NEURAL_BIN_RANK_UPPER.getFloat();
        binarizeWithRank = (lowerBinarizeRank > 0.0f || upperBinarizeRank < 1.0f);

        lowerBinarizeThreshold = NeuralOptions.NEURAL_BIN_THRESHOLD_LOWER.getFloat();
        upperBinarizeThreshold = NeuralOptions.NEURAL_BIN_THRESHOLD_UPPER.getFloat();
        binarizeWithThreshold = (lowerBinarizeThreshold > 0.0f || upperBinarizeThreshold < 1.0f);

        normalizeLabels = NeuralOptions.NEURAL_NORMALIZE_LABELS.getBoolean();
    }

    @Override
    public void load(Map<String, String> config, String relativeDir) {
        String modelPath = makePath(relativeDir, config.get(CONFIG_MODEL));
        if (modelPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL));
        }

        String labelsPath = makePath(relativeDir, config.get(CONFIG_LABELS));
        if (labelsPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a label path (\"%s\") specified in predicate config.",
                    CONFIG_LABELS));
        }

        String featuresPath = makePath(relativeDir, config.get(CONFIG_FEATURES));
        if (featuresPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a feature path (\"%s\") specified in predicate config.",
                    CONFIG_FEATURES));
        }

        String observationsPath = makePath(relativeDir, config.get(CONFIG_OBSERVATIONS));

        loadLabels(labelsPath);

        float[][] arrayFeatures = loadFeatures(featuresPath);
        features = Nd4j.create(arrayFeatures);

        loadModel(modelPath);

        manualLabels = new float[entityIndexMapping.size()][labelIndexMapping.size()];

        if (observationsPath != null) {
            loadObservations(observationsPath);
        }
    }

    @Override
    public float getValue(RandomVariableAtom atom, int entityIndex, int labelIndex) {
        return output.getFloat(entityIndex, labelIndex);
    }

    @Override
    public void run() {
        output = model.output(features);
        long[] shape = output.shape();

        if (shape.length != 2 || shape[0] != entityIndexMapping.size() || shape[1] != labelIndexMapping.size()) {
            throw new RuntimeException(String.format(
                    "Unexpected shape for model output. Expected [%d, %d], found [%s].",
                    entityIndexMapping.size(), labelIndexMapping.size(),
                    StringUtils.join(", ", shape)));
        }
    }

    private void thresholdBinarize() {
        for (int entityIndex = 0; entityIndex < entityIndexMapping.size(); entityIndex++) {
            for (int labelIndex = 0; labelIndex < labelIndexMapping.size(); labelIndex++) {
                if (manualLabels[entityIndex][labelIndex] < lowerBinarizeThreshold) {
                    manualLabels[entityIndex][labelIndex] = 0.0f;
                }

                if (manualLabels[entityIndex][labelIndex] > upperBinarizeThreshold) {
                    manualLabels[entityIndex][labelIndex] = 1.0f;
                }
            }
        }
    }

    private void rankBinarize() {
        IndexSortable[] values = new IndexSortable[labelIndexMapping.size()];
        for (int labelIndex = 0; labelIndex < labelIndexMapping.size(); labelIndex++) {
            values[labelIndex] = new IndexSortable();
        }

        for (int entityIndex = 0; entityIndex < entityIndexMapping.size(); entityIndex++) {
            for (int labelIndex = 0; labelIndex < labelIndexMapping.size(); labelIndex++) {
                values[labelIndex].index = labelIndex;
                values[labelIndex].value = manualLabels[entityIndex][labelIndex];
            }

            Arrays.sort(values);

            for (int i = 0; i < values.length; i++) {
                if (((float)(i + 1) / values.length) < lowerBinarizeRank) {
                    manualLabels[entityIndex][values[i].index] = 0.0f;
                }

                if (((float)(i + 1) / values.length) > upperBinarizeRank) {
                    manualLabels[entityIndex][values[i].index] = 1.0f;
                }
            }
        }
    }

    private static class IndexSortable implements Comparable<IndexSortable> {
        public int index;
        public float value;

        public IndexSortable() {
            index = -1;
            value = 0.0f;
        }

        @Override
        public int compareTo(IndexSortable other) {
            if (this.value < other.value) {
                return -1;
            } else if (this.value < other.value) {
                return 1;
            } else {
                return 0;
            }
        }
    }

    @Override
    public void fit() {
        log.trace("Fitting {}.", this);

        model.clear();

        if (normalizeLabels) {
            normalizeLabels();
        }

        if (log.isTraceEnabled()) {
            log.trace("Mean label range: " + computeMeanLabelRange());
        }

        if (binarizeWithThreshold) {
            thresholdBinarize();
        }

        if (binarizeWithRank) {
            rankBinarize();
        }

        INDArray labels = Nd4j.create(manualLabels);

        Iterable<Pair<INDArray, INDArray>> pairs = IteratorUtils.map(
                IteratorUtils.newIterable(IteratorUtils.count(entityIndexMapping.size())),
                new IteratorUtils.MapFunction<Integer, Pair<INDArray, INDArray>> () {
                    @Override
                    public Pair<INDArray, INDArray> map(Integer index) {
                        return Pair.create(features.getRow(index.intValue()), labels.getRow(index.intValue()));
                    }
                });


        DataSetIterator data = new INDArrayDataSetIterator(pairs, batchSize);
        model.fit(data, epochs);

        log.trace("Done fitting {}.", this);
    }

    private void normalizeLabels() {
        for (int entityIndex = 0; entityIndex < entityIndexMapping.size(); entityIndex++) {
            // Start with a small number to avoid any division issues.
            float sum = 1.0e-7f;
            float min = 0.0f;

            for (int labelIndex = 0; labelIndex < labelIndexMapping.size(); labelIndex++) {
                float value = manualLabels[entityIndex][labelIndex];
                sum += value;

                if (labelIndex == 0 || value < min) {
                    min = value;
                }
            }

            // Adjust the sum to retroactively subtract the min from all values.
            sum -= min * labelIndexMapping.size();

            // Adjust every value so the new sum will be one.
            for (int labelIndex = 0; labelIndex < labelIndexMapping.size(); labelIndex++) {
                manualLabels[entityIndex][labelIndex] = Math.min(1.0f, Math.max(0.0f, (manualLabels[entityIndex][labelIndex] - min) / sum));
            }
        }
    }

    private double computeMeanLabelRange() {
        double meanRange = 0.0;

        if (entityIndexMapping.size() == 0) {
            return -1.0;
        }

        for (int entityIndex = 0; entityIndex < entityIndexMapping.size(); entityIndex++) {
            float min = 0.0f;
            float max = 0.0f;

            for (int labelIndex = 0; labelIndex < labelIndexMapping.size(); labelIndex++) {
                float value = manualLabels[entityIndex][labelIndex];

                if (labelIndex == 0 || value < min) {
                    min = value;
                }

                if (labelIndex == 0 || value > max) {
                    max = value;
                }
            }

            meanRange += (max - min);
        }

        return meanRange / entityIndexMapping.size();
    }

    private void loadModel(String path) {
        log.debug("Loading model for {} from {}", this, path);

        MultiLayerNetwork rawModel = null;

        try {
            rawModel = KerasModelImport.importKerasSequentialModelAndWeights(path, false);
        } catch (InvalidKerasConfigurationException ex) {
            throw new RuntimeException("Unable to load Keras model at: " + path);
        } catch (UnsupportedKerasConfigurationException ex) {
            throw new RuntimeException("The provided Keras model is unsupported by DL4J: " + path);
        } catch (IOException ex) {
            throw new RuntimeException("Unable to load model at: " + path);
        }

        MultiLayerConfiguration layerConfigs = rawModel.getLayerWiseConfigurations();
        NeuralNetConfiguration outputLayerConfig = createOutputLayerConfig();

        MultiLayerConfiguration.Builder builder = MultiLayerConfigurationAccess.getBuilder(layerConfigs, outputLayerConfig);
        MultiLayerConfiguration newConfig = builder.build();

        // Construct the new model with the configuration and parameters.
        model = new MultiLayerNetwork(newConfig, rawModel.params());

        iterationCount = model.getIterationCount();

        if (!MathUtils.isZero(newLearningRate)) {
            model.setLearningRate(newLearningRate);
        }
    }

    /**
     * Construct a configuration for an output layer.
     * This layer will have no parameters.
     */
    private NeuralNetConfiguration createOutputLayerConfig() {
        // Build a dummy network (so we can fetch out the network config).
        MultiLayerConfiguration dummyConfig = new NeuralNetConfiguration.Builder()
            .list()
            .layer(new org.deeplearning4j.nn.conf.layers.DenseLayer.Builder()
                .nIn(labelIndexMapping.size())
                .nOut(labelIndexMapping.size())
                .build())
            .layer(new LossLayer.Builder(LossFunctions.LossFunction.valueOf(lossFunction))
                .build())
            .build();

        MultiLayerNetwork dummyModel = new MultiLayerNetwork(dummyConfig);
        dummyModel.init();

        return dummyModel.getOutputLayer().conf();
    }
}
