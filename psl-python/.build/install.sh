#!/bin/bash

# Install via pip to the user's local location.

# The prerequisite package step clears the dist directory, so there should only be one entry.
pip3 install --user --upgrade dist/pslpython-*.whl
