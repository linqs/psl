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
import unittest

from pslpython.predicate import Predicate

class PSLTest(unittest.TestCase):
    """
    All PSL tests need a base for standard setup and teardown.
    """

    TEST_DATA_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), 'data'))

    SIMPLE_ACQUAINTANCES_PSL_DIR = os.path.join(TEST_DATA_DIR, 'simple-acquaintances')
    SIMPLE_ACQUAINTANCES_DATA_DIR = os.path.join(SIMPLE_ACQUAINTANCES_PSL_DIR, 'data')

    EPSILON = 1e-4

    def assertClose(self, a, b):
        self.assertTrue(abs(a - b) <= self.EPSILON)
