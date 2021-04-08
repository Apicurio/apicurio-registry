#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter

IMAGE_REPOSITORY="$1"  # Image Repository, e.g. docker.io, quay.io
RELEASE_VERSION="$2"   # Release version

# Check if image repository is either 'docker.io' or 'quay.io'
if [[ ($IMAGE_REPOSITORY != "docker.io") && ($IMAGE_REPOSITORY != "quay.io") ]]
then
	echo "ERROR: Illegal value '${IMAGE_REPOSITORY}' for variable 'IMAGE_REPOSITORY'. Values can only be [docker.io, quay.io]"
    exit 1
fi


# building Images
make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=latest-snapshot build-all-images
make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=${RELEASE_VERSION} build-all-images
