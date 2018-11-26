'''
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2018 The Regents of the University of California

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

from pslpython.predicate import Predicate
from tests.base_test import PSLTest

class TestRelationalTimeseries(PSLTest):
    def test_name_normalization(self):
        # [(input, expected), ...]
        names = [
            ('a', 'A'),
            ('foo', 'FOO'),
            ('Bar', 'BAR'),
            ('BAZ', 'BAZ'),
            ('123', '123'),
        ]

        for (input_name, expected_name) in names:
            predicate = Predicate(input_name, closed = True, size = 2)
            self.assertEqual(predicate.name(), expected_name)

    def test_duplicate_name(self):
        name = 'Foo'

        a = Predicate(name, closed = True, size = 2)

        try:
            b = Predicate(name, closed = True, size = 2)
            self.fail('Duplicate predicate name did not raise an exception.')
        except ValueError:
            # Expected
            pass

        try:
            b = Predicate(name, closed = True, size = 3)
            self.fail('Duplicate predicate name did not raise an exception.')
        except ValueError:
            # Expected
            pass
