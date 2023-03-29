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

import importlib
import os
import random
import sys

THIS_DIR = os.path.join(os.path.dirname(os.path.realpath(__file__)))
sys.path.append(os.path.join(THIS_DIR, '..'))

util = importlib.import_module("util")

ENTITY_DATA_MAP_FILENAME = 'entity-data-map.txt'

TARGETS_TRAIN_FILENAME = 'targets_train.txt'
TARGETS_TEST_FILENAME = 'targets_test.txt'
TRUTH_TRAIN_FILENAME = 'truth_train.txt'
TRUTH_TEST_FILENAME = 'truth_test.txt'

CONFIG_FILENAME = 'config.json'

TRAIN_SIZE = 100
TEST_SIZE = 100
FEATURE_RANGE = 2 ** 10
CLASS_SIZE = 2

SEED = 4


def get_psl_data(out_dir):
    if not os.path.isfile(os.path.join(out_dir, CONFIG_FILENAME)):
        x_train, y_train, x_test, y_test = _generate_data()
        _write_data(out_dir, x_train, y_train, x_test, y_test)

    return _load_data(out_dir)


def get_neural_data(out_dir):
    entity_data_map, x_train, y_train, x_test, y_test = get_psl_data(out_dir)

    entity_data_dict = {example[0]: example[0:] for example in entity_data_map}

    neural_x_train = [[int(feature) for feature in entity_data_dict[example[0]][:-1]] for example in x_train]
    neural_y_train = [int(entity_data_dict[example[0]][-1:][0]) for example in x_train]
    neural_x_test = [[int(feature) for feature in entity_data_dict[example[0]][:-1]] for example in x_test]
    neural_y_test = [int(entity_data_dict[example[0]][-1:][0]) for example in x_test]

    return neural_x_train, neural_y_train, neural_x_test, neural_y_test


def _generate_data():
    '''
    A simple sign dataset for classifying positive and negative entities. A label
    of [1, 0] means all values will be positive, [0, 1] is all negative.
    '''
    random.seed(SEED)

    x_train = []
    y_train = []

    x_test = []
    y_test = []

    for features, labels, size in ((x_train, y_train, TRAIN_SIZE), (x_test, y_test, TEST_SIZE)):
        for index in range(size):
            point = [random.randint(0, FEATURE_RANGE) for _ in range(CLASS_SIZE)]
            label = [1, 0]

            if random.random() < 0.5:
                point = list(map(lambda x: -x, point))
                label = [0, 1]

            features.append(point)
            labels.append(label)

    return x_train, y_train, x_test, y_test


def _write_data(out_dir, x_train, y_train, x_test, y_test):
    os.makedirs(out_dir, exist_ok=True)

    x_data = x_train + x_test
    y_data = y_train + y_test

    entity_data_map = [[index] + x_data[index] + [y_data[index][1]] for index in range(len(x_data))]
    util.write_psl_file(os.path.join(out_dir, ENTITY_DATA_MAP_FILENAME), entity_data_map)

    targets_train = [[index] for index in range(len(x_train))]
    util.write_psl_file(os.path.join(out_dir, TARGETS_TRAIN_FILENAME), targets_train)

    truth_train = [[index, y_train[index][1]] for index in range(len(y_train))]
    util.write_psl_file(os.path.join(out_dir, TRUTH_TRAIN_FILENAME), truth_train)

    targets_test = [[index + len(x_train)] for index in range(len(x_test))]
    util.write_psl_file(os.path.join(out_dir, TARGETS_TEST_FILENAME), targets_test)

    truth_test = [[index + len(y_train), y_test[index][1]] for index in range(len(y_test))]
    util.write_psl_file(os.path.join(out_dir, TRUTH_TEST_FILENAME), truth_test)

    config = {
        'seed': SEED,
        'train_size': TRAIN_SIZE,
        'test_size': TEST_SIZE,
        'feature_range': FEATURE_RANGE,
        'class_size': CLASS_SIZE,
    }
    util.write_json(config, os.path.join(out_dir, CONFIG_FILENAME))


def _load_data(load_path):
    entity_data_map = util.load_psl_file(os.path.join(load_path, ENTITY_DATA_MAP_FILENAME))

    x_train = util.load_psl_file(os.path.join(load_path, TARGETS_TRAIN_FILENAME))
    y_train = util.load_psl_file(os.path.join(load_path, TRUTH_TRAIN_FILENAME))

    x_test = util.load_psl_file(os.path.join(load_path, TARGETS_TEST_FILENAME))
    y_test = util.load_psl_file(os.path.join(load_path, TRUTH_TEST_FILENAME))

    return entity_data_map, x_train, y_train, x_test, y_test
