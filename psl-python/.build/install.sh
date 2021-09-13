#!/bin/bash

# Install via pip to the user's local location.
# The prerequisite package step clears the dist directory, so there should only be one entry.

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

    pip3 install --user --upgrade dist/pslpython-*.whl
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
