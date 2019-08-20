#!/bin/bash

# Create a Python binary wheel.
# This will clear out any previous builds.

rm -Rf dist
python3 setup.py bdist_wheel
