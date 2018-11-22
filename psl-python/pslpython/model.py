"""
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
"""

class Model(object):
    """
    A PSL model.
    This is the primary class for running PSL.

    The python inferface to PSL utilizes PSL's CLI.
    For information on default values / behavior, see the CLI: https://github.com/linqs/psl/wiki/Using-the-CLI
    """

    def __init__(self):
        pass

    def add_predicate(self, predicate):
        """
        Add a predicate to the model.
        All predicates should be added before adding rules that use said predicates.

        Args:
            predicate: The predicate to add.

        Returns:
            This model.
        """

        pass

    def add_rule(self, rule):
        """
        Add a rule to the model.

        Rules are ordered and will maintain the order they were inserted in.
        The rule ordering does not effect inference.

        Args:
            rule: The rule to add.

        Returns:
            This model.
        """

        pass

    def infer(self, method = ''):
        """
        Run inference on this model.

        Args:
            method: The inference method to use.

        Returns:
            The inferred values as a map to dataframe.
            The keys of the map are the predicate being inferred.
        """

        pass

    def learn(self, method = ''):
        """
        Run weight learning on this model.
        The new weights will be applied to this model.

        Args:
            method: The weight learning method to use.

        Returns:
            This model.
        """

        pass

    def get_rules(self):
        """
        Get the rules used by this model.

        Returns:
            The rules used by this model.
        """

        pass
