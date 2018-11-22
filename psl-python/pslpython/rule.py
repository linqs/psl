'''
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2017 The Regents of the University of California

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

class Rule(object):
    """
    A PSL rule.
    For details on rule syntax, see https://github.com/linqs/psl/wiki/Rule-Specification
    """

    def __init__(self, rule_string, weighted = None, weight = None, squared = None):
        """
        Create a new PSL rule from a string.
        The string can optionally specify the weight and squred of a rule.
        If these properties are not specified in the string,
        then they must be specified using the parameters.
        A weighted rule can change its weight or squared status,
        however a weighted rule cannot convert into an unweighted rule
        and visa-versa.

        Args:
            rule_string: The text of the rule.
            weighted: A boolean representing if the rule is weighted.
                      Unweighted rules are constraints.
            weight: The weight of this rule.
            squared: A boolean representing if this rule's potential is squared.
        """

        pass

    @staticmethod
    def load_from_file(path):
        """
        Load a collection of rules from a file.

        Returns:
            A list of rules.
        """

        pass

    def set_weight(self, weight):
        """
        Set the weight of this rule.

        Args:
            weight: The new weight for this rule.
                    Must be non-negative.

        Returns:
            This rule.
        """

        pass

    def set_squared(self, squared):
        """
        Set the squared stats of this rule.

        Args:
            squared: The new squared status for this rule.

        Returns:
            This rule.
        """

        pass

    def __str__(self):
        """
        Create a PSL CLI compliant string representation of this string.

        Returns:
            A string representation of this rule.
        """

        pass
