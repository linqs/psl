#!/bin/bash

# Clean the package of the artifacts from any build step.

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

    rm -Rf build dist pslpython.egg-info
    find . -name __pycache__ | xargs rm -Rf
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
