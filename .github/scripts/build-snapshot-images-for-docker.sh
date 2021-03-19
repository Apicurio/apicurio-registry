#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_REF="$1"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}


case $BRANCH_NAME in

  "master")
       # if master branch, tag image with "latest-snapshot"
       echo "Building Snapshot Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot -pl distro/docker
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot -pl distro/docker
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot -pl distro/docker
       make CONTAINER_IMAGE_TAG=latest-snapshot tenant-manager-container
       ;;

   *)
       # if other than master, tag image in the form "${BRANCH_NAME}-snapshot"
       echo "Building Snapshot Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot -pl distro/docker
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot -pl distro/docker
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot -pl distro/docker
       make CONTAINER_IMAGE_TAG=${BRANCH_NAME}-snapshot tenant-manager-container
       ;; 
esac
        