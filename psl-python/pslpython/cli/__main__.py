"""
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2021 The Regents of the University of California

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

import shutil
import sys

from pslpython import model
from pslpython import util

def main(argv):
    command = [
        shutil.which('java'),
        '-jar',
        model.Model.CLI_JAR_PATH,
    ]
    command += argv

    exit_status = util.execute(command)
    if (exit_status != 0):
        sys.exit(1)

    sys.exit(0)

if (__name__ == '__main__'):
    main(sys.argv[1:])
