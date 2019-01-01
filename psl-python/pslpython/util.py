"""
This file is part of the PSL software.
Copyright 2011-2015 University of Maryland
Copyright 2013-2019 The Regents of the University of California

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

import asyncio
import shlex

# Heavily inspiried by https://kevinmccarthy.org/2016/07/25/streaming-subprocess-stdin-and-stdout-with-asyncio-in-python/

async def _read_stream(stream, callback):
    while True:
        line = await stream.readline()
        if not line:
            break

        callback(line.decode("utf-8").rstrip('\r\n'))

async def _stream_subprocess(command_args, stdout_callback, stderr_callback):
    process = await asyncio.create_subprocess_exec(*command_args,
            stdout = asyncio.subprocess.PIPE, stderr = asyncio.subprocess.PIPE)

    await asyncio.wait([
        _read_stream(process.stdout, stdout_callback),
        _read_stream(process.stderr, stderr_callback)
    ])

    return await process.wait()

def execute(command_args, stdout_callback, stderr_callback):
    """
    Exec an external process specified by the args (list).
    """

    # Using asyncio.run() would be better, but it is new in 3.7.
    # https://docs.python.org/3/library/asyncio-task.html#asyncio.run

    loop = asyncio.get_event_loop()
    result = loop.run_until_complete(
        _stream_subprocess(
            command_args,
            stdout_callback,
            stderr_callback,
        )
    )

    return result

def shell_join(command_args):
    """
    Get a shell command that is properly escaped.
    """

    return ' '.join([shlex.quote(str(arg)) for arg in command_args])
