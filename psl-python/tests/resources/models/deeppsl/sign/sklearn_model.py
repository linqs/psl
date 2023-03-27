#!/usr/bin/env python3
'''
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2023 The Regents of the University of California

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
'''

'''
A data generator for simple positive/negative classes.
'''

import os
import random
import sys

import tests.resources.models.deeppsl.sign_create_data

MODEL_DIR_NAME = 'model'
DATA_DIR_NAME = 'data'


class SignModel(pslpython.deeppsl.model.DeepModel):
    def __init__(self):
        super().__init__()

        _import()

        self._model = None

    def internal_init_model(self, options = {}):
        layers = [
            tensorflow.keras.layers.Input(options['input_shape']),
            tensorflow.keras.layers.Dense(options['output_shape'], activation = 'softmax'),
        ]

        model = tensorflow.keras.Sequential(layers)

        model.compile(
            optimizer = tensorflow.keras.optimizers.Adam(learning_rate = options['learning_rate']),
            loss = tensorflow.keras.losses.CategoricalCrossentropy(from_logits = False),
            metrics = ['categorical_accuracy']
        )

        self._model = model
        return {}

    def internal_fit(self, features, labels, alpha = 0.0, gradients = None,
                     batch_size = 32, epochs = 10, learning_rate = 0.001,
                     options = {}):
        self._model.fit(features, labels, batch_size = batch_size, epochs = epochs)
        return {}

    def internal_predict(self, options = {}):
        predictions = self._model.predict(self._features)
        return predictions, {}

    def internal_eval(self, options = {}):
        return self._model.evaluate(features, labels, return_dict = True)

    def internal_save(self, path, options = {}):
        self._model.save(path, save_format = 'tf')
        return {}

'''
A simple sign dataset for classifying positive and negative entities.
A label of [1, 0] means all values will be positive, [0, 1] is all negative.
'''
def generate_data(width = 2, train_size = 100, test_size = 100):
    train_features = []
    train_labels = []

    test_features = []
    test_labels = []

    for features, labels, size in ((train_features, train_labels, train_size), (test_features, test_labels, test_size)):
        for i in range(size):
            point = [random.randint(0, 2 ** 10) for i in range(width)]
            label = [1, 0]

            if random.random() < 0.5:
                point = list(map(lambda x: -x, point))
                label = [0, 1]

            features.append(point)
            labels.append(label)

    return train_features, train_labels, test_features, test_labels

'''
Handle importing tensorflow and pslpython.neupsl into the global scope.
Will raise if tensorflow is not installed.
'''
def _import():
    global tensorflow
    global pslpython

    # Tensoflow has a bug when sys.argv is empty on import: https://github.com/tensorflow/tensorflow/issues/45994
    sys.argv.append('__workaround__')
    import tensorflow
    sys.argv.pop()

    import pslpython
