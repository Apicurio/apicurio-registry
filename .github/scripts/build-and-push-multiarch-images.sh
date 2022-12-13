#!/bin/bash
set -euxo pipefail

defvalue="foo"
variantDefValue="multiarch-registry-images"

# Initializing the variable with the Passed Parameter

BRANCH_NAME="$1"       # Git Branch
IMAGE_REPOSITORY="$2"  # Image Repository, e.g. docker.io, quay.io
VARIANT=${3:-$variantDefValue}   # Variant type
RELEASE_TYPE="$4"      # Either 'snapshot' or 'release' or 'pre-release'
RELEASE_VERSION=${5:-$defvalue}   # Release version (Pass the release version if you also want images tagged with the release version)

# Check if variant type is valid
if [[ ($VARIANT != "mem-multiarch-images") &&  ($VARIANT != "sql-multiarch-images") &&  ($VARIANT != "kafkasql-multiarch-images") && ($VARIANT != "multiarch-registry-images") ]]
then
    echo "ERROR: Illegal value '${VARIANT}' for variable '$VARIANT'. Values can only be [mem-multiarch-images, sql-multiarch-images, kafkasql-multiarch-images, multiarch-registry-images]"
    exit 1
fi

# Check if release type is valid
if [[ ($RELEASE_TYPE != "release") &&  ($RELEASE_TYPE != "snapshot") &&  ($RELEASE_TYPE != "pre-release") ]]
then
    echo "ERROR: Illegal value '${RELEASE_TYPE}' for variable 'RELEASE_TYPE'. Values can only be [release, snapshot, pre-release]"
    exit 1
fi

# Check if image repository is either 'docker.io' or 'quay.io'
if [[ ($IMAGE_REPOSITORY != "docker.io") && ($IMAGE_REPOSITORY != "quay.io") ]]
then
	echo "ERROR: Illegal value '${IMAGE_REPOSITORY}' for variable 'IMAGE_REPOSITORY'. Values can only be [docker.io, quay.io]"
    exit 1
fi



# If release version is passed as a parameter, build images tagged with the 'RELEASE_VERSION'
if [[ $RELEASE_VERSION != "foo" ]]
then
    echo "Building Images With '${RELEASE_VERSION}' Tag."
    make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=${RELEASE_VERSION} multiarch-registry-images
fi



# If it is a pre-release, skip images with other tags
if [[ $RELEASE_TYPE == "pre-release" ]]
then
    echo "This is a '${RELEASE_TYPE}'. Skipping other image tags."
    exit 0
fi



case $BRANCH_NAME in

  "main")
       # if main branch, build images with tag "latest-${RELEASE_TYPE}"
       make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=latest-${RELEASE_TYPE} ${VARIANT}
       ;;

   *)
       # if other than main, build images with tag "${BRANCH_NAME}-${RELEASE_TYPE}"
       make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=${BRANCH_NAME}-${RELEASE_TYPE} ${VARIANT}
       ;;
esac
