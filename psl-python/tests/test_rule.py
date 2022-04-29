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

from pslpython.rule import Rule
from pslpython.rule import RuleError
from tests.base_test import PSLTest

class TestRule(PSLTest):
    def test_init_args(self):
        failing_configs = [
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B) .',
                'weighted': True
            }, 'Unweighted mismatch.'),
            ({
                'rule_string': '1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
                'weighted': False,
            }, 'Weighted mismatch.'),
            ({
                'rule_string': '1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) .',
            }, 'Both weighted and unweighted.'),
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B) ^2 .',
            }, 'Squared unweighted.'),
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B) . ^2',
            }, 'Unweighted squared.'),
            ({
                'rule_string': '1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) . ^2',
            }, 'Weight unweighted squared.'),
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
            }, 'Missing weight 1.'),
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
                'weighted': True,
            }, 'Missing weight 2.'),
            ({
                'rule_string': '1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
                'weight': 0.0,
            }, 'Weight mismatch.'),
            ({
                'rule_string': '-1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
            }, 'Negative weight (string).'),
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
                'weight': -1.0,
            }, 'Negative weight (arg).'),
            ({
                'rule_string': '1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) ^2',
                'squared': False,
            }, 'Squred mismatch 1.'),
            ({
                'rule_string': '1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) ^1',
                'squared': True,
            }, 'Squred mismatch 2.'),
            ({
                'rule_string': 'Nice(A) & Nice(B) & (A != B) -> Friends(A, B)',
                'weighted': False,
                'squared': True,
            }, 'Squred mismatch 2.'),
        ]

        for (args, reason) in failing_configs:
            try:
                rule = Rule(**args)
                self.fail('Failed to raise exception on: ' + reason)
            except RuleError as ex:
                # Expected
                pass

    def test_set_weight(self):
        rule = Rule('1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) ^2')
        self.assertEquals(rule.weight(), 1.0)

        rule.set_weight(5.0)
        self.assertEquals(rule.weight(), 5.0)

        rule.set_weight(0.0)
        self.assertEquals(rule.weight(), 0.0)

        try:
            rule.set_weight(-1.0)
            self.fail('No exception when setting negative weight.')
        except RuleError as ex:
            # Expected
            pass

        rule = Rule('Nice(A) & Nice(B) & (A != B) -> Friends(A, B) .')
        try:
            rule.set_weight(1.0)
            self.fail('No exception when setting weight of an unweighted rule.')
        except RuleError as ex:
            # Expected
            pass

    def test_set_squared(self):
        rule = Rule('1.0: Nice(A) & Nice(B) & (A != B) -> Friends(A, B) ^2')
        self.assertEquals(rule.squared(), True)

        rule.set_squared(False)
        self.assertEquals(rule.squared(), False)

        rule.set_squared(True)
        self.assertEquals(rule.squared(), True)

        rule = Rule('Nice(A) & Nice(B) & (A != B) -> Friends(A, B) .')
        try:
            rule.set_squared(True)
            self.fail('No exception when squared an unweighted rule.')
        except RuleError as ex:
            # Expected
            pass

    def test_load_from_file(self):
        path = os.path.join(PSLTest.SIMPLE_ACQUAINTANCES_PSL_DIR, 'simple-acquaintances.psl')
        rules = Rule.load_from_file(path)

        expected = [
            '20.00: Lived(P1, L) & Lived(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2',
            '5.00: Lived(P1, L1) & Lived(P2, L2) & (P1 != P2) & (L1 != L2) -> !Knows(P1, P2) ^2',
            '10.00: Likes(P1, L) & Likes(P2, L) & (P1 != P2) -> Knows(P1, P2) ^2',
            '5.00: Knows(P1, P2) & Knows(P2, P3) & (P1 != P3) -> Knows(P1, P3) ^2',
            'Knows(P1, P2) = Knows(P2, P1) .',
            '5.00: !Knows(P1, P2) ^2',
        ]

        self.assertEquals(len(rules), len(expected))
        for i in range(len(rules)):
            self.assertEquals(rules[i].to_string(2), expected[i])
