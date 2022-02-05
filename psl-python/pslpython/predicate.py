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

import csv
import enum
import pandas

from pslpython.partition import Partition

class Predicate(object):
    """
    A PSL predicate.

    This class controls not just the semantic meaning of a predicate,
    but also the data for this predciate.
    """

    DEFAULT_FILE_DELIMITER = "\t"

    class ArgType(enum.Enum):
        UNIQUE_STRING_ID = 'UniqueStringID'
        UNIQUE_INT_ID = 'UniqueIntID'
        STRING = 'String'
        INT = 'Integer'
        LONG = 'Long'
        DOUBLE = 'Double'

    STRING_TYPES = {ArgType.UNIQUE_STRING_ID, ArgType.STRING}
    INT_TYPES = {ArgType.UNIQUE_INT_ID, ArgType.INT, ArgType.LONG}
    FLOAT_TYPES = {ArgType.DOUBLE}

    DEFAULT_ARG_TYPE = ArgType.UNIQUE_STRING_ID
    DEFAULT_TRUTH_VALUE = 1.0

    def __init__(self, raw_name: str, closed: bool, size: int = None, arg_types = None):
        """
        Construct a new predicate.

        Predicates must have unique names.

        Enough information must be supplied to infer the amount and types
        of predicate arguments.
        A size without types can be supplied,
        in which case all arguments will default to the string type.
        No size with types can be supplied (as the size is inferred by the types).
        Both a size and types can be supplied, but the size must match the list size.
        Two predicates with the same name should never be added to the same model.

        Args:
            name: The name of the predicate.
            size: The number of arguments to this predicate.
            arg_types: The types of arguments to this predicate.
        """

        self._types = []
        # {partition: dataframe, ...}
        # Note that the dataframes have a spot for the truth value.
        self._data = {}
        self._name = Predicate.normalize_name(raw_name)
        self._closed = closed

        if (size is None and (arg_types is None or len(arg_types) == 0)):
            raise PredicateError("Predicates must have a size and/or type infornation, neither supplied.")

        if (size is not None and size < 1):
            raise PredicateError("Predicates must have a positive size. Got: %d." % (size))

        if (size is not None and arg_types is not None and len(arg_types) != size):
            raise PredicateError("Mismatch between supplied predicate size (%d) and size of supplied argument types (%d)." % (size, len(arg_types)))

        # Arg checking complete, now construct the types.

        if (size is None):
            size = len(arg_types)

        if (arg_types is None):
            arg_types = [Predicate.DEFAULT_ARG_TYPE] * size

        for arg_type in arg_types:
            if (not isinstance(arg_type, Predicate.ArgType)):
                raise PredicateError("Supplied argument type was not a Predicate.ArgType: %s (%s)." % (arg_type, str(type(arg_type))))
            self._types.append(arg_type)

        self.clear_data()

    def add_data_file(self, partition: Partition, path, has_header = False, delim = DEFAULT_FILE_DELIMITER):
        """
        Add an entire file to the predicate.

        Args:
            partition: The partition to add this data into.
            path: The path to the file to load.
            delim: The field separator used in the file to be loaded.

        Returns:
            This predicate.
        """

        skiprows = None
        if (has_header):
            skiprows = 1

        data = pandas.read_csv(path, delimiter = delim, header = None, skiprows = skiprows, quoting = csv.QUOTE_NONE)

        try:
            return self.add_data(partition, data)
        except Exception as ex:
            error = PredicateError("File (%s) was not formatted properly for the %s predicate (used delimiter '%s')." % (path, self._name, delim))
            raise error from ex

    def add_data_row(self, partition: Partition, args, truth_value: float = 1.0):
        """
        Add a single record to the predicate.

        Args:
            partition: The partition to add this data into.
            args: The args of the record to add (as a list).
            truth_value: The truth value of the record to add (defaults to 1.0).

        Returns:
            This predicate.
        """

        size = len(self._types)
        data = pandas.DataFrame([args + [truth_value]], columns = list(range(size + 1)))
        return self.add_data(partition, data)

    def add_data(self, partition: Partition, data):
        """
        Add several records to the predciate.
        The data can be in the form of a list of lists or a dataframe.

        Args:
            partition: The partition to add this data into.
            data: The data to add to this predciate.

        Returns:
            This predicate.
        """

        if (not isinstance(partition, Partition)):
            raise PredicateError("Supplied partition is not a pslpython.partition.Partition: %s (%s)." % (partition, type(partition)))

        size = len(self._types)
        data = pandas.DataFrame(data)

        if (len(data.columns) not in (size, size + 1)):
            raise PredicateError("Data was not formatted properly for the %s predicate. Expecting %d or %d columns, got %d." % (self._name, size, size + 1, len(data.columns)))

        if (len(data.columns) == size):
            # Missing the truth value.
            data[size] = Predicate.DEFAULT_TRUTH_VALUE

        self._data[partition] = pandas.concat([self._data[partition], data], ignore_index = True)

        return self

    def clear_data(self):
        """
        Clear and initialize the predicate's data.

        Returns:
            This predicate.
        """

        self._data.clear()
        for partition in Partition:
            # Column names don't matter, only order.
            # +1 for truth value.
            self._data[partition] = pandas.DataFrame(columns = list(range(len(self._types) + 1)))

        return self

    def closed(self):
        return self._closed

    def name(self):
        return self._name

    def types(self):
        return self._types

    def __len__(self):
        return len(self._types)

    def data(self):
        return self._data

    @staticmethod
    def normalize_name(name):
        return name.upper()

class PredicateError(Exception):
    pass
