#!/bin/bash

# Sign and deploy build artifacts to the production servers.
# This script will not return a non-zero exit code if requirements are not met to run it.
# It will only return a non-zero code if the requirements are met and the process fails.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly DEPLOY_SCRIPT="${THIS_DIR}/deploy-artifacts.sh"

readonly REF_VERSION_TAG_REGEX='^refs/tags/(CANARY-)?[0-9]+\.[0-9]+\.[0-9]+$'

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

    # Prod deployment should only happen on a version tag push.
    if [[ ! "${gitref}" =~ $REF_VERSION_TAG_REGEX ]]; then
        echo "Found git ref that does not look like a version tag: '${gitref}'."
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
        echo "Skipping prod artifact deploy."
        return 0
    fi

    "${DEPLOY_SCRIPT}" prod
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
