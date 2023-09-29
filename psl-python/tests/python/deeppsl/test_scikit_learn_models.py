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

import numpy

import tests.python.base_test
import tests.resources.models.deeppsl.sign.data
import tests.resources.models.deeppsl.sign.scikit_learn_model

THIS_DIR = os.path.abspath(os.path.dirname(os.path.realpath(__file__)))
DATA_DIR = os.path.join(THIS_DIR, '..', '..', 'resources', 'data')
SIGN_DIR = os.path.join(DATA_DIR, 'sign')

class TestSciKitLearnModels(tests.python.base_test.PSLTest):
    def setUp(self):
        global sklearn

        # Skip these tests if sklearn is not installed.
        try:
            sys.argv.append('__workaround__')
            import sklearn
            sys.argv.pop()
        except ImportError:
            self.skipTest("SciKitLearn is not installed.")

        numpy.random.seed(4)

    def tearDown(self):
        pass

    def test_sign_model(self):
        sign_model = tests.resources.models.deeppsl.sign.scikit_learn_model.SignModel()
        x_train, y_train, x_test, y_test = tests.resources.models.deeppsl.sign.data.get_deep_data(SIGN_DIR)

        train_data = [x_train, [label[1] for label in y_train]]
        test_data = [x_test, [label[1] for label in y_test]]

        options = {}

        sign_model.internal_init_model(None, options=options)
        sign_model.internal_fit(train_data, None, options=options)
        results = sign_model.internal_eval(test_data, options=options)

        self.assertTrue(results['metrics']['categorical_accuracy'] > 0.5)
