"""
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
"""

import uuid

import pandas

import pslpython.runtime
from pslpython.predicate import Predicate
from pslpython.predicate import PredicateError
from pslpython.rule import Rule

class Model(object):
    """
    A PSL model.
    This is the primary class for running PSL.

    The python inferface to PSL utilizes PSL's Runtime.
    """

    TRUTH_COLUMN_NAME = 'truth'

    def __init__(self, name = None):
        """
        Create a PSL model.
        All models have some name.

        Args:
            name: The name of this model. If not supplied, then a random one is chosen.
        """

        self._name = name
        if (self._name is None):
            self._name = str(uuid.uuid4())

        self._rules = []
        # {normalized_name: predicate, ...}
        self._predicates = {}
        self._options = {}

    def add_predicate(self, predicate: Predicate):
        """
        Add a predicate to the model.
        Two predicates with the same name should never be added to the same model.

        Args:
            predicate: The predicate to add.

        Returns:
            This model.
        """

        if (predicate is None):
            raise ModelError('Cannot add a None predicate.')

        name = predicate.name()
        if (name in self._predicates and predicate != self._predicates[name]):
            raise PredicateError("Within a model, predciates must have unique names. Got a duplicate: %s." % (name))

        self._predicates[predicate.name()] = predicate
        return self

    def add_rule(self, rule: Rule):
        """
        Add a rule to the model.

        Rules are ordered and will maintain the order they were inserted in.
        The rule ordering does not effect inference.

        Args:
            rule: The rule to add.

        Returns:
            This model.
        """

        self._rules.append(rule)
        return self

    def infer(self, method = '', psl_options = {}, jvm_options = [], transform_config = None):
        """
        Run inference on this model.

        Args:
            method: The inference method to use.
            psl_options: Configuration options passed directly to PSL.
            jvm_options: Options passed to the JVM.
                         Most commonly '-Xmx' and '-Xms'.

        Returns:
            The inferred values as a map to dataframe.
            {predicate: frame, ...}
            The frame will have columns names that match the index of the argument and 'truth'.
        """

        config = self._prep_config(psl_options)

        config["options"]["runtime.learn"] = False
        config["options"]["runtime.inference"] = True
        config["options"]["runtime.inference.output.results"] = False

        if (method != ''):
            config["options"]["runtime.inference.method"] = method

        if (transform_config is not None):
            config = transform_config(config)

        raw_results = pslpython.runtime.run(config, jvm_options = jvm_options)
        results = self._collect_inference_results(raw_results)

        return results

    def learn(self, method = '', psl_options = {}, jvm_options = [], transform_config = None):
        """
        Run weight learning on this model.
        The new weights will be applied to this model.

        Args:
            method: The weight learning method to use.
            psl_options: Configuration passed directly to PSL.
            jvm_options: Options passed to the JVM.
                         Most commonly '-Xmx' and '-Xms'.

        Returns:
            This model.
        """

        config = self._prep_config(psl_options)

        config["options"]["runtime.learn"] = True
        config["options"]["runtime.inference"] = False

        if (method != ''):
            config["options"]["runtime.learn.method"] = method

        if (transform_config is not None):
            config = transform_config(config)

        raw_results = pslpython.runtime.run(config, jvm_options = jvm_options)
        self._fetch_new_weights(raw_results)

        return self

    def ground(self, psl_options = {}, jvm_options = [], transform_config = None):
        """
        Ground the model.
        """

        config = self._prep_config(psl_options)
        if (transform_config is not None):
            config = transform_config(config)

        ground_program = pslpython.runtime.ground(config, jvm_options = jvm_options)

        return ground_program

    def get_rules(self):
        return self._rules

    def get_predicates(self):
        """
        Get all the predicates keyed by their normalized name.
        If you are trying to get a specific predicate by name you should use Predicate.normalize_name(),
        or just use get_predicate() instead.

        Returns:
            A dict of predicates keyed by their normalized name.
        """

        return self._predicates

    def get_predicate(self, name):
        """
        Get a specific predicate or None if one does not exist.
        Name normalization will be handled internally.

        Returns:
            A predicate matching the name, or None.
        """

        return self._predicates[Predicate.normalize_name(name)]

    def get_name(self):
        return self._name

    def add_options(self, options):
        self._options.update(options)

    def get_options(self):
        return self._options

    def clear_options(self):
        self._options = {}

    def _collect_inference_results(self, raw_results):
        """
        Parse the results inferred by PSL.

        Returns:
            A dict with the keys being the predicate and the value being a dataframe with the data.
            {predicate: frame, ...}
            The frame will have columns names that match the index of the argument and 'truth'.
        """

        results = {}

        for atom in raw_results['atoms']:
            predicate = self._predicates[atom['predicate']]
            if (predicate is None):
                raise ModelError("Could not find predciate seen in results: " + atom['predicate'])

            if (predicate not in results):
                results[predicate] = []

            row = atom['arguments'] + [float(atom['value'])]

            for i in range(len(atom['arguments'])):
                if (predicate.types()[i] in Predicate.INT_TYPES):
                    row[i] = int(row[i])
                elif (predicate.types()[i] in Predicate.FLOAT_TYPES):
                    row[i] = float(row[i])

            results[predicate].append(row)

        for predicate in results:
            results[predicate] = pandas.DataFrame(results[predicate], columns = list(range(len(predicate))) + [Model.TRUTH_COLUMN_NAME])

        return results

    def _fetch_new_weights(self, raw_results):
        new_weights = []

        for rule in raw_results['rules']:
            new_weights.append(rule['weight'])

        if (len(new_weights) != len(self._rules)):
            raise ModelError("Mismatch between the number of base rules (%d) and the number of weighted rules (%d)." % (len(self._rules), len(new_weights)))

        for i in range(len(self._rules)):
            if (self._rules[i].weighted()):
                self._rules[i].set_weight(new_weights[i])

    def _prep_config(self, psl_options = {}):
        if (len(self._rules) == 0):
            raise ModelError("No rules specified to the model.")

        options = dict(self._options)
        options.update(psl_options)

        config = {
            "options": options,
            "rules": list(map(str, self._rules)),
            "predicates": {predicate.name() : predicate.to_dict() for predicate in self._predicates.values()},
        }

        return config

class ModelError(Exception):
    pass
