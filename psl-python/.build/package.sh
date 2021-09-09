#!/bin/bash

# Create a Python binary wheel.
# This will clear out any previous builds.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly PSLPYTHON_DIR="${THIS_DIR}/.."

function main() {
    if [[ $# -ne 0 ]]; then
        echo "USAGE: $0"
        exit 1
    fi

    set -e
    trap exit SIGINT

    cd "${PSLPYTHON_DIR}"

    rm -Rf dist
    python3 setup.py bdist_wheel
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
