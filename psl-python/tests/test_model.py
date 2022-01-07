'''
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2022 The Regents of the University of California

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

import tests.data.models.simpleacquaintances
from pslpython.model import Model
from pslpython.predicate import Predicate
from pslpython.predicate import PredicateError
from tests.base_test import PSLTest

class TestModel(PSLTest):
    def test_simple_acquaintances(self):
        results = tests.data.models.simpleacquaintances.run()

        self.assertEquals(len(results), 1)

        predicate, frame = list(results.items())[0]
        self.assertEquals(predicate.name(), 'KNOWS')
        self.assertEquals(len(frame), 52)

    def test_duplicate_predicate_name(self):
        model = Model('test-predicate')
        predicate_name = 'Foo'

        a = Predicate(predicate_name, closed = True, size = 2)
        model.add_predicate(a)

        # Adding the same predicate again should by no issue.
        model.add_predicate(a)

        try:
            b = Predicate(predicate_name, closed = True, size = 2)
            model.add_predicate(b)
            self.fail('Duplicate predicate name did not raise an exception.')
        except PredicateError:
            # Expected
            pass

        try:
            b = Predicate(predicate_name, closed = True, size = 3)
            model.add_predicate(b)
            self.fail('Duplicate predicate name did not raise an exception.')
        except PredicateError:
            # Expected
            pass

    def test_numeric_data(self):
        data_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), 'data', 'simple-acquaintances', 'numeric_data'))
        results = tests.data.models.simpleacquaintances.run(data_dir)

        self.assertEquals(len(results), 1)

        predicate, frame = list(results.items())[0]
        self.assertEquals(predicate.name(), 'KNOWS')
        self.assertEquals(len(frame), 6)
