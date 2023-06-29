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

'''
This file handles data management for the sign example.

When getting data, first attempts to retrieve existing data. If no data is found, then
generate and save new data.
'''

import os
import random

import tests.resources.models.deeppsl.util

ENTITY_DATA_MAP_FILENAME = 'entity-data-map.txt'

TARGETS_TRAIN_FILENAME = 'targets_train.txt'
TARGETS_TEST_FILENAME = 'targets_test.txt'
TRUTH_TRAIN_FILENAME = 'truth_train.txt'
TRUTH_TEST_FILENAME = 'truth_test.txt'

CONFIG_FILENAME = 'config.json'

TRAIN_SIZE = 100
TEST_SIZE = 100
FEATURE_RANGE = 2 ** 10
FEATURE_SIZE = 5
CLASS_SIZE = 2

SEED = 4


def get_psl_data(out_dir):
    if not os.path.isfile(os.path.join(out_dir, CONFIG_FILENAME)):
        x_train, y_train, x_test, y_test = _generate_data()
        _write_data(out_dir, x_train, y_train, x_test, y_test)

    return _load_data(out_dir)


def get_deep_data(out_dir):
    entity_data_map, x_train, y_train, x_test, y_test = get_psl_data(out_dir)

    entity_data_dict = {example[0]: example[0:] for example in entity_data_map}
    deep_x_train, deep_y_train = _convert_to_deep_data(entity_data_dict, x_train)
    deep_x_test, deep_y_test = _convert_to_deep_data(entity_data_dict, x_test)

    return deep_x_train, deep_y_train, deep_x_test, deep_y_test


def _convert_to_deep_data(entity_data_dict, data):
    features = []
    labels = []
    for example in data:
        features.append([int(feature) for feature in entity_data_dict[example[0]][1:-1]])
        labels.append([1,0] if int(entity_data_dict[example[0]][-1:][0]) == 0 else [0,1])
    return features, labels


def _generate_data():
    '''
    A simple sign dataset for classifying positive and negative entities. A label
    of [1, 0] means majority values will be positive, [0, 1] is majority negative.
    '''
    random.seed(SEED)

    x_train = []
    y_train = []

    x_test = []
    y_test = []

    for features, labels, size in ((x_train, y_train, TRAIN_SIZE), (x_test, y_test, TEST_SIZE)):
        # Make sure the number of features is odd for easier generation.
        if FEATURE_SIZE % 2 != 1:
            raise ValueError('Feature size must be odd.')

        for index in range(size):
            # Flip the sign of a minority of the features.
            point = [random.randint(0, FEATURE_RANGE) for _ in range(FEATURE_SIZE)]
            flip_sign_indexes = random.sample(list(range(len(point))), random.randint(0, FEATURE_SIZE // 2))
            point = [-1 * point[index] if index in flip_sign_indexes else point[index] for index in range(len(point))]

            label = [1, 0]

            if random.random() < 0.5:
                point = list(map(lambda x: -x, point))
                label = [0, 1]

            features.append(point)
            labels.append(label)

    return x_train, y_train, x_test, y_test


def _write_data(out_dir, x_train, y_train, x_test, y_test):
    os.makedirs(out_dir, exist_ok=True)

    targets_train = []
    truth_train = []
    targets_test = []
    truth_test = []

    x_data = x_train + x_test
    y_data = y_train + y_test

    entity_data_map = [[index] + x_data[index] + [y_data[index][1]] for index in range(len(x_data))]
    tests.resources.models.deeppsl.util.write_psl_file(os.path.join(out_dir, ENTITY_DATA_MAP_FILENAME), entity_data_map)


    for entity_index in range(len(x_train)):
        for class_index in range(CLASS_SIZE):
            targets_train.append([entity_index, class_index])
            truth_train.append([entity_index, class_index, y_train[entity_index][class_index]])

    for entity_index in range(len(x_test)):
        for class_index in range(CLASS_SIZE):
            targets_test.append([entity_index + len(x_train), class_index])
            truth_test.append([entity_index + len(y_train), class_index, y_test[entity_index][class_index]])

    tests.resources.models.deeppsl.util.write_psl_file(os.path.join(out_dir, TARGETS_TRAIN_FILENAME), targets_train)
    tests.resources.models.deeppsl.util.write_psl_file(os.path.join(out_dir, TRUTH_TRAIN_FILENAME), truth_train)
    tests.resources.models.deeppsl.util.write_psl_file(os.path.join(out_dir, TARGETS_TEST_FILENAME), targets_test)
    tests.resources.models.deeppsl.util.write_psl_file(os.path.join(out_dir, TRUTH_TEST_FILENAME), truth_test)

    config = {
        'seed': SEED,
        'train_size': TRAIN_SIZE,
        'test_size': TEST_SIZE,
        'feature_range': FEATURE_RANGE,
        'feature_size': FEATURE_SIZE,
        'class_size': CLASS_SIZE,
    }
    tests.resources.models.deeppsl.util.write_json(config, os.path.join(out_dir, CONFIG_FILENAME))


def _load_data(load_path):
    entity_data_map = tests.resources.models.deeppsl.util.load_psl_file(os.path.join(load_path, ENTITY_DATA_MAP_FILENAME))

    x_train = tests.resources.models.deeppsl.util.load_psl_file(os.path.join(load_path, TARGETS_TRAIN_FILENAME))
    y_train = tests.resources.models.deeppsl.util.load_psl_file(os.path.join(load_path, TRUTH_TRAIN_FILENAME))

    x_test = tests.resources.models.deeppsl.util.load_psl_file(os.path.join(load_path, TARGETS_TEST_FILENAME))
    y_test = tests.resources.models.deeppsl.util.load_psl_file(os.path.join(load_path, TRUTH_TEST_FILENAME))

    return entity_data_map, x_train, y_train, x_test, y_test
