#!/usr/bin/env python3

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

"""
A TCP server that acts as a bridge between PSL (Java) and deep learning models (Python).
"""

# TODO(eriq): More error checking.
# TODO(eriq): Message validation.

import atexit
import importlib
import json
import os
import socket
import sys
import traceback

ENCODING = 'utf-8'
MAX_MESSAGE_SIZE_BYTES = 2 ** 14

class ConnectionHandler(object):
    def __init__(self):
        self._model = None

    def handle_request(self, connection, data):
        try:
            response, keep_open = self.handle_internal(connection, data)
        except Exception as ex:
            keep_open = False
            traceback.print_exc()

            response = {
                'status': 'failed',
                'message': "Server encountered an error: '%s'" % (ex,),
            }

        connection.sendall((json.dumps(response) + "\n").encode(ENCODING))
        return keep_open

    def handle_internal(self, connection, data):
        payload = data.decode(ENCODING)

        try:
            request = json.loads(payload)
        except Exception as ex:
            raise ValueError("Payload is not valid json.", ex)

        keep_open = True

        if request['task'] == 'init':
            result = self._init_model(request)
        elif request['task'] == 'fit':
            result = self._fit(request)
        elif request['task'] == 'predict':
            result = self._predict(request)
        elif request['task'] == 'eval':
            result = self._eval(request)
        elif request['task'] == 'save':
            result = self._save(request)
        elif request['task'] == 'close':
            result = self._close()
            keep_open = False
        else:
            raise ValueError("Unknown task: '%s'." % (request['task']))

        response = {
            'status': 'success',
            'task': request['task'],
            'result': result,
        }

        return response, keep_open

    def _init_model(self, request):
        shared_memory_path = request['shared_memory_path']
        options = request.get('options', {})

        self._model = self._load_model(os.path.join(options['relative_dir'], options['model-path']))
        return self._model.init_model(shared_memory_path, options=options)

    def _fit(self, request):
        options = request.get('options', {})
        return self._model.fit(options=options)

    def _predict(self, request):
        options = request.get('options', {})
        return self._model.predict(options=options)

    def _eval(self, request):
        options = request.get('options', {})
        return self._model.eval(options=options)

    def _save(self, request):
        options = request.get('options', {})
        return self._model.save(options=options)

    def _close(self):
        if self._model is not None:
            self._model.close()
        return True

    def _load_model(self, model_info):
        model_parts = model_info.split('::')
        if len(model_parts) > 2:
            raise ValueError("Bad format for model definition. Got: '%s'. Should be: '<qualified class name>' or '<path>::<class name>'." % (model_info))

        if len(model_parts) == 1:
            # Info is a qualified class name.
            parts = model_parts[0].split('.')
            if len(parts) <= 1:
                raise ValueError("Class definition not qualified: '%s'." % (model_parts[0]))

            module_name = '.'.join(parts[0:-1])
            class_name = parts[-1]

            module = importlib.import_module(module_name)
            model_class = getattr(module, class_name)
        else:
            # Import as a path.
            model_source = model_parts[0]
            class_name = model_parts[1]

            if (not model_source.endswith('.py')):
                raise ValueError("Module source files does not look like Python (ends with .py): '%s'." % (model_source))

            spec = importlib.util.spec_from_file_location('model_source', model_source)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)

            model_class = getattr(module, class_name)

        return model_class()

def main(port):
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    atexit.register(_close, sock)
    sock.bind(('127.0.0.1', port))

    sock.listen(1)
    connection, _ = sock.accept()
    atexit.register(_close, connection)

    handler = ConnectionHandler()

    while True:
        data = connection.recv(MAX_MESSAGE_SIZE_BYTES)
        if not data:
            break

        keep_open = handler.handle_request(connection, data)
        if not keep_open:
            connection.shutdown(socket.SHUT_RDWR)
            sock.shutdown(socket.SHUT_RDWR)
            break

    connection.close()
    sock.close()

def _close(resource):
    resource.close()

def _load_args(args):
    exe = args.pop(0)
    if len(args) != 1 or ({'h', 'help'} & {arg.lower().strip().replace('-', '') for arg in args}):
        print("USAGE: python3 %s <port>" % (exe), file = sys.stderr)
        sys.exit(1)

    return int(args.pop(0))

if __name__ == '__main__':
    main(_load_args(list(sys.argv)))
