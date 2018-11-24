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

import pslpython.partition
import pslpython.util

import os
import shutil
import tempfile
import uuid
import yaml

class Model(object):
    """
    A PSL model.
    This is the primary class for running PSL.

    The python inferface to PSL utilizes PSL's CLI.
    For information on default values / behavior, see the CLI: https://github.com/linqs/psl/wiki/Using-the-CLI
    """

    CLI_INFERRED_OUTPUT_DIR = 'inferred-predicates'
    CLI_DELIM = "\t"
    TEMP_DIR_SUBDIR = 'psl-python'
    DATA_STORAGE_DIR = 'data'
    CLI_JAR_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), 'cli', 'psl-cli.jar'))

    def __init__(self, name = None):
        """
        Create a PSL model.
        All models have some name.

        Args:
            name: The name of this model. If not supplied, then a random one is chosen.
        """

        self._java_path = shutil.which('java')
        if (self._java_path is None):
            raise FileNotFoundError("Could not locate a java runtime (via https://docs.python.org/dev/library/shutil.html#shutil.which). Make sure that java exists within your path.")

        self._name = name
        if (self._name is None):
            self._name = uuid.uuid4()

        self._rules = []
        self._predicates = set()

    def add_predicate(self, predicate):
        """
        Add a predicate to the model.

        Args:
            predicate: The predicate to add.

        Returns:
            This model.
        """

        self._predicates.add(predicate)
        return self

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

        self._rules.append(rule)
        return self

    # TODO(eriq): Comment params when stable.
    # TODO(eriq): Should cleanup by default.
    def infer(self, method = '', psl_config = {}, logger = None, temp_dir = None, cleanup_temp = False):
        """
        Run inference on this model.

        Args:
            method: The inference method to use.
            logger: An optional logger to send the output of PSL to.
                    If not specified (None), then stdout/stderr will be used.
                    If False, then no output from PSL will be passed on.

        Returns:
            The inferred values as a map to dataframe.
            The keys of the map are the predicate being inferred.
        """

        if (len(self._rules) == 0):
            raise ArgumentException("No rules specified to the model.")

        if (temp_dir is None):
            temp_dir = os.path.join(tempfile.gettempdir(), Model.TEMP_DIR_SUBDIR)
        temp_dir = os.path.join(temp_dir, self._name)
        os.makedirs(temp_dir, exist_ok = True)

        data_file_path = self._write_data(temp_dir)
        rules_file_path = self._write_rules(temp_dir)

        cli_options = []

        cli_options.append('--infer')
        if (method != ''):
            cli_options.append(method)

        inferred_dir = os.path.join(temp_dir, Model.CLI_INFERRED_OUTPUT_DIR)
        cli_options.append('--output')
        cli_options.append(inferred_dir)

        self._run_psl(data_file_path, rules_file_path, cli_options, psl_config, logger)

        # TODO(eriq): Collect the inference results.

        if (cleanup_temp):
            self._cleanup_temp(temp_dir)

        # TODO(eriq): Inference results.
        return None

    def learn(self, method = '', logger = None):
        """
        Run weight learning on this model.
        The new weights will be applied to this model.

        Args:
            method: The weight learning method to use.
            logger: An optional logger to send the output of PSL to.
                    If not specified (None), then stdout/stderr will be used.
                    If False, then no output from PSL will be passed on.

        Returns:
            This model.
        """

        # TODO(eriq)
        pass

    def get_rules(self):
        return self._rules

    def get_name(self):
        return self._name

    def _cleanup_temp(self, temp_dir):
        shutil.rmtree(temp_dir)

    def _write_data(self, temp_dir):
        """
        Write out all the data for the predicates found in the rules of this model.
        Will clobber any existing data.
        Also writes out the CLI data file.

        Returns:
            The path to the data file.
        """

        data_file_path = os.path.join(temp_dir, self._name + '.data')

        data_storage_path = os.path.join(temp_dir, Model.DATA_STORAGE_DIR)
        os.makedirs(data_storage_path, exist_ok = True)

        self._write_cli_datafile(data_file_path, data_storage_path)
        self._write_cli_data(data_storage_path)

        return data_file_path

    def _write_cli_data(self, data_storage_path):
        for partition in pslpython.partition.Partition:
            for predicate in self._predicates:
                if (partition not in predicate.data()):
                    continue

                data = predicate.data()[partition]
                if (data is None or len(data) == 0):
                    continue

                filename = "%s_%s.txt" % (predicate.name(), partition.value)
                path = os.path.join(data_storage_path, filename)

                data.to_csv(path, sep = Model.CLI_DELIM, header = False, index = False)

    def _write_cli_datafile(self, data_file_path, data_storage_path):
        data_file_contents = {}

        predicates = {}
        for predicate in self._predicates:
            predicate_id = predicate.name() + '/' + str(len(predicate))

            types = []
            for predicate_type in predicate.types():
                types.append(predicate_type.value)

            open_closed = 'open'
            if (predicate.closed()):
                open_closed = 'closed'

            predicates[predicate_id] = [
                open_closed,
                {'types': types}
            ]

        data_file_contents['predicates'] = predicates

        for partition in pslpython.partition.Partition:
            partition_data = {}

            for predicate in self._predicates:
                if (partition not in predicate.data()):
                    continue

                data = predicate.data()[partition]
                if (data is None or len(data) == 0):
                    continue

                filename = "%s_%s.txt" % (predicate.name(), partition.value)
                # Make paths relative to the CLI data file for portability.
                partition_data[predicate.name()] = os.path.join(Model.DATA_STORAGE_DIR, filename)

            if (len(partition_data) > 0):
                data_file_contents[partition.value] = partition_data

        with open(data_file_path, 'w') as file:
            yaml.dump(data_file_contents, file, default_flow_style = False)

    def _write_rules(self, temp_dir):
        """
        Write out all the rules for this model.
        Will clobber any existing rules.

        Returns:
            The path to the rules file.
        """

        rules_file_path = os.path.join(temp_dir, self._name + '.psl')

        with open(rules_file_path, 'w') as file:
            for rule in self._rules:
                file.write(str(rule) + "\n")

        return rules_file_path

    # TODO(eriq): JVM options (xms)
    def _run_psl(self, data_file_path, rules_file_path, cli_options, psl_config, logger):
        cli_options.append('--model')
        cli_options.append(rules_file_path)

        cli_options.append('--data')
        cli_options.append(data_file_path)

        for (key, value) in psl_config.items():
            cli_options.append('-D')
            cli_options.append("%s=%s" % (key, value))

        # TODO(eriq): Set logging level to match logger.

        # TODO(eriq): Log command line used.
        # TODO(eriq): Maybe make a method that logs (check the logger and use if it exists, or stdout if it doesn't).

        cli_options.insert(0, self._java_path)
        cli_options.insert(1, '-jar')
        cli_options.insert(2, Model.CLI_JAR_PATH)

        stdout_callback = lambda line: Model._log_stdout(logger, line)
        stderr_callback = lambda line: Model._log_stderr(logger, line)

        # TODO(eriq): Log level
        print("Running: `%s`." % (pslpython.util.shell_join(cli_options)))

        exit_status = pslpython.util.execute(cli_options, stdout_callback, stderr_callback)

        # TODO(eriq): Yell if bad exit status.

    @staticmethod
    def _log_stdout(logger, line):
        # TODO(eriq): Check the line for a log level and pass it on to the logger. (Trance -> debug)
        print(line)

    @staticmethod
    def _log_stderr(logger, line):
        # TODO(eriq): If the logger exists pass to error, otherwise print to stdout.
        print(line)
