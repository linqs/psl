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
import struct

import numpy

FLOAT_SIZE_BYTES = 4
INT_SIZE_BYTES = 4

DEFAULT_EPOCHS = 1
DEFAULT_BATCH_SIZE = 32
DEFAULT_LEARNING_RATE = 0.001
DEFAULT_ALPHA = 0.0

class DeepModel(abc.ABC):
    def __init__(self):
        self._shared_file = None
        self._shared_buffer = None

        self._num_labels = None
        self._entity_ids = None
        self._features = None

    # Higher-level methods that are passed nicely-formatted data for implementing classes to extend.

    def internal_init_model(self, options = {}):
        """
        Returns a response that will be passed to the Java side.
        """

        return {}

    def internal_fit(self, features, labels,
                     alpha = DEFAULT_ALPHA, gradients = None,
                     batch_size = DEFAULT_BATCH_SIZE, epochs = DEFAULT_EPOCHS, learning_rate = DEFAULT_LEARNING_RATE,
                     options = {}):
        return {}

    def internal_predict(self, options = {}):
        """
        Returns predictions and a response.

        The predictions must be an index match to the features and each element must be |self._num_labels| wide.
        E.g.: [[0, 0, 1], [0, 1, 0], ...].
        """

        raise NotImplementedError("internal_predict")

    def internal_eval(self, options = {}):
        return {}

    def internal_save(self, path, options = {}):
        return {}

    # Low-level methods that take care of moving around data.

    def init_model(self, features_path, shared_memory_path, entity_argument_length, num_labels, options = {}):
        """
        Initialize the underlying model/network.

        The features file will have one row per entity and start with an identifier for the entity.
        The idenfier will be |entity_argument_length| columns long.
        The order that entities appear in the features will be the order that PSL transmits values for entities.
        The index for this ordering will be referred to as "entity index".
        """

        self._shared_file = open(shared_memory_path, 'rb+')
        self._shared_buffer = mmap.mmap(self._shared_file.fileno(), 0)

        self._num_labels = num_labels
        self._features = []
        self._entity_ids = []

        with open(features_path, 'r') as file:
            for row in file:
                parts = row.split("\t")

                entity_id = parts[0:entity_argument_length]
                features = parts[entity_argument_length:len(parts)]

                self._entity_ids.append(entity_id)
                self._features.append([float(value) for value in features])

        self._features = numpy.array(self._features)

        return self.internal_init_model(options = options)

    def fit(self, options = {}):
        epochs = options.get('epochs', DEFAULT_EPOCHS)
        batch_size = options.get('batch_size', DEFAULT_BATCH_SIZE)
        learning_rate = options.get('learning_rate', DEFAULT_LEARNING_RATE)
        alpha = options.get('alpha', DEFAULT_ALPHA)

        self._shared_buffer.seek(0)

        entity_indexes, labels = self._read_entity_values()

        gradients = None
        if (options.get('has_gradients', False)):
            entity_indexes_gradients, gradients = self._read_entity_values()

            if (not numpy.array_equal(entity_indexes, entity_indexes_gradients)):
                raise RuntimeError("Gradients were passed that do not have the exact same layout as the passed labels.")

        used_features = numpy.array([self._features[index] for index in entity_indexes])

        return self.internal_fit(used_features, labels,
                                 alpha = alpha, gradients = gradients,
                                 epochs = epochs, batch_size = batch_size, learning_rate = learning_rate,
                                 options = options)

    def predict(self, options = {}):
        predictions, response = self.internal_predict()

        if (len(predictions) != len(self._features)):
            raise RuntimeError("Mismatch in the number of prefictions. Wanted %d, got %d." % (len(self._features), len(predictions)))

        self._shared_buffer.seek(0)

        self._write_int(len(predictions))

        predictions = numpy.array(predictions, dtype = '>f4', copy = False)
        self._shared_buffer.write(predictions.tobytes(order = 'C'))

        return response

    def eval(self, options = {}):
        return self.internal_eval()

    def save(self, path, options = {}):
        return self.internal_save(path)

    # Helper methods.

    def close(self):
        if (self._shared_buffer is not None):
            self._shared_buffer.close()
            self._shared_buffer = None

        if (self._shared_file is not None):
            self._shared_file.close()
            self._shared_file = None

        self._num_labels = None
        self._entity_ids = None
        self._features = None

    # Read entity values (typically labels or gradients, always floats) off the buffer.
    # On the buffer, entity values are laid out in the format:
    # <count><entity index, ...><value, ...>
    # Returns: numpy.ndarray([entity index, ...]), numpy.ndarray([[entity1_value1, ...], [entity2_value1, ...], ...])
    def _read_entity_values(self):
        count = self._read_int()

        indexes_buffer = self._shared_buffer.read(count * INT_SIZE_BYTES)
        values_buffer = self._shared_buffer.read(count * self._num_labels * FLOAT_SIZE_BYTES)

        indexes = numpy.frombuffer(indexes_buffer, dtype = '>i4', count = count)
        values = numpy.frombuffer(values_buffer, dtype = '>f4', count = (count * self._num_labels))

        values = values.reshape((count, self._num_labels))

        return indexes, values

    def _read_int(self):
        return struct.unpack('>i', self._shared_buffer.read(INT_SIZE_BYTES))[0]

    def _write_int(self, value):
        self._shared_buffer.write(struct.pack('>i', value))
