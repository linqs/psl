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

    def save(self, h5Path, tfPath):
        self.model.save(h5Path,
                save_format = 'h5',
                include_optimizer = True)

        signatures = {
            'call': self.__call__.get_concrete_function(self.dataTensorSpec),
            'predict': self.predict.get_concrete_function(self.dataTensorSpec),
            'fit': self.fit.get_concrete_function(self.dataTensorSpec, self.labelsTensorSpec),
        }

        tensorflow.saved_model.save(self, tfPath, signatures = signatures)
