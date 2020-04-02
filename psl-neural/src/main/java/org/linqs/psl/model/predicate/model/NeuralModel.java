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

import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.util.IteratorUtils;
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

    public NeuralModel() {
        features = null;

        model = null;
        iterationCount = -1;

        output = null;

        epochs = Options.MODEL_PREDICATE_ITERATIONS.getInt();
        batchSize = Options.MODEL_PREDICATE_BATCH_SIZE.getInt();
    }

    @Override
    public void load(Map<String, String> config, String relativeDir) {
        String modelPath = config.get(CONFIG_MODEL);
        if (modelPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL));
        }

        String labelsPath = config.get(CONFIG_LABELS);
        if (labelsPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a label path (\"%s\") specified in predicate config.",
                    CONFIG_LABELS));
        }

        String featuresPath = config.get(CONFIG_FEATURES);
        if (featuresPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a feature path (\"%s\") specified in predicate config.",
                    CONFIG_FEATURES));
        }

        modelPath = makePath(relativeDir, modelPath);
        labelsPath = makePath(relativeDir, labelsPath);
        featuresPath = makePath(relativeDir, featuresPath);

        loadLabels(labelsPath);

        float[][] arrayFeatures = loadFeatures(featuresPath);
        features = Nd4j.create(arrayFeatures);

        loadModel(modelPath);

        manualLabels = new float[entityIndexMapping.size()][labelIndexMapping.size()];
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

    @Override
    public void fit() {
        log.trace("Fitting {}.", this);

        model.clear();

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
            .layer(new LossLayer.Builder(LossFunctions.LossFunction.L2)
                .build())
            .build();

        MultiLayerNetwork dummyModel = new MultiLayerNetwork(dummyConfig);
        dummyModel.init();

        return dummyModel.getOutputLayer().conf();
    }
}
