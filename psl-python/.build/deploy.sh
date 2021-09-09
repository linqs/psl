#!/bin/bash

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly PSLPYTHON_DIR="${THIS_DIR}/.."

function main() {
    if [[ $# -ne 1 ]]; then
        echo "USAGE: ${0} [test | release]"
        exit 1
    fi

    set -e
    trap exit SIGINT

    cd "${PSLPYTHON_DIR}"

    local repo=''
    if [[ $1 == 'test' ]]; then
        repo='https://test.pypi.org/legacy/'
    elif [[ $1 == 'release' ]]; then
        repo='https://upload.pypi.org/legacy/'
    else
        echo "Unknown deploy type ('${1}'), expected 'test' or 'release'."
        exit 2
    fi

    if [[ -z "$TWINE_USERNAME" ]]; then
        echo "Could not locate twine username (env variable: TWINE_USERNAME)."
        exit 3
    fi

    if [[ -z "$TWINE_PASSWORD" ]]; then
        echo "Could not locate twine password (env variable: TWINE_PASSWORD)."
        exit 4
    fi

    python3 -m twine upload --repository-url "${repo}" dist/pslpython-*.whl
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
