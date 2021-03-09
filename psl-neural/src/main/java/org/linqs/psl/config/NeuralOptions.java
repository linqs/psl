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
 */
public class NeuralOptions {
    public static final Option NEURAL_BIN_RANK_LOWER = new Option(
        "neural.binarize.rank.lower",
        0.0f,
        "Trainning values for the neural net below this percentage ranking will be set to zero.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option NEURAL_BIN_RANK_UPPER = new Option(
        "neural.binarize.rank.upper",
        1.0f,
        "Trainning values for the neural net above this percentage ranking will be set to one.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option NEURAL_BIN_THRESHOLD_LOWER = new Option(
        "neural.binarize.threshold.lower",
        0.0f,
        "Trainning values for the neural net below this threshold will be set to zero.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option NEURAL_BIN_THRESHOLD_UPPER = new Option(
        "neural.binarize.threshold.upper",
        1.0f,
        "Trainning values for the neural net above this threshold will be set to one.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option NEURAL_NORMALIZE_LABELS = new Option(
        "neural.normalize",
        false,
        "Normalize the labels before fitting so that the minimum label is zero and the sum of the labels is 1.0."
        + " This is applied before any binarization."
    );

    public static final Option NEURAL_LEARNING_RATE = new Option(
        "neural.learningrate",
        0,
        "The global learning rate of the neural net. If zero, don't override the learning rate.",
        Option.FLAG_NON_NEGATIVE
    );

    public static final Option NEURAL_LOSS_FUNCTION = new Option(
        "neural.lossfunction",
        "MSE",
        "The loss function for the neural net."
        + " See https://deeplearning4j.org/api/latest/index.html?org/nd4j/linalg/factory/Nd4j.html"
    );

    static {
        Options.addOption(NEURAL_BIN_RANK_LOWER);
        Options.addOption(NEURAL_BIN_RANK_UPPER);
        Options.addOption(NEURAL_BIN_THRESHOLD_LOWER);
        Options.addOption(NEURAL_BIN_THRESHOLD_UPPER);
        Options.addOption(NEURAL_LEARNING_RATE);
        Options.addOption(NEURAL_LOSS_FUNCTION);
    }

    // Static only.
    private NeuralOptions() {}
}
