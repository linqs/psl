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

import sys

import pslpython.deeppsl.model

class SignModel(pslpython.deeppsl.model.DeepModel):
    def __init__(self):
        super().__init__()

        _import()

        self._model = None

    def internal_init_model(self, options = {}):
        layers = [
            tensorflow.keras.layers.Input(options['input_shape']),
            tensorflow.keras.layers.Dense(options['output_shape'], activation = 'softmax'),
        ]

        model = tensorflow.keras.Sequential(layers)

        model.compile(
            optimizer = tensorflow.keras.optimizers.Adam(learning_rate = options['learning_rate']),
            loss = tensorflow.keras.losses.CategoricalCrossentropy(from_logits = False),
            metrics = ['categorical_accuracy']
        )

        self._model = model
        return {}

    def internal_fit(self, features, labels, alpha = 0.0, gradients = None,
                     batch_size = 32, epochs = 10, learning_rate = 0.001,
                     options = {}):
        self._model.fit(features, labels, epochs = epochs)
        return {}

    def internal_predict(self, options = {}):
        predictions = self._model.predict(self._features)
        return predictions, {}

    def internal_eval(self, features, labels, options = {}):
        return self._model.evaluate(features, labels, return_dict = True)

    def internal_save(self, options = {}):
        self._model.save(options['save_path'], save_format = 'tf')
        return {}

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
