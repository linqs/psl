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

class Predicate(object):
    """
    A PSL predicate.

    This class controls not just the semantic meaning of a predicate,
    but also the data for this predciate.
    """

    DEFAULT_FILE_DELIMITER = "\t"

    # Predicates must have unique names.
    _used_names = set()

    def __init__(self, name, size = None, arg_types = None):
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

        pass

    def add_data_file(self, partition, path, delim = DEFAULT_FILE_DELIMITER):
        """
        Add an entire file to the predicate.

        Args:
            partition: The partition to add this data into.
            path: The path to the file to load.
            delim: The field separator used in the file to be loaded.

        Returns:
            This predicate.
        """

        pass

    def add_data(self, partition, args, truth_value = 1.0):
        """
        Add a single record to the predicate.

        Args:
            partition: The partition to add this data into.
            args: The args of the record to add (as a list).
            truth_value: The truth value of the record to add (defaults to 1.0).

        Returns:
            This predicate.
        """

        pass

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

        pass
