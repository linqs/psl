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
import sys
import tempfile

import tests.base_test
import tests.data.neupsl.sign

class TestNeuPSL(tests.base_test.PSLTest):
    def setUp(self):
        global tensorflow

        # Skip these tests if tensorflow is not installed.
        try:
            # Tensoflow has a bug when sys.argv is empty on import: https://github.com/tensorflow/tensorflow/issues/45994
            sys.argv.append('__workaround__')
            import tensorflow
            sys.argv.pop()
        except ImportError:
            self.skipTest("Tensorflow is not installed.")

        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

        tensorflow.random.set_seed(4)

    def tearDown(self):
        del os.environ['TF_CPP_MIN_LOG_LEVEL']

    def test_wrapper_base(self):
        wrapper, train_features, train_labels, test_features, test_labels = tests.data.neupsl.sign.fetch()
        epochs = 20

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
