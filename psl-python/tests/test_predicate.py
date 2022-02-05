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

import pandas

from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.predicate import PredicateError
from tests.base_test import PSLTest

class TestPredicate(PSLTest):
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

    def test_init_args(self):
        failing_configs = [
            ({'raw_name': 'Foo', 'closed': False}, 'No size supplied.'),
            ({'raw_name': 'Foo', 'closed': False, 'size': -1}, 'Negative size.'),
            ({'raw_name': 'Foo', 'closed': False, 'size': 0}, 'Zero size.'),
            ({'raw_name': 'Foo', 'closed': False, 'size': 2, 'arg_types': [Predicate.ArgType.UNIQUE_INT_ID]}, 'Type size mismatch.'),
            ({'raw_name': 'Foo', 'closed': False, 'size': 1, 'arg_types': ['UniqueIntID']}, 'Non-enum arg type.'),
        ]

        for (args, reason) in failing_configs:
            try:
                predicate = Predicate(**args)
                self.fail('Failed to raise exception on: ' + reason)
            except PredicateError as ex:
                # Expected
                pass

    def test_add_record(self):
        predicate = Predicate('Foo', closed = True, size = 2)

        predicate.add_data_row(Partition.OBSERVATIONS, ['A', 'B'])
        predicate.add_data_row(Partition.OBSERVATIONS, ['C', 'D'], 0.5)
        predicate.add_data_row(Partition.OBSERVATIONS, [1, 2])

        expected = pandas.DataFrame([
            ['A', 'B', 1.0],
            ['C', 'D', 0.5],
            [1, 2, 1.0],
        ])

        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

    def test_add_frame(self):
        predicate = Predicate('Foo', closed = True, size = 2)

        input_data = pandas.DataFrame([
            ['A', 'B'],
            ['C', 'D'],
            [1, 2],
        ])
        predicate.add_data(Partition.OBSERVATIONS, input_data)

        expected = pandas.DataFrame([
            ['A', 'B', 1.0],
            ['C', 'D', 1.0],
            [1, 2, 1.0],
        ])

        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

    def test_add_list(self):
        predicate = Predicate('Foo', closed = True, size = 2)

        input_data = [
            ['A', 'B', 0.0],
            ['C', 'D', 0.5],
            [1, 2, 1.0],
        ]
        predicate.add_data(Partition.OBSERVATIONS, input_data)

        expected = pandas.DataFrame([
            ['A', 'B', 0.0],
            ['C', 'D', 0.5],
            [1, 2, 1.0],
        ])

        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

    def test_add_file(self):
        predicate = Predicate('1', closed = True, size = 2)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small.txt')
        predicate.add_data_file(Partition.OBSERVATIONS, path)
        expected = pandas.DataFrame([
            ['A', 'B', 1.0],
            ['C', 'D', 1.0],
            ['1', '2', 1.0],
        ])
        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

        predicate = Predicate('2', closed = True, size = 2)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small.txt')
        predicate.add_data_file(Partition.OBSERVATIONS, path, has_header = True)
        expected = pandas.DataFrame([
            ['C', 'D', 1.0],
            ['1', '2', 1.0],
        ])
        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

        predicate = Predicate('3', closed = True, size = 2)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small.csv')
        predicate.add_data_file(Partition.OBSERVATIONS, path, delim = ',')
        expected = pandas.DataFrame([
            ['A', 'B', 1.0],
            ['C', 'D', 1.0],
            ['1', '2', 1.0],
        ])
        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

        predicate = Predicate('4', closed = True, size = 2)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small_truth.txt')
        predicate.add_data_file(Partition.OBSERVATIONS, path)
        expected = pandas.DataFrame([
            ['A', 'B', 0.0],
            ['C', 'D', 0.5],
            ['1', '2', 1.0],
        ])
        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)

    def test_add_wrong_number_of_cols(self):
        # Too few.
        predicate = Predicate('1', closed = True, size = 3)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small.txt')
        try:
            predicate.add_data_file(Partition.OBSERVATIONS, path)
            self.fail('Failed to raise exception when too few columns.')
        except PredicateError as ex:
            # Expected
            pass

        # Too many.
        predicate = Predicate('2', closed = True, size = 1)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small_truth.txt')
        try:
            predicate.add_data_file(Partition.OBSERVATIONS, path)
            self.fail('Failed to raise exception when too many columns.')
        except PredicateError as ex:
            # Expected
            pass

    def test_no_quoting(self):
        predicate = Predicate('1', closed = True, size = 2)
        path = os.path.join(PSLTest.TEST_DATA_DIR, 'misc', 'binary_small_quoted.txt')
        predicate.add_data_file(Partition.OBSERVATIONS, path)
        expected = pandas.DataFrame([
            ['A', 'B', 1.0],
            ['\'C\'', '"D"', 1.0],
            ['1  ', '   2   ', 1.0],
            ['"3  "', '\'   4   \'', 1.0],
            ['\'\'5\'\'', '""6""', 1.0],
        ])
        pandas.testing.assert_frame_equal(predicate._data[Partition.OBSERVATIONS], expected, check_dtype = False)
