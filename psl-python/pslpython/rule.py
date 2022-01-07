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

import math
import re

class Rule(object):
    """
    A PSL rule.
    For details on rule syntax, see https://github.com/linqs/psl/wiki/Rule-Specification

    Very little rule parsing will happen here.
    The rule weight and squared will be parsed out of the rule string, but the actual body of the rule will be left alone.
    """

    WEIGHT_REGEX = r'^(-?\d+(\.\d+)?):\s*'
    UNWEIGHTED_REGEX = r'\s+\.\s*$'
    SQUARED_REGEX = r'\s+(\^[12])\s*$'

    def __init__(self, rule_string: str, weighted: bool = None, weight: float = None, squared: bool = None):
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

        self._rule_body = None
        self._weighted = None
        self._weight = None
        self._squared = None

        rule_string = rule_string.strip()
        raw_rule_string = rule_string

        # First check for an unweighted rule.

        match = re.search(Rule.UNWEIGHTED_REGEX, rule_string)
        if (match is not None):
            if (weighted is not None and weighted):
                raise RuleError(raw_rule_string, 'Rule string has an unweighted marker, but the passed in argument says the rule is weighted.')

            weighted = False

            # Remove the unweight marker from the string.
            rule_string = re.sub(Rule.UNWEIGHTED_REGEX, '', rule_string)

            # Make sure there is not also a squared.
            match = re.search(Rule.SQUARED_REGEX, rule_string)
            if (match is not None):
                raise RuleError(raw_rule_string, 'Rule string is squared (implying weighted) and has an unweighted marker.')

        elif (weighted is None):
            # Note that in the absence of an unweighted specifier or argument, we will assume that the rule is weighted.
            # So, if we don't get a weight later we will raise an error.
            weighted = True

        self._weighted = weighted

        # Now check the weight.

        match = re.search(Rule.WEIGHT_REGEX, rule_string)
        if (match is not None):
            parsed_weight = float(match.group(1))

            if (weight is not None and not math.isclose(parsed_weight, weight)):
                raise RuleError(raw_rule_string, "Weight from rule string (%s) and passed in weight (%f) do not match." % (match.group(1), weight))

            weight = parsed_weight

            # Remove the weight from the string.
            rule_string = re.sub(Rule.WEIGHT_REGEX, '', rule_string)

        if (weight is not None and not self._weighted):
            raise RuleError(raw_rule_string, 'Rule was declared as unweighted, but a weight was supplied.')

        if (weight is None and self._weighted):
            raise RuleError(raw_rule_string, 'Rule was declared as weighted, but no weight was supplied through parameters or rule string.')

        if (weight is not None and weight < 0):
            raise RuleError(raw_rule_string, "Negative weights (%f) are not allowed." % (weight))

        self._weight = weight

        # Check the squared status.

        match = re.search(Rule.SQUARED_REGEX, rule_string)
        if (match is not None):
            # Note that '^1' is also allowed.
            parsed_squared = match.group(1) == '^2'

            if (squared is not None and (parsed_squared != squared)):
                raise RuleError(raw_rule_string, "Squred status from rule string (%s) does not match argument (%s)." % (parsed_squared, squared))

            squared = parsed_squared

            # Remove the square from the string.
            rule_string = re.sub(Rule.SQUARED_REGEX, '', rule_string)

            # Make sure there is not also an unweighted marker.
            match = re.search(Rule.UNWEIGHTED_REGEX, rule_string)
            if (match is not None):
                raise RuleError(raw_rule_string, 'Rule string is squared (implying weighted) and has an unweighted marker.')

        elif (squared is None):
            squared = False

        if (squared and not self._weighted):
            raise RuleError(raw_rule_string, 'A rule cannot be both squared and unweighted.')

        self._squared = squared

        # All information parsed.
        self._rule_body = rule_string

    def set_weight(self, weight: float):
        """
        Set the weight of this rule.

        Args:
            weight: The new weight for this rule.
                    Must be non-negative.

        Returns:
            This rule.
        """

        if (weight < 0):
            raise RuleError(self._rule_body, "Negative weights (%f) are not allowed." % (weight))

        if (not self._weighted):
            raise RuleError(self._rule_body, 'Unweighted rules cannot take a weight.')

        self._weight = weight

        return self

    def set_squared(self, squared: bool):
        """
        Set the squared stats of this rule.

        Args:
            squared: The new squared status for this rule.

        Returns:
            This rule.
        """

        if (not self._weighted):
            raise RuleError(self._rule_body, 'Unweighted rules cannot be squared.')

        self._squared = squared

        return self

    def weight(self):
        return self._weight

    def weighted(self):
        return self._weighted

    def squared(self):
        return self._squared

    def __str__(self):
        return self.to_string()

    def to_string(self, weight_places: int = None):
        """
        Create a PSL CLI compliant string representation of this string.
        Most non-testing people will just use str().

        Args:
            weight_places: The number of decimal places to use.
                           Defaults to not caring and doing whatever "%f" does.

        Returns:
            A string representation of this rule.
        """

        text = []

        if (self._weighted):
            if (weight_places is None):
                text.append("%f:" % (self._weight))
            else:
                format_string = "%%.%df:" % (weight_places)
                text.append(format_string % (self._weight))

        text.append(self._rule_body)

        if (self._squared):
            text.append('^2')
        elif (not self._weighted):
            text.append('.')

        return ' '.join(text)

    @staticmethod
    def load_from_file(path):
        """
        Load a collection of rules from a file.

        TODO:
            Allow for comments.

        Returns:
            A list of rules.
        """

        rules = []

        with open(path, 'r') as  file:
            for line in file:
                line = line.strip()
                if (line == ''):
                    continue

                rules.append(Rule(line))

        return rules

class RuleError(Exception):
    def __init__(self, raw_rule_string, message):
        super().__init__(message + " Rule: \"%s\"." % (raw_rule_string))
