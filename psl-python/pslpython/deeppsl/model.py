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

        self._value_count = None
        self._data = None

    """
    Higher-level methods that are passed nicely-formatted data for implementing classes to extend.
    """

    def internal_init_model(self, application, options = {}):
        raise NotImplementedError("internal_init_model")

    def internal_fit(self, data, gradients, options = {}):
        raise NotImplementedError("internal_fit")

    def internal_epoch_end(self, options = {}):
        raise NotImplementedError("internal_epoch")

    def internal_predict(self, data, options = {}):
        raise NotImplementedError("internal_predict")

    def internal_eval(self, data, options = {}):
        raise NotImplementedError("internal_eval")

    def internal_save(self, options = {}):
        raise NotImplementedError("internal_save")

    """
    Low-level methods that take care of moving around data.
    """

    def init_weight(self, shared_memory_path, application, options = {}):
        raise NotImplementedError("init_weight")

    def fit_weight(self, options = {}):
        raise NotImplementedError("fit_weight")

    def predict_weight(self, options = {}):
        raise NotImplementedError("predict_weight")

    def predict_weight_learn(self, options = {}):
        raise NotImplementedError("predict_weight")

    def eval_weight(self, options = {}):
        raise NotImplementedError("eval_weight")

    def init_predicate(self, shared_memory_path, application, options = {}):
        self._shared_file = open(shared_memory_path, 'rb+')
        self._shared_buffer = mmap.mmap(self._shared_file.fileno(), 0)

        self._value_count = int(options['class-size'])
        self._data = []

        entity_argument_length = len(options['entity-argument-indexes'].split(","))

        with open(os.path.join(options['relative-dir'], options['entity-data-map-path']), 'r') as file:
            for row in file:
                parts = row.split("\t")

                data = parts[entity_argument_length:]
                self._data.append([float(value) for value in data])

        self._data = numpy.array(self._data)

        return self.internal_init_model(application, options = options)

    def fit_predicate(self, options = {}):
        self._shared_buffer.seek(0)

        count = self._read_int()
        entity_indexes = self._read_values('>i4', count)
        gradients = self._read_values('>f4', count * self._value_count).reshape((count, self._value_count))

        data = numpy.array([self._data[index] for index in entity_indexes])

        return self.internal_fit(data, gradients, options = options)

    def epoch_end(self, options = {}):
        return self.internal_epoch_end(options = options)

    def predict_predicate(self, options = {}):
        self._predict_predicate(False, options = options)

    def predict_predicate_learn(self, options = {}):
        self._predict_predicate(True, options = options)

    def _predict_predicate(self, learn, options = {}):
        options['learn'] = learn

        self._shared_buffer.seek(0)

        count = self._read_int()
        entity_indexes = self._read_values('>i4', count)

        data = numpy.array([self._data[index] for index in entity_indexes])

        predictions, response = self.internal_predict(data, options=options)

        self._shared_buffer.seek(0)

        self._write_int(int(options['class-size']) * len(predictions))
        predictions = numpy.array(predictions, dtype = '>f4', copy = False)
        self._shared_buffer.write(predictions.tobytes(order = 'C'))

        return response

    def eval_predicate(self, options = {}):
        self._shared_buffer.seek(0)

        count = self._read_int()
        entity_indexes = self._read_values('>i4', count)

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

        self._value_count = None
        self._data = None

    def _read_values(self, value_type, count, byte_size = INT_SIZE_BYTES):
        values_buffer = self._shared_buffer.read(count * byte_size)
        values_buffer = numpy.frombuffer(values_buffer, dtype = value_type, count = count)

        return values_buffer

    def _read_int(self):
        return struct.unpack('>i', self._shared_buffer.read(INT_SIZE_BYTES))[0]

    def _write_int(self, value):
        self._shared_buffer.write(struct.pack('>i', value))