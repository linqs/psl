#!/bin/bash

# Sign and deploy build artifacts to the test servers.
# This script will not return a non-zero exit code if requirements are not met to run it.
# It will only return a non-zero code if the requirements are met and the process fails.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly DEPLOY_SCRIPT="${THIS_DIR}/deploy-artifacts.sh"

readonly REPO_ROOT_DIR="${THIS_DIR}/.."
readonly POM_PATH="${REPO_ROOT_DIR}/pom.xml"

# Check if we are in the correct CI state for this script.
function verifyCIState() {
    local gitref=$1

    local returnValue=0
    local shift=0

    # Bail if no ref.
    if [[ -z "${gitref}" ]]; then
        echo "Cannot find git ref."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    # Test deployment should only happen on a push to develop.
    if [[ ! "${gitref}" == 'refs/heads/develop' ]]; then
        echo "Git ref does not point to develop: '${gitref}'."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    # Check that this is a snapshot build.
    if ! grep -q '<version>.\..\..-SNAPSHOT</version>' "${POM_PATH}" ; then
        echo "Build version does not look like a snapshot."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    return ${returnValue}
}

function main() {
    if [[ $# -ne 0 ]]; then
        echo "USAGE: $0"
        exit 1
    fi

    trap exit SIGINT
    set -e

    if ! verifyCIState "${GITHUB_REF}" ; then
        echo "Skipping test artifact deploy."
        return 0
    fi

    "${DEPLOY_SCRIPT}" test
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
