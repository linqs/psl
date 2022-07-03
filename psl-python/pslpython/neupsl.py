"""
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
"""

import tensorflow

'''
A wrapper for models that wish to be used in NeuPSL.
'''
class NeuPSLWrapper(tensorflow.Module):
    def __init__(self, model, inputSize, labelsSize):
        super(NeuPSLWrapper, self).__init__()
        self.model = model

        # model should be compiled.
        assert(self.model.compiled_loss is not None)
        assert(self.model.compiled_metrics is not None)

        self.dataTensorSpec = tensorflow.TensorSpec([None, inputSize], tensorflow.float32, name = 'data')
        self.labelsTensorSpec = tensorflow.TensorSpec([None, labelsSize], tensorflow.float32, name = 'labels')

        # Make `__call__`, `predict`, and `fit` all tensorflow.function.
        # This is done here instead of using a decorator so we can use variable sizes.
        self.__call__ = tensorflow.function(self.__call__, input_signature = [self.dataTensorSpec])
        self.predict = tensorflow.function(self.predict, input_signature = [self.dataTensorSpec])
        self.fit = tensorflow.function(self.fit, input_signature = [self.dataTensorSpec, self.labelsTensorSpec])
        self.evaluate = tensorflow.function(self.evaluate, input_signature = [self.dataTensorSpec, self.labelsTensorSpec])

    def __call__(self, data):
        return self.model(data)

    def predict(self, data):
        return self.model(data)

    # Returns: [loss, metrics, ...]
    def fit(self, data, labels):
        with tensorflow.GradientTape() as tape:
            output = self.model(data, training = True)
            mainLoss = tensorflow.reduce_mean(self.model.compiled_loss(labels, output))
            # self.model.losses contains the reularization loss.
            totalLoss = tensorflow.add_n([mainLoss] + self.model.losses)

        gradients = tape.gradient(totalLoss, self.model.trainable_weights)
        self.model.optimizer.apply_gradients(zip(gradients, self.model.trainable_weights))

        # Compute the metrics scores.

        newOutput = self.model(data)

        self.model.compiled_metrics.reset_state()
        self.model.compiled_metrics.update_state(labels, newOutput)

        results = [totalLoss]
        for metric in self.model.compiled_metrics.metrics:
            results.append(metric.result())

        return tensorflow.stack(results)

    # Returns: [loss, metrics, ...]
    def evaluate(self, data, labels):
        output = self.model(data, training = False)
        mainLoss = tensorflow.reduce_mean(self.model.compiled_loss(labels, output))
        # self.model.losses contains the reularization loss.
        totalLoss = tensorflow.add_n([mainLoss] + self.model.losses)

        # Compute the metrics scores.

        newOutput = self.model(data)

        self.model.compiled_metrics.reset_state()
        self.model.compiled_metrics.update_state(labels, newOutput)

        results = [totalLoss]
        for metric in self.model.compiled_metrics.metrics:
            results.append(metric.result())

        return tensorflow.stack(results)

    def save(self, h5Path = None, tfPath = None):
        if (h5Path is not None):
            self.model.save(h5Path,
                    save_format = 'h5',
                    include_optimizer = True)

        if (tfPath is not None):
            signatures = {
                'call': self.__call__.get_concrete_function(self.dataTensorSpec),
                'predict': self.predict.get_concrete_function(self.dataTensorSpec),
                'fit': self.fit.get_concrete_function(self.dataTensorSpec, self.labelsTensorSpec),
                'evaluate': self.evaluate.get_concrete_function(self.dataTensorSpec, self.labelsTensorSpec),
            }

            tensorflow.saved_model.save(self, tfPath, signatures = signatures)
