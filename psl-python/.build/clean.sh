#!/bin/bash

# Clean the package of the artifacts from any build step.

rm -Rf build dist pslpython.egg-info
find . -name __pycache__ | xargs rm -Rf
