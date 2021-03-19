#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_NAME="$1"


case $BRANCH_NAME in

  "master")
       echo "Building Release Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=latest-release -pl distro/docker
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=latest-release -pl distro/docker
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=latest-release -pl distro/docker
       make CONTAINER_IMAGE_TAG=latest-release tenant-manager-container
       ;;

   *)
       echo "Building Release Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release -pl distro/docker
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release -pl distro/docker
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release -pl distro/docker
       make CONTAINER_IMAGE_TAG=${BRANCH_NAME}-release tenant-manager-container
       ;; 
esac