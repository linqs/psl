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
        self._model = tree.DecisionTreeClassifier()
        return {}

    def internal_fit(self, data, gradients, options = {}):
        self._model.fit(numpy.array(data[0]), numpy.array(data[1]))
        return {}

    def internal_predict(self, data, options = {}):
        predictions = self._model.predict(data[0])
        return predictions, {}

    def internal_eval(self, data, options = {}):
        predictions, _ = self.internal_predict(data);
        results = {'metrics': calculate_metrics(predictions, data[1], self._metrics)}

        return results

    def internal_save(self, options = {}):
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
Handle importing sklearn and pslpython into the global scope.
Will raise if sklearn is not installed.
'''
def _import():
    global sklearn
    global tree
    global metrics

    sys.argv.append('__workaround__')
    import sklearn
    from sklearn import tree
    from sklearn import metrics
    sys.argv.pop()
