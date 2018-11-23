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

    DEFAULT_ARG_TYPE = ArgType.UNIQUE_STRING_ID
    DEFAULT_TRUTH_VALUE = 1.0

    # Predicates must have unique names.
    _used_names = set()

    def __init__(self, raw_name, size = None, arg_types = None):
        """
        Construct a new predicate.

        Predicates must have unique names.

        Enough information must be supplied to infer the amount and types
        of predicate arguments.
        A size without types can be supplied,
        in which case all arguments will default to the string type.
        No size with types can be supplied (as the size is inferred by the types).
        Both a size and types can be supplied, but the size must match the list size.

        Args:
            name: The name of the predicate.
            size: The number of arguments to this predicate.
            arg_types: The types of arguments to this predicate.
        """

        self._types = []
        # {partition: dataframe, ...}
        # Note that the dataframes have a spot for the truth value.
        self._data = {}
        self._name = _normalize_name(raw_name)

        if (self._name in _used_names):
            raise ValueError("Predciates must have unique names. Got duplicate: %s (%s)." % (name, raw_name))

        if (size is None and (arg_tpes is None or len(arg_types) == 0)):
            raise ValueError("Predicates must have a size and/or type infornation, neither supplied.")

        if (size is not None and size < 1):
            raise ValueError("Predicates must have a positive size. Got: %d." % (size))

        if (size is not None and args is not None and len(args) != size):
            raise ValueError("Mismatch between supplied predicate size (%d) and size of supplied argument types (%d)." % (size, len(arg_types)))

        # Arg checking complete, not make the types.

        if (size is None):
            size = len(arg_types)

        if (arg_types is None):
            arg_types = [DEFAULT_ARG_TYPE] * size

        for (arg_type in arg_types):
            if (not isinstance(arg_type, ArgType)):
                raise ValueError("Supplied argument type was not a Predicate.ArgType: %s (%s)." % (arg_type, str(type(arg_type))))
            self._types.append(arg_type)

        for partition in Partition:
            # Column names don't matter, only order.
            # +1 for truth value.
            self._data[partition] = pandas.DataFrame(columns = list(range(size + 1)))

    def add_data_file(self, partition, path, has_header = False, delim = DEFAULT_FILE_DELIMITER):
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
            skiprows = 0

        data = pandas.read_csv(path, delimiter = delim, header = None, skiprows = skiprows)

        try:
            return self.add_data(partition, data)
        except Exception as ex:
            error = ValueError("File (%s) was not formatted properly for the %s predicate (used delimiter '%s')." % (path, self._name, delim))
            raise error from ex

    def add_data_row(self, partition, args, truth_value = 1.0):
        """
        Add a single record to the predicate.

        Args:
            partition: The partition to add this data into.
            args: The args of the record to add (as a list).
            truth_value: The truth value of the record to add (defaults to 1.0).

        Returns:
            This predicate.
        """

        data = pandas.DataFrame([args + [truth_value]], columns = list(range(size + 1)))
        return self.add_data(partition, data)

    def add_data(self, partition, data):
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
            raise ValueError("Supplied partition is not a pslpython.partition.Partition: %s (%s)." % (partition, type(partition)))

        data = pandas.DataFrame(data, columns = list(range(size + 1)))
        size = len(self._types)

        if (len(data.columns) not in (size, size + 1)):
            raise ValueError("Data was not formatted properly for the %s predicate. Expecting %d or %d columns, got %d." % (self._name, size, size + 1, len(data.columns)))

        if (len(data.columns) == size):
            # Missing the truth value.
            data[size] = DEFAULT_TRUTH_VALUE
            
        self._data[partition] = self._data[partition].append(data, ignore_index = True)

        return self

    def _normalize_name(name):
        return name.upper()
