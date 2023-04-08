"""
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2021 The Regents of the University of California

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

import abc
import mmap
import os
import struct

import numpy

FLOAT_SIZE_BYTES = 4
INT_SIZE_BYTES = 4

class DeepModel(abc.ABC):
    def __init__(self):
        self._shared_file = None
        self._shared_buffer = None

        self._class_size = None

        self._data = None
        self._entity_ids = None

    """
    Higher-level methods that are passed nicely-formatted data for implementing classes to extend.
    """

    def internal_init_model(self, options = {}):
        raise NotImplementedError("internal_init_model")

    def internal_fit(self, data, gradients, options = {}):
        raise NotImplementedError("internal_fit")

    def internal_predict(self, data, options = {}):
        raise NotImplementedError("internal_predict")

    def internal_eval(self, data, options = {}):
        raise NotImplementedError("internal_eval")

    def internal_save(self, options = {}):
        raise NotImplementedError("internal_save")

    """
    Low-level methods that take care of moving around data.
    """

    def init_model(self, shared_memory_path, options = {}):
        """
        Initialize the underlying model/network.

        The data file must have one row per entity and start with an identifier for the entity.
        The identifier will be |entity-argument-indexes| columns long.
        """

        self._shared_file = open(shared_memory_path, 'rb+')
        self._shared_buffer = mmap.mmap(self._shared_file.fileno(), 0)

        self._class_size = int(options['class-size'])
        self._data = []
        self._entity_ids = []

        entity_argument_length = len(options['entity-argument-indexes'].split(","))

        with open(os.path.join(options['relative-dir'], options['entity-data-map-path']), 'r') as file:
            for row in file:
                parts = row.split("\t")

                entity_id = parts[0:entity_argument_length]
                data = parts[entity_argument_length:len(parts)]

                self._entity_ids.append(entity_id)
                self._data.append([float(value) for value in data])

        self._data = numpy.array(self._data)

        return self.internal_init_model(options = options)

    def fit(self, options = {}):
        self._shared_buffer.seek(0)

        entity_indexes, gradients = self._read_entity_values()
        data = numpy.array([self._data[index] for index in entity_indexes])

        return self.internal_fit(data, gradients = gradients, options = options)

    def predict(self, options = {}):
        self._shared_buffer.seek(0)

        entity_indexes = self._read_indexes()
        data = numpy.array([self._data[index] for index in entity_indexes])

        predictions, response = self.internal_predict(data, options=options)

        self._shared_buffer.seek(0)

        self._write_int(int(options['class-size']) * len(predictions))
        predictions = numpy.array(predictions, dtype = '>f4', copy = False)
        self._shared_buffer.write(predictions.tobytes(order = 'C'))

        return response

    def eval(self, options = {}):
        self._shared_buffer.seek(0)

        entity_indexes = self._read_indexes()
        data = numpy.array([self._data[index] for index in entity_indexes])

        return self.internal_eval(data, options=options)

    def save(self, options = {}):
        return self.internal_save(options=options)

    """
    Helper methods.
    """

    def close(self):
        if self._shared_buffer is not None:
            self._shared_buffer.close()
            self._shared_buffer = None

        if self._shared_file is not None:
            self._shared_file.close()
            self._shared_file = None

        self._class_size = None
        self._data = None
        self._entity_ids = None

    """
    Read entity values (typically gradients, always floats) off the buffer.
    On the buffer, entity values are laid out in the format:
    <count><entity index, ...><value, ...>
    Returns: numpy.ndarray([entity index, ...]), numpy.ndarray([[entity1_value1, ...], [entity2_value1, ...], ...])
    """
    def _read_entity_values(self):
        count = self._read_int()

        indexes_buffer = self._shared_buffer.read(count * INT_SIZE_BYTES)
        values_buffer = self._shared_buffer.read(count * self._class_size * FLOAT_SIZE_BYTES)

        indexes = numpy.frombuffer(indexes_buffer, dtype = '>i4', count = count)
        values = numpy.frombuffer(values_buffer, dtype = '>f4', count = (count * self._class_size))

        values = values.reshape((count, self._class_size))

        return indexes, values

    def _read_indexes(self):
        count = self._read_int()

        indexes_buffer = self._shared_buffer.read(count * INT_SIZE_BYTES)
        indexes = numpy.frombuffer(indexes_buffer, dtype = '>i4', count = count)

        return indexes

    def _read_int(self):
        return struct.unpack('>i', self._shared_buffer.read(INT_SIZE_BYTES))[0]

    def _write_int(self, value):
        self._shared_buffer.write(struct.pack('>i', value))
