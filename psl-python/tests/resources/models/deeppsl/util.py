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

import json
import os


def write_json(data, path, indent=4):
    os.makedirs(os.path.dirname(path), exist_ok=True)

    with open(path, "w") as file:
        json.dump(data, file, indent=indent)


def load_json(path):
    if not os.path.isfile(path):
        raise FileNotFoundError("File does not exist: %s" % (path,))

    with open(path, "r") as file:
        return json.load(file)


def write_psl_file(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)

    with open(path, 'w') as file:
        for row in data:
            file.write('\t'.join(map(str, row)) + "\n")


def load_psl_file(path):
    if not os.path.isfile(path):
        raise FileNotFoundError("File does not exist: %s" % (path,))

    data = []
    with open(path, 'r') as file:
        for line in file:
            line = line.strip()
            if line == '':
                continue

            data.append(list(map(str, line.split("\t"))))

    return data
