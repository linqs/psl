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
package org.linqs.psl.config;

/**
 * Additional options for the psl-neural module.
 *
 * When adding new options, remember to statically add them to Options
 * (see the static block near the end of this class).
 */
public class NeuralOptions {
    public static final Option NEURAL_TF_BUNDLE_TAG = new Option(
        "neural.tf.bundletag",
        "serve",
        "The tag to load the saved model with."
    );

    public static final Option NEURAL_TF_FUNCTION_FIT = new Option(
        "neural.tf.function.fit",
        "fit",
        "The named fit/training function to grab from the Tensorflow saved model bundle."
    );

    public static final Option NEURAL_TF_FUNCTION_PREDICT = new Option(
        "neural.tf.function.predict",
        "predict",
        "The named prediction function to grab from the Tensorflow saved model bundle."
    );

    public static final Option NEURAL_TF_TENSOR_INPUT = new Option(
        "neural.tf.tensor.input",
        "data",
        "The name for the input tensor in the saved model."
    );

    public static final Option NEURAL_TF_TENSOR_LABELS = new Option(
        "neural.tf.tensor.labels",
        "labels",
        "The name for the labels tensor in the saved model."
    );

    public static final Option NEURAL_TF_TENSOR_OUTPUT = new Option(
        "neural.tf.tensor.output",
        "output_0",
        "The name for the output tensor in the saved model."
    );

    static {
        Options.addOption(NEURAL_TF_BUNDLE_TAG);
        Options.addOption(NEURAL_TF_FUNCTION_FIT);
        Options.addOption(NEURAL_TF_FUNCTION_PREDICT);
        Options.addOption(NEURAL_TF_TENSOR_INPUT);
        Options.addOption(NEURAL_TF_TENSOR_OUTPUT);
    }

    // Static only.
    private NeuralOptions() {}
}
