#!/bin/bash

# Run style and lint checks.
# Any non-zero exit means failure.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
readonly ROOT_DIR="${THIS_DIR}/.."

# Just some checks that don't fall into lint or style checks.
misc_linqs_checks() {
    local status=0
    local offendingFiles=''

    # Every file needs a copyright.
    echo "Checking that every source file has a copyright."

    offendingFiles=$(grep -RL 'Copyright 2013-' "${ROOT_DIR}/"*"/src" "${ROOT_DIR}/psl-python/pslpython" | grep -v '__pycache__' | grep -v '/resources/' | grep  -v '.jar')
    if [[ ! -z ${offendingFiles} ]]; then
        status=$(($status | 32))

        echo "The following source files are missing a copyright:"
        echo "${offendingFiles}"
        echo "---"
    fi

    # The copyright should be up-to-date.
    echo "Checking that every source file has a copyright that is up-to-date."
    local year=$(date "+%Y")

    offendingFiles=$(grep -R 'Copyright 2013-' "${ROOT_DIR}/"*"/src" "${ROOT_DIR}/psl-python/pslpython" | grep -v '__pycache__' | sed 's/:.*Copyright 2013-\([0-9]\+\).*/\t\1/' | grep -Pv "\t${year}$")
    if [[ ! -z ${offendingFiles} ]]; then
        status=$(($status | 64))

        echo "The following source files have an out-of-date copyright:"
        echo "${offendingFiles}"
        echo "---"
    fi

    return $status
}

main() {
    if [[ $# -ne 0 ]]; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT

    local status=0

    # Run lint.
    mvn compile spotbugs:check -P all-modules
    status=$(($status | $?))

    # Run misc checks.
    misc_linqs_checks
    status=$(($status | $?))

    if [[ $status -eq 0 ]]; then
        echo "All style and lint checks passed!"
    else
        echo "Some style and lint checks have failed, see output for details."
    fi

    return $status
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
