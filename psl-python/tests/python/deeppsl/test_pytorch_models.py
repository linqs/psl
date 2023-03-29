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

import os
import sys
import tempfile

import tests.python.base_test
import tests.resources.models.deeppsl.sign.data
import tests.resources.models.deeppsl.sign.tensorflow_model

THIS_DIR = os.path.abspath(os.path.dirname(os.path.realpath(__file__)))
DATA_DIR = os.path.join(THIS_DIR, '..', '..', 'resources', 'data')
SIGN_DIR = os.path.join(THIS_DIR, 'sign')

class TestPytorchModels(tests.python.base_test.PSLTest):
    def setUp(self):
        global tensorflow

        # Skip these tests if tensorflow is not installed.
        try:
            # Tensorflow has a bug when sys.argv is empty on import: https://github.com/tensorflow/tensorflow/issues/45994
            sys.argv.append('__workaround__')
            import tensorflow
            sys.argv.pop()
        except ImportError:
            self.skipTest("Tensorflow is not installed.")

        os.environ['TF_CPP_MIN_LOG_LEVEL'] = '2'

        tensorflow.random.set_seed(4)

    def tearDown(self):
        del os.environ['TF_CPP_MIN_LOG_LEVEL']

    def test_sign_model(self):
        sign_model = tests.resources.models.deeppsl.sign.tensorflow_model.SignModel()
        x_train, y_train, x_test, y_test = tests.resources.models.deeppsl.sign.data.get_data(SIGN_DIR)

        epochs = 20
        options = {}

        sign_model.internal_init_model()
        pre_train_results = sign_model.internal_eval(x_test, y_test)
        sign_model.internal_fit(x_train, y_train, epochs=epochs)
        post_train_results = sign_model.internal_eval(x_test, y_test)

        with tempfile.TemporaryDirectory(suffix = '_TestNeuPSL') as temp_dir:
            save_path = os.path.join(temp_dir, 'tensorflow_model')
            sign_model.internal_save(options = {'save_path': save_path})

            new_sign_model = tensorflow.saved_model.load(save_path)
            post_load_results = new_sign_model.evaluate(x_test, y_test)

        # First value is the objective, second value is the accuracy.

        # Assert that training helped.
        self.assertTrue(pre_train_results[0] >= post_train_results[0])
        self.assertTrue(pre_train_results[1] <= post_train_results[1])

        # Assert that the model produces the same results after being reloaded.
        self.assertClose(post_train_results[0], post_load_results[0])
        self.assertClose(post_train_results[1], post_load_results[1])
