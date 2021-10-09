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

import org.linqs.psl.config.NeuralOptions;
import org.linqs.psl.config.Options;
import org.linqs.psl.model.atom.RandomVariableAtom;
import org.linqs.psl.util.FileUtils;
import org.linqs.psl.util.IteratorUtils;
import org.linqs.psl.util.MathUtils;
import org.linqs.psl.util.StringUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tensorflow.SavedModelBundle;
import org.tensorflow.Tensor;
import org.tensorflow.ndarray.Shape;
import org.tensorflow.types.TFloat32;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A supporting model that is back by a neural network.
 */
public class NeuralModel extends SupportingModel {
    private static final Logger log = LoggerFactory.getLogger(NeuralModel.class);

    /**
     * The most recent output from the model.
     * This is the full output for all entities.
     * [entities x labels]
     */
    private TFloat32 output;

    /**
     * All features for all entities.
     * [entities x features]
     */
    private TFloat32 features;

    /**
     * A tensor representation of the current labels.
     * [entities x labels]
     */
    private TFloat32 labelsTensor;

    // TODO(eriq): batch size is not being honored.
    // Always take the min of the batch size and number of data points.
    private int maxBatchSize;
    private int epochs;

    private int initialMaxBatchSize;
    private int initialEpochs;

    private SavedModelBundle bundle;

    // Keys for working with the saved model.
    private String bundleTag;
    private String fitFunction;
    private String predictFunction;
    private String labelsTensorName;
    private String inputTensorName;
    private String outputTensorName;

    public NeuralModel() {
        bundle = null;

        features = null;
        output = null;
        labelsTensor = null;

        epochs = Options.MODEL_PREDICATE_ITERATIONS.getInt();
        maxBatchSize = Options.MODEL_PREDICATE_BATCH_SIZE.getInt();

        initialEpochs = Options.MODEL_PREDICATE_INITIAL_ITERATIONS.getInt();
        initialMaxBatchSize = Options.MODEL_PREDICATE_INITIAL_BATCH_SIZE.getInt();

        bundleTag = NeuralOptions.NEURAL_TF_BUNDLE_TAG.getString();
        fitFunction = NeuralOptions.NEURAL_TF_FUNCTION_FIT.getString();
        predictFunction = NeuralOptions.NEURAL_TF_FUNCTION_PREDICT.getString();
        inputTensorName = NeuralOptions.NEURAL_TF_TENSOR_INPUT.getString();
        labelsTensorName = NeuralOptions.NEURAL_TF_TENSOR_LABELS.getString();
        outputTensorName = NeuralOptions.NEURAL_TF_TENSOR_OUTPUT.getString();
    }

    @Override
    public void load(Map<String, String> config, String relativeDir) {
        String modelPath = FileUtils.makePath(relativeDir, config.get(CONFIG_MODEL));
        if (modelPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a model path (\"%s\") specified in predicate config.",
                    CONFIG_MODEL));
        }

