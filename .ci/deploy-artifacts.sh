#!/bin/bash

# Sign and deploy build artifacts to the supplied destination.
# This script will not return a non-zero exit code if requirements are not met to run it.
# It will only return a non-zero code if the requirements are met and the process fails.

readonly THIS_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"

readonly SETTINGS_XML_PATH="${THIS_DIR}/settings.xml"
readonly SETTINGS_XML_DEST="${HOME}/.m2/"

# Check if we are in the correct CI state for this script.
function verifyCIState() {
    local repoId=$1
    local gitref=$2

    local returnValue=0
    local shift=0

    if [[ -z "${gitref}" ]]; then
        echo "Cannot find git ref."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    if [[ -z "${repoId}" ]]; then
        echo "Cannot find git repository id."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    if [[ "${repoId}" != 'linqs/psl' ]]; then
        echo "Deployment will only happen on the 'linqs/psl' respository. Found '${repoId}'."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    # Check env variables.

    if [[ -z "${OSSRH_JIRA_USERNAME}" ]]; then
        echo "Cannot find OSSRH username."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    if [[ -z "${OSSRH_JIRA_PASSWORD}" ]]; then
        echo "Cannot find OSSRH password."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    if [[ -z "${GPG_KEY_NAME}" ]]; then
        echo "Cannot find GPG key name."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    if [[ -z "${TWINE_USERNAME}" ]]; then
        echo "Cannot find Twine username."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    if [[ -z "${TWINE_PASSWORD}" ]]; then
        echo "Cannot find Twine password."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    # Check for the actual GPG key.
    if ! gpg --list-key "${GPG_KEY_NAME}" > /dev/null 2> /dev/null ; then
        echo "Cannot find GPG key on machine."
        returnValue=$((returnValue | (1 << shift++)))
    fi

    return ${returnValue}
}

function main() {
    if [[ $# -ne 1 ]] || [[ $1 != 'test' && $1 != 'prod' ]] ; then
        echo "USAGE: $0 <test|prod>"
        exit 1
    fi

    trap exit SIGINT
    set -e

    if ! verifyCIState "${GITHUB_REPOSITORY}" "${GITHUB_REF}" ; then
        echo "Skipping artifact deploy."
        return 0
    fi

    cp "${SETTINGS_XML_PATH}" "${SETTINGS_XML_DEST}"

    if [[ $1 == 'test' ]] ; then
        mvn verify deploy -P all-modules -P test-release -D maven.test.skip=true
    else
        mvn verify deploy -P all-modules -P central-release -D maven.test.skip=true
    fi
}

[[ "${BASH_SOURCE[0]}" == "${0}" ]] && main "$@"
