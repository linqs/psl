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

import shlex
import subprocess

def execute(command, log_callback = lambda line: print(line)):
    if (isinstance(command, str)):
        command = shlex.split(command)

    with subprocess.Popen(command, stdout = subprocess.PIPE, stderr = subprocess.STDOUT, universal_newlines = True) as proc:
        if (log_callback is not None):
            for line in proc.stdout:
                log_callback(line.rstrip())

        proc.wait()
        return proc.returncode

def shell_join(command_args):
    """
    Get a shell command that is properly escaped.
    """

    return ' '.join([shlex.quote(str(arg)) for arg in command_args])
