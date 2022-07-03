"""
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
"""

import csv
import logging
import os
import re
import shutil
import tempfile
import uuid
import yaml

import pandas

import pslpython.util
from pslpython.partition import Partition
from pslpython.predicate import Predicate
from pslpython.predicate import PredicateError
from pslpython.rule import Rule

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
    TRUTH_COLUMN_NAME = 'truth'
    CLI_JAR_PATH = os.path.abspath(os.path.join(os.path.dirname(__file__), 'cli', 'psl-cli.jar'))

    PSL_LOGGING_OPTION = 'log4j.threshold'
    PSL_LOGGING_LEVEL_REGEX = r'\] (TRACE|DEBUG|INFO|WARN|ERROR|FATAL) '
    PYTHON_LOGGING_FORMAT_STRING = '%(relativeCreated)d [%(name)s PSL] %(levelname)s --- %(message)s'
    PYTHON_TO_PSL_LOGGING_LEVELS = {
        logging.CRITICAL: 'FATAL',
        logging.ERROR: 'ERROR',
        logging.WARNING: 'WARN',
        logging.INFO: 'INFO',
        logging.DEBUG: 'DEBUG',
        logging.NOTSET: 'INFO',
    }

    def __init__(self, name = None):
        """
        Create a PSL model.
        All models have some name.

        Args:
            name: The name of this model. If not supplied, then a random one is chosen.
        """

        self._java_path = shutil.which('java')
        if (self._java_path is None):
            raise ModelError("Could not locate a java runtime (via https://docs.python.org/dev/library/shutil.html#shutil.which). Make sure that java exists within your path.")

        self._name = name
        if (self._name is None):
            self._name = str(uuid.uuid4())

        self._rules = []
        # {normalized_name: predicate, ...}
        self._predicates = {}

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

    def infer(self, method = '', additional_cli_options = [], psl_config = {}, jvm_options = [], logger = None, temp_dir = None, cleanup_temp = True):
        """
        Run inference on this model.

        Args:
            method: The inference method to use.
            additional_cli_options: Additional options to pass direcly to the CLI.
                                   Here you would do things like select a database backend.
            psl_config: Configuration passed directly to the PSL core code.
                        https://github.com/eriq-augustine/psl/wiki/Configuration-Options
            jvm_options: Options passed to the JVM.
                         Most commonly '-Xmx' and '-Xms'.
            logger: An optional logger to send the output of PSL to.
                    If not specified (None), then a default INFO logger is used.
                    If False, only fatal PSL output will be passed on.
                    If no logging levels are sent via psl_config, PSL's logging level will be set
                    to match this logger's level.
            temp_dir: Where to write PSL files to for calling the CLI.
                      Defaults to Model.TEMP_DIR_SUBDIR inside the system's temp directory (tempfile.gettempdir()).
            cleanup_temp: Remove the files in temp_dir after running.

        Returns:
            The inferred values as a map to dataframe.
            {predicate: frame, ...}
            The frame will have columns names that match the index of the argument and 'truth'.
        """

        logger, temp_dir, data_file_path, rules_file_path = self._prep_run(logger, temp_dir)

        cli_options = []

        cli_options.append('--infer')
        if (method != ''):
            cli_options.append(method)

        inferred_dir = os.path.join(temp_dir, Model.CLI_INFERRED_OUTPUT_DIR)
        cli_options.append('--output')
        cli_options.append(inferred_dir)

        cli_options += additional_cli_options

        self._run_psl(data_file_path, rules_file_path, cli_options, psl_config, jvm_options, logger)
        results = self._collect_inference_results(inferred_dir)

        if (cleanup_temp):
            self._cleanup_temp(temp_dir)

        return results

    def learn(self, method = '', additional_cli_options = [], psl_config = {}, jvm_options = [], logger = None, temp_dir = None, cleanup_temp = True):
        """
        Run weight learning on this model.
        The new weights will be applied to this model.

        Args:
            method: The weight learning method to use.
            additional_cli_options: Additional options to pass direcly to the CLI.
                                   Here you would do things like select a database backend.
            psl_config: Configuration passed directly to the PSL core code.
                        https://github.com/eriq-augustine/psl/wiki/Configuration-Options
            jvm_options: Options passed to the JVM.
                         Most commonly '-Xmx' and '-Xms'.
            logger: An optional logger to send the output of PSL to.
                    If not specified (None), then a default INFO logger is used.
                    If False, only fatal PSL output will be passed on.
                    If no logging levels are sent via psl_config, PSL's logging level will be set
                    to match this logger's level.
            temp_dir: Where to write PSL files to for calling the CLI.
                      Defaults to Model.TEMP_DIR_SUBDIR inside the system's temp directory (tempfile.gettempdir()).
            cleanup_temp: Remove the files in temp_dir after running.

        Returns:
            This model.
        """

        logger, temp_dir, data_file_path, rules_file_path = self._prep_run(logger, temp_dir)

        cli_options = []

        cli_options.append('--learn')
        if (method != ''):
            cli_options.append(method)

        cli_options += additional_cli_options

        self._run_psl(data_file_path, rules_file_path, cli_options, psl_config, jvm_options, logger)
        self._fetch_new_weights(rules_file_path)

        if (cleanup_temp):
            self._cleanup_temp(temp_dir)

        return self

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

    def _collect_inference_results(self, inferred_dir):
        """
        Get the inferred data written by PSL.

        Returns:
            A dict with the keys being the predicate and the value being a dataframe with the data.
            {predicate: frame, ...}
            The frame will have columns names that match the index of the argument and 'truth'.
        """

        results = {}

        for dirent in os.listdir(inferred_dir):
            path = os.path.join(inferred_dir, dirent)

            if (not os.path.isfile(path)):
                continue

            predicate_name = os.path.splitext(dirent)[0]
            predicate = None
            for possible_predicate in self._predicates.values():
                if (possible_predicate.name() == predicate_name):
                    predicate = possible_predicate
                    break

            if (predicate is None):
                raise ModelError("Unable to find predicate that matches name if inferred data file. Predicate name: '%s'. Inferred file path: '%s'." % (predicate_name, path))

            columns = list(range(len(predicate))) + [Model.TRUTH_COLUMN_NAME]
            data = pandas.read_csv(path, delimiter = Model.CLI_DELIM, names = columns, header = None, skiprows = None, quoting = csv.QUOTE_NONE)

            # Clean up and convert types.
            for i in range(len(data.columns) - 1):
                if (predicate.types()[i] in Predicate.INT_TYPES):
                    data[data.columns[i]] = data[data.columns[i]].apply(lambda val: int(val))
                elif (predicate.types()[i] in Predicate.FLOAT_TYPES):
                    data[data.columns[i]] = data[data.columns[i]].apply(lambda val: float(val))

            data[Model.TRUTH_COLUMN_NAME] = pandas.to_numeric(data[Model.TRUTH_COLUMN_NAME])

            results[predicate] = data

        return results

    def _fetch_new_weights(self, base_rules_file_path):
        new_weights = []

        learned_rules_path = re.sub(r'\.psl$', '-learned.psl', base_rules_file_path)
        with open(learned_rules_path, 'r') as file:
            for line in file:
                line = line.strip()
                if (line == ''):
                    continue

                # Unweighted
                if (line.endswith('.')):
                    new_weights.append(None)
                    continue

                parts = line.split(':')
                new_weights.append(float(parts[0]))

        if (len(new_weights) != len(self._rules)):
            raise ModelError("Mismatch between the number of base rules and the number of weighted rules. Base rules: '%s', learned rules: '%s'." % (base_rules_file_path, learned_rules_path))

        for i in range(len(self._rules)):
            if (self._rules[i].weighted()):
                self._rules[i].set_weight(new_weights[i])

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
        for partition in Partition:
            for predicate in self._predicates.values():
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
        for predicate in self._predicates.values():
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

        for partition in Partition:
            partition_data = {}

            for predicate in self._predicates.values():
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

    def _prep_run(self, logger = None, temp_dir = None):
        """
        Run weight learning on this model.
        The new weights will be applied to this model.

        Args:
            logger: An optional logger to send the output of PSL to.
                    If not specified (None), then a default INFO logger is used.
                    If False, only fatal PSL output will be passed on.
            temp_dir: Where to write PSL files to for calling the CLI.
                      Defaults to Model.TEMP_DIR_SUBDIR inside the system's temp directory (tempfile.gettempdir()).

        Returns:
            A prepped logger, a usable temp_dir, the path to the CLI data file, and the path to the CLI rules file.
        """

        if (len(self._rules) == 0):
            raise ModelError("No rules specified to the model.")

        if (logger is None or logger == False):
            level = logging.INFO
            if (logger == False):
                level = logging.CRITICAL

            logging.basicConfig(format = Model.PYTHON_LOGGING_FORMAT_STRING)
            logger = logging.getLogger(__name__)
            logger.setLevel(level)

        if (temp_dir is None):
            temp_dir = os.path.join(tempfile.gettempdir(), Model.TEMP_DIR_SUBDIR)
        temp_dir = os.path.join(temp_dir, self._name)
        os.makedirs(temp_dir, exist_ok = True)

        data_file_path = self._write_data(temp_dir)
        rules_file_path = self._write_rules(temp_dir)

        return logger, temp_dir, data_file_path, rules_file_path

    def _run_psl(self, data_file_path, rules_file_path, cli_options, psl_config, jvm_options, logger):
        command = [
            self._java_path
        ]

        for option in jvm_options:
            command.append(str(option))

        command += [
            '-jar',
            Model.CLI_JAR_PATH,
            '--model',
            rules_file_path,
            '--data',
            data_file_path,
        ]

        # Set the PSL logging level to match the logger (if not explicitly set in the additional options).
        if (Model.PSL_LOGGING_OPTION not in psl_config):
            psl_config[Model.PSL_LOGGING_OPTION] = Model.PYTHON_TO_PSL_LOGGING_LEVELS[logger.level]

        for option in cli_options:
            command.append(str(option))

        for (key, value) in psl_config.items():
            command.append('-D')
            command.append("%s=%s" % (key, value))

        log_callback = lambda line: Model._log_stdout(logger, line)

        logger.debug("Running: `%s`." % (pslpython.util.shell_join(command)))
        exit_status = pslpython.util.execute(command, log_callback)

        if (exit_status != 0):
            raise ModelError("PSL returned a non-zero exit status: %d." % (exit_status))

    @staticmethod
    def _log_stdout(logger, line):
        match = re.search(Model.PSL_LOGGING_LEVEL_REGEX, line)
        if (match is None):
            # On a failed lookup, log to error.
            logger.error('(Unknown PSL logging level) -- ' + line)
            return

        level = match.group(1)
        if (level == 'TRACE' or level == 'DEBUG'):
            logger.debug(line)
        elif (level == 'INFO'):
            logger.info(line)
        elif (level == 'WARN'):
            logger.warning(line)
        elif (level == 'ERROR'):
            logger.error(line)
        elif (level == 'FATAL'):
            logger.critical(line)
        else:
            logger.error('(Unknown PSL logging level) -- ' + line)

    @staticmethod
    def _log_stderr(logger, line):
        logger.error('(PSL stderr) -- ' + line)

class ModelError(Exception):
    pass
