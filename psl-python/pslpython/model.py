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

import os
import shutil
import tempfile
import uuid

class Model(object):
    """
    A PSL model.
    This is the primary class for running PSL.

    The python inferface to PSL utilizes PSL's CLI.
    For information on default values / behavior, see the CLI: https://github.com/linqs/psl/wiki/Using-the-CLI
    """

    CLI_INFERRED_OUTPUT_DIR = 'inferred-predicates'
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

    # TODO(eriq): Comment params when stable.
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
            temp_dir = tempfile.gettempdir()
        temp_dir = os.path.join(temp_dir, self._name)

        predicates = self._collect_predicates()

        data_file_path = self._write_data(temp_dir)
        rules_file_path = self._write_rules(temp_dir)

        cli_options = []

        cli_options.append('--infer')
        if (method != ''):
            cli_options.append(method)

        inferred_dir = os.path.join(temp_dir, CLI_INFERRED_OUTPUT_DIR)
        cli_options.append('--output')
        cli_options.append(inferred_dir)

        self._run_psl(data_file_path, rules_file_path, cli_options, psl_config, temp_dir, logger)

        # TODO(eriq): Collect the inference results.

        if (cleanup_temp):
            self._cleanup_temp()

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

    def _write_data(self, temp_dir):
        """
        Write out all the data for the predicates found in the rules of this model.
        Will clobber any existing data.
        Also writes out the CLI data file.

        Returns:
            The path to the data file.
        """

        pass

    def _write_rules(self, temp_dir):
        """
        Write out all the rules for this model.
        Will clobber any existing rules.

        Returns:
            The path to the rules file.
        """

        pass

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

        stdout_callback = lambda line: _log_stdout(logger, line)
        stderr_callback = lambda line: _log_stderr(logger, line)

        exit_status = execute(cli_options, stdout_callback, stderr_callback)

        # TODO(eriq): Yell if bad exit status.

    def _log_stdout(logger, line)
        # TODO(eriq): Check the line for a log level and pass it on to the logger. (Trance -> debug)
        print(line)

    def _log_stderr(logger, line):
        # TODO(eriq): If the logger exists pass to error, otherwise print to stdout.
        print(line)
