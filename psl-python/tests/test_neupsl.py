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

import os
import random
import sys
import tempfile

from tests.base_test import PSLTest

class TestNeuPSL(PSLTest):
    def setUp(self):
        # Skip these tests if tensorflow is not installed.
        try:
            # Tensoflow has a bug when sys.argv is empty on import: https://github.com/tensorflow/tensorflow/issues/45994
            sys.argv.append('__workaround__')
            import tensorflow
            sys.argv.pop()
        except ImportError:
            self.skipTest("Tensorflow is not installed.")

        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

    def tearDown(self):
        del os.environ['TF_CPP_MIN_LOG_LEVEL']

    def test_wrapper_base(self):
        import tensorflow
        import pslpython.neupsl

        (train_features, train_labels), (test_features, test_labels) = _dataset_pos_neg()

        width = len(train_features[0])
        num_features = len(train_labels[0])
        epochs = 20

        layers = [
            tensorflow.keras.layers.Input(shape = width),
            tensorflow.keras.layers.Dense(32, activation = 'relu'),
            tensorflow.keras.layers.Dense(num_features, activation = 'softmax'),
        ]

        model = tensorflow.keras.Sequential(layers)

        model.compile(
            optimizer = tensorflow.keras.optimizers.Adam(learning_rate = 0.01),
            loss = tensorflow.keras.losses.CategoricalCrossentropy(from_logits = False),
            metrics = ['categorical_accuracy']
        )

        wrapper = pslpython.neupsl.NeuPSLWrapper(model, width, num_features)

        pre_train_results = wrapper.evaluate(test_features, test_labels)

        for epoch in range(epochs):
            wrapper.fit(train_features, train_labels)

        post_train_results = wrapper.evaluate(test_features, test_labels)

        with tempfile.TemporaryDirectory(suffix = '_TestNeuPSL') as temp_dir:
            save_path = os.path.join(temp_dir, 'model')
            wrapper.save(tfPath = save_path)

            newWrapper = tensorflow.saved_model.load(save_path)
            post_load_results = newWrapper.evaluate(test_features, test_labels)

        # First value is the objective, second value is the accuracy.

        # Assert that training helped.
        self.assertTrue(pre_train_results[0] >= post_train_results[0])
        self.assertTrue(pre_train_results[1] <= post_train_results[1])

        # Assert that the model produces the same results after being reloaded.
        self.assertClose(post_train_results[0], post_load_results[0])
        self.assertClose(post_train_results[1], post_load_results[1])

# A dataset for classifying positive and negative entities.
# A label of [1, 0] means all values will be positive, [0, 1] is all negative.
def _dataset_pos_neg(width = 2, train_size = 100, test_size = 100):
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

    return (train_features, train_labels), (test_features, test_labels)
