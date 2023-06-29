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

import numpy

import tests.python.base_test
import tests.resources.models.deeppsl.sign.data
import tests.resources.models.deeppsl.sign.pytorch_model

THIS_DIR = os.path.abspath(os.path.dirname(os.path.realpath(__file__)))
DATA_DIR = os.path.join(THIS_DIR, '..', '..', 'resources', 'data')
SIGN_DIR = os.path.join(DATA_DIR, 'sign')

class TestPytorchModels(tests.python.base_test.PSLTest):
    def setUp(self):
        global torch

        # Skip these tests if pytorch is not installed.
        try:
            sys.argv.append('__workaround__')
            import torch
            sys.argv.pop()
        except ImportError:
            self.skipTest("Pytorch is not installed.")

        numpy.random.seed(4)
        torch.manual_seed(4)

    def tearDown(self):
        pass

    def test_sign_model(self):
        sign_model = tests.resources.models.deeppsl.sign.pytorch_model.SignModel()
        x_train, y_train, x_test, y_test = tests.resources.models.deeppsl.sign.data.get_deep_data(SIGN_DIR)

        train_data = [x_train, y_train]
        test_data = [x_test, y_test]
        options = {'input_shape': tests.resources.models.deeppsl.sign.data.FEATURE_SIZE,
                   'output_shape': tests.resources.models.deeppsl.sign.data.CLASS_SIZE,
                   'learning_rate': 1.0e-0,
                   'epochs': 20,
                   'save_path': None}

        sign_model.internal_init_model(None, options=options)
        pre_train_results = sign_model.internal_eval(test_data, options=options)
        sign_model.internal_fit(train_data, None, options=options)
        post_train_results = sign_model.internal_eval(test_data, options=options)

        with tempfile.TemporaryDirectory(suffix = '_TestNeuPSL') as temp_dir:
            save_path = os.path.join(temp_dir, 'tensorflow_model')
            options['save_path'] = save_path
            options['load_path'] = save_path
            sign_model.internal_save(options=options)

            saved_sign_model = tests.resources.models.deeppsl.sign.pytorch_model.SignModel()
            saved_sign_model.load(options=options)
            post_load_results = saved_sign_model.internal_eval(test_data, options=options)

        # First value is the objective, second value is the accuracy.

        # Assert that training helped.
        self.assertTrue(pre_train_results['loss'] > post_train_results['loss'])
        self.assertTrue(pre_train_results['metrics']['categorical_accuracy'] < post_train_results['metrics']['categorical_accuracy'])

        # Assert that the model produces the same results after being reloaded.
        self.assertClose(post_train_results['loss'], post_load_results['loss'])
        self.assertClose(post_train_results['metrics']['categorical_accuracy'], post_load_results['metrics']['categorical_accuracy'])
