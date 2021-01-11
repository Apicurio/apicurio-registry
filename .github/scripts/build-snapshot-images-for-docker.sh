#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_REF="$1"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}

# change directory to docker pom
cd distro/docker


case $BRANCH_NAME in

  "1.2.x" | "1.3.x")
       # if branch 1.2.x (or) 1.3.x, tag images in the form "${BRANCH_NAME}-snapshot"
       # variants to build: mem, asyncmem, infinispan, jpa, kafka, streams
       echo "Building Snapshot Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pasyncmem -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pinfinispan -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pjpa -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pkafka -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pstreams -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       ;;

   "2.0.x")
       # if branch "2.0.x", tag image with "2.0.x-snapshot"
       # variants to build: mem, asyncmem, infinispan, kafkasql, sql, streams
       echo "Building Snapshot Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pasyncmem -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pinfinispan -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       mvn package -Pprod -Pstreams -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-snapshot
       ;; 

  "master")
       # if master branch, tag image with "latest-snapshot"
       # variants to build: mem, asyncmem, infinispan, kafkasql, sql, streams
       echo "Building Snapshot Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot
       mvn package -Pprod -Pasyncmem -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot
       mvn package -Pprod -Pinfinispan -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot
       mvn package -Pprod -Pstreams -DskipTests -Ddocker -Ddocker.tag.name=latest-snapshot
       ;;

    *)
       echo "Cannot Build Image for Branch '${BRANCH_NAME}'"
       exit 1
       ;;
esac
        