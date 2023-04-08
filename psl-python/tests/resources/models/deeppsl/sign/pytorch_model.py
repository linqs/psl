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
        self._loss = None
        self._optimizer = None

    def internal_init_model(self, application, options = {}):
        class SignPytorchNetwork(torch.nn.Module):
            def __init__(self, input_size, output_size):
                _import()
                super(SignPytorchNetwork, self).__init__()
                self.layer = torch.nn.Linear(input_size, output_size)
                self.output_size = output_size

            def forward(self, x):
                x = self.layer(x)
                x = torch.nn.functional.softmax(x, dim=1)
                return x

        self._model = SignPytorchNetwork(options['input_shape'], options['output_shape'])

        self._loss = torch.nn.CrossEntropyLoss()
        self._optimizer = torch.optim.Adam(self._model.parameters(), lr=options['learning_rate'])

        return {}

    def internal_fit(self, data, gradients, options = {}, verbose=0):
        features = torch.FloatTensor(data[0])
        labels = torch.FloatTensor(data[1])

        for epoch in range(options['epochs']):
            y_pred = self._model(features)
            loss = self._loss(y_pred, labels)

            self._model.zero_grad()
            loss.backward()

            self._optimizer.step()
        return {}

    def internal_predict(self, data, options = {}, verbose=0):
        features = torch.FloatTensor(data[0])
        predictions = self._model(features)
        return predictions, {}

    def internal_eval(self, data, options = {}):
        predictions, _ = self.internal_predict(data, options=options)
        results = {'loss': self._loss(self._model(torch.FloatTensor(data[0])), torch.FloatTensor(data[1])).item(),
                   'metrics': calculate_metrics(predictions.detach().numpy(), data[1], self._metrics)}

        return results

    def internal_save(self, options = {}):
        torch.save(self._model.state_dict(), options['save_path'])
        return {}

    def load(self, options = {}):
        self.internal_init_model(None, options=options)
        self._model.load_state_dict(torch.load(options['load_path']))
        return {}


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
Handle importing pytorch and pslpython into the global scope.
Will raise if pytorch is not installed.
'''
def _import():
    global torch

    sys.argv.append('__workaround__')
    import torch
    sys.argv.pop()