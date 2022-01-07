'''
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
'''

'''
Utils for working with the generated neural data.
'''

import os

MODEL_DIRNAME = 'model'
DATA_DIRNAME = 'data'

FEATURES_FILENAME = 'features.txt'
LABELS_FILENAME = 'labels.txt'
OBSERVATIONS_FILENAME = 'observations.txt'
TARGETS_FILENAME = 'targets.txt'
TRUTH_FILENAME = 'truth.txt'

def save(save_dir, wrapper, train_features, train_labels, test_features, test_labels):
    model_path = os.path.join(save_dir, MODEL_DIRNAME)
    data_path = os.path.join(save_dir, DATA_DIRNAME)

    if (os.path.exists(save_dir)):
        raise FileExistsError("Path already exists: " + save_dir)

    os.makedirs(model_path)
    os.makedirs(data_path)

    wrapper.save(tfPath = model_path)

    features = train_features + test_features
    features = [[i] + features[i] for i in range(len(features))]
    write_file(features, os.path.join(data_path, FEATURES_FILENAME))

    labels = [[0], [1]]
    write_file(labels, os.path.join(data_path, LABELS_FILENAME))

    # Note that the labels are binary one-hot encoded.
    # So we can just take the last position and that represents the correct label:
    # [1, 0] = 0, [0, 1] = 1.
    observations = [[i, train_labels[i][1]] for i in range(len(train_labels))]
    write_file(observations, os.path.join(data_path, OBSERVATIONS_FILENAME))

    targets = [[i + len(train_labels)] for i in range(len(test_labels))]
    write_file(targets, os.path.join(data_path, TARGETS_FILENAME))

    truth = [[i + len(train_labels), test_labels[i][1]] for i in range(len(test_labels))]
    write_file(truth, os.path.join(data_path, TRUTH_FILENAME))

def write_file(rows, path):
    with open(path, 'w') as file:
        for row in rows:
            file.write("\t".join(map(str, row)) + "\n")
