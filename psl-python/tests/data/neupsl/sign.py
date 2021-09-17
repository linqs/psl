#!/usr/bin/env python3
'''
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2021 The Regents of the University of California

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
A data generator for simple positive/neagtive classes.
'''

import os
import random
import sys

import tests.data.neupsl.util

MODEL_DIR_NAME = 'model'
DATA_DIR_NAME = 'data'

def fetch(seed = 4, learning_rate = 0.01,
        layer_size = 32, layer_activation = 'relu'):
    _import()

    random.seed(seed)

    train_features, train_labels, test_features, test_labels = _generate_data()

    width = len(train_features[0])
    num_features = len(train_labels[0])

    layers = [
        tensorflow.keras.layers.Input(shape = width),
        tensorflow.keras.layers.Dense(layer_size, activation = layer_activation),
        tensorflow.keras.layers.Dense(num_features, activation = 'softmax'),
    ]

    model = tensorflow.keras.Sequential(layers)

    model.compile(
        optimizer = tensorflow.keras.optimizers.Adam(learning_rate = learning_rate),
        loss = tensorflow.keras.losses.CategoricalCrossentropy(from_logits = False),
        metrics = ['categorical_accuracy']
    )

    wrapper = pslpython.neupsl.NeuPSLWrapper(model, width, num_features)

    return wrapper, train_features, train_labels, test_features, test_labels

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

    import pslpython.neupsl

# A dataset for classifying positive and negative entities.
# A label of [1, 0] means all values will be positive, [0, 1] is all negative.
def _generate_data(width = 2, train_size = 100, test_size = 100):
    train_features = []
    train_labels = []

    test_features = []
    test_labels = []

    for features, labels, size in ((train_features, train_labels, train_size), (test_features, test_labels, test_size)):
        for i in range(size):
            point = [random.randint(0, 2 ** 10) for i in range(width)]
            label = [1, 0]

            if (random.random() < 0.5):
                point = list(map(lambda x: -x, point))
                label = [0, 1]

            features.append(point)
            labels.append(label)

    return train_features, train_labels, test_features, test_labels

def main(save_path):
    os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'
    wrapper, train_features, train_labels, test_features, test_labels = fetch()
    wrapper.model.summary()

    tests.data.neupsl.util.save(save_path, wrapper, train_features, train_labels, test_features, test_labels)

def _load_args(args):
    executable = args.pop(0)
    if (len(args) != 1 or ({'h', 'help'} & {arg.lower().strip().replace('-', '') for arg in args})):
        print("USAGE: python3 %s <save path>" % (executable), file = sys.stderr)
        print('Create a model and serialize it to the passed in directory.')
        sys.exit(1)

    return args.pop()

if (__name__ == '__main__'):
    main(_load_args(sys.argv))