        String labelsPath = FileUtils.makePath(relativeDir, config.get(CONFIG_LABELS));
        if (labelsPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a label path (\"%s\") specified in predicate config.",
                    CONFIG_LABELS));
        }

        String featuresPath = FileUtils.makePath(relativeDir, config.get(CONFIG_FEATURES));
        if (featuresPath == null) {
            throw new IllegalArgumentException(String.format(
                    "A NeuralModel must have a feature path (\"%s\") specified in predicate config.",
                    CONFIG_FEATURES));
        }

        String observationsPath = FileUtils.makePath(relativeDir, config.get(CONFIG_OBSERVATIONS));

        loadLabels(labelsPath);

        float[][] rawFeatures = loadFeatures(featuresPath);

        features = TFloat32.tensorOf(Shape.of(rawFeatures.length, numFeatures()));
        for (int i = 0; i < rawFeatures.length; i++) {
            for (int j = 0; j < numFeatures(); j++) {
                features.setFloat(rawFeatures[i][j], i, j);
            }
        }

        loadModel(modelPath);

        manualLabels = new float[entityIndexMapping.size()][numLabels()];

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
        // Close any previous output tensor.
        if (output != null) {
            output.close();
        }

        Map<String, Tensor> inputMap = new HashMap<String, Tensor>(1);
        inputMap.put(inputTensorName, features);

        Map<String, Tensor> outputMap = bundle.function(predictFunction).call(inputMap);
        output = validateOutputTensor(outputMap, predictFunction, Shape.of(features.shape().size(0), numLabels()));
    }

    @Override
    public void initialFit() {
        if (observedLabels.size() == 0) {
            log.trace("No observed values for initial fitting of {}.", this);
            return;
        }

        log.trace("Initial fitting {}.", this);

        TFloat32 initialFeatures = TFloat32.tensorOf(Shape.of(observedLabels.size(), numFeatures()));
        TFloat32 initialLabels = TFloat32.tensorOf(Shape.of(observedLabels.size(), numLabels()));

        for (Map.Entry<Integer, float[]> entry : observedLabels.entrySet()) {
            int entityIndex = entry.getKey().intValue();
            float[] labels = entry.getValue();

            for (int i = 0; i < numFeatures(); i++) {
                initialFeatures.setFloat(features.getFloat(entityIndex, i), entityIndex, i);
            }

            for (int i = 0; i < numLabels(); i++) {
                initialLabels.setFloat(labels[i], entityIndex, i);
            }
        }

        Map<String, Tensor> inputMap = new HashMap<String, Tensor>(1);
        inputMap.put(inputTensorName, initialFeatures);
        inputMap.put(labelsTensorName, initialLabels);

        float loss = -1.0f;
        float metricScore = -1.0f;

        for (int i = 0; i < initialEpochs; i++) {
            Map<String, Tensor> outputMap = bundle.function(fitFunction).call(inputMap);
            TFloat32 result = validateOutputTensor(outputMap, fitFunction, Shape.of(2));

            loss = result.getFloat(0);
            metricScore = result.getFloat(1);
            result.close();

            log.trace("Epoch: {} / {}, Loss: {}, Score: {}", i + 1, initialEpochs, loss, metricScore);
        }

        log.debug("Done initial fitting {} with {} epochs. Loss: {}, Score: {}.", this, initialEpochs, loss, metricScore);

        initialFeatures.close();
        initialLabels.close();
    }

    @Override
    public void fit() {
        log.trace("Fitting {}.", this);

        if (labelsTensor == null) {
            labelsTensor = TFloat32.tensorOf(Shape.of(manualLabels.length, numLabels()));
        }

        // Reset all labels.
        for (int i = 0; i < manualLabels.length; i++) {
            for (int j = 0; j < numLabels(); j++) {
                labelsTensor.setFloat(manualLabels[i][j], i, j);
            }
        }

        Map<String, Tensor> inputMap = new HashMap<String, Tensor>(1);
        inputMap.put(inputTensorName, features);
        inputMap.put(labelsTensorName, labelsTensor);

        float loss = -1.0f;
        float metricScore = -1.0f;

        for (int i = 0; i < epochs; i++) {
            Map<String, Tensor> outputMap = bundle.function(fitFunction).call(inputMap);
            TFloat32 result = validateOutputTensor(outputMap, fitFunction, Shape.of(2));

            loss = result.getFloat(0);
            metricScore = result.getFloat(1);
            result.close();

            log.trace("Epoch: {} / {}, Loss: {}, Score: {}", i + 1, epochs, loss, metricScore);
        }

        log.debug("Done fitting {} with {} epochs. Loss: {}, Score: {}.", this, epochs, loss, metricScore);
    }

    private TFloat32 validateOutputTensor(Map<String, Tensor> outputMap, String identifier, Shape expectedShape) {
        if (!outputMap.containsKey(outputTensorName)) {
            throw new RuntimeException(String.format(
                    "[%s] Neural model (%s) does not have an output named '%s'.",
                    identifier, this, outputTensorName));
        }

        Tensor rawOutput = outputMap.get(outputTensorName);

        if (!rawOutput.shape().equals(expectedShape)) {
            throw new RuntimeException(String.format(
                    "[%s] Unexpected output shape for nerual model (%s). Expected: %s. Found: %s.",
                    identifier, this, expectedShape, rawOutput.shape()));

        }

        if (!(rawOutput instanceof TFloat32)) {
            throw new RuntimeException(String.format(
                    "[%s] Unexpected type for output of nerual model (%s). Expected: %s. Found: %s.",
                    identifier, this, TFloat32.class, rawOutput.getClass()));
        }

        return (TFloat32)(rawOutput);
    }

    public void close() {
        super.close();

        if (output != null) {
            output.close();
            output = null;
        }

        if (features != null) {
            features.close();
            features = null;
        }

        if (labelsTensor != null) {
            labelsTensor.close();
            labelsTensor = null;
        }

        if (bundle != null) {
            bundle.close();
            bundle = null;
        }
    }

    private void loadModel(String path) {
        log.debug("Loading model for {} from {}", this, path);

        if (!FileUtils.isDir(path)) {
            throw new RuntimeException("Expecting a Tensorflow SavedModel directory, not a file (see: https://www.tensorflow.org/guide/keras/save_and_serialize#savedmodel_format).");
        }

        bundle = SavedModelBundle.load(path, bundleTag);
    }
}
