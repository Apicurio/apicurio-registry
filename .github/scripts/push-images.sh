#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter

BRANCH_NAME="$1"       # Git Branch
IMAGE_REPOSITORY="$2"  # Image Repository, e.g. docker.io, quay.io
RELEASE_TYPE="$3"      # Either 'snapshot' or 'release'
RELEASE_VERSION="$4"   # Release version (pass 'null' for snapshot release)



# Check if release type is valid
if [[ ($RELEASE_TYPE != "release") &&  ($RELEASE_TYPE != "snapshot") ]]
then
    echo "ERROR: Illegal value '${RELEASE_TYPE}' for variable 'RELEASE_TYPE'. Values can only be [release, snapshot]"
    exit 1	  
fi


# Check if image repository is either 'docker.io' or 'quay.io'
if [[ ($IMAGE_REPOSITORY != "docker.io") && ($IMAGE_REPOSITORY != "quay.io") ]]
then
	echo "ERROR: Illegal value '${IMAGE_REPOSITORY}' for variable 'IMAGE_REPOSITORY'. Values can only be [docker.io, quay.io]"
    exit 1
fi


# If it is not a snapshot release, We also need to push images tagged with the '${RELEASE_VERSION}'
if [[ $RELEASE_TYPE == "release" ]]
then
    echo "Not a Snapshot Release. Pushing Images With '${RELEASE_VERSION}' Tag."
    make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=${RELEASE_VERSION} push-all-images
fi

case $BRANCH_NAME in

    "master")
       # if master branch, push images with tag "latest-${RELEASE_TYPE}"
       make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=latest-${RELEASE_TYPE} push-all-images
       ;;

    *)
       # if other than master, push images with tag "${BRANCH_NAME}-${RELEASE_TYPE}"
       make IMAGE_REPO=${IMAGE_REPOSITORY} IMAGE_TAG=${BRANCH_NAME}-${RELEASE_TYPE} push-all-images
       ;;
esac
        
