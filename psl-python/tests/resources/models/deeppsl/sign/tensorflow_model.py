#!/usr/bin/env python3
'''
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
'''

import numpy
import sys

import pslpython.deeppsl.model


class SignModel(pslpython.deeppsl.model.DeepModel):
    def __init__(self):
        super().__init__()

        _import()

        self._model = None
        self._metrics = ['categorical_accuracy']

    def internal_init_model(self, application, options = {}):
        layers = [
            tensorflow.keras.layers.Input(int(options['input_shape'])),
            tensorflow.keras.layers.Dense(int(options['output_shape']), activation = 'softmax'),
        ]

        model = tensorflow.keras.Sequential(layers)

        model.compile(
            optimizer = tensorflow.keras.optimizers.Adam(learning_rate = float(options['learning_rate'])),
            loss = tensorflow.keras.losses.CategoricalCrossentropy(from_logits = False),
            metrics = self._metrics
        )

        self._model = model
        return {}

    def internal_fit(self, data, gradients, options = {}):
        data = self._prepare_data(data)
        self._model.fit(data[0], data[1], epochs = int(options['epochs']), verbose=0)
        return {}

    def internal_predict(self, data, options = {}):
        data = self._prepare_data(data)
        predictions = self._model.predict(data[0], verbose=0)
        return predictions, {}

    def internal_eval(self, data, options = {}):
        data = self._prepare_data(data)
        predictions, _ = self.internal_predict(data, options=options)
        results = {'loss': self._model.compiled_loss(tensorflow.constant(predictions, dtype=tensorflow.float32), tensorflow.constant(data[1], dtype=tensorflow.float32)),
                   'metrics': calculate_metrics(predictions, data[1], self._metrics)}

        return results

    def internal_save(self, options = {}):
        if 'save_path' not in options:
            return {}

        self._model.save(options['save_path'], save_format = 'tf')
        return {}

    def load(self, options = {}):
        self._model = tensorflow.keras.models.load_model(options['load_path'])
        return {}

    def _prepare_data(self, data):
        if len(data) == 2:
            return data

        return [numpy.asarray(data[:,:-1]), numpy.asarray([[1, 0] if label == 0 else [0, 1] for label in data[:,-1]])]


def calculate_metrics(y_pred, y_truth, metrics):
    results = {}
    for metric in metrics:
        if metric == 'categorical_accuracy':
            results['categorical_accuracy'] = _categorical_accuracy(y_pred, y_truth)
        else:
            raise ValueError('Unknown metric: {}'.format(metric))
    return results


def _categorical_accuracy(y_pred, y_truth):
    correct = 0
    for i in range(len(y_truth)):
        if numpy.argmax(y_pred[i]) == numpy.argmax(y_truth[i]):
            correct += 1
    return correct / len(y_truth)


'''
Handle importing tensorflow and pslpython into the global scope.
Will raise if tensorflow is not installed.
'''
def _import():
    global tensorflow

    # Tensoflow has a bug when sys.argv is empty on import: https://github.com/tensorflow/tensorflow/issues/45994
    sys.argv.append('__workaround__')
    import tensorflow
    sys.argv.pop()
