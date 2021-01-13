#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_NAME="$1"

# change directory to docker pom
cd registry/distro/docker


case $BRANCH_NAME in

  "master")
       echo "Building Release Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=latest-release
       mvn package -Pprod -Pasyncmem -DskipTests -Ddocker -Ddocker.tag.name=latest-release
       mvn package -Pprod -Pinfinispan -DskipTests -Ddocker -Ddocker.tag.name=latest-release
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=latest-release
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=latest-release
       mvn package -Pprod -Pstreams -DskipTests -Ddocker -Ddocker.tag.name=latest-release
       ;;

   *)
       echo "Building Release Images for Branch '$BRANCH_NAME'"
       mvn package -Pprod -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release
       mvn package -Pprod -Pasyncmem -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release
       mvn package -Pprod -Pinfinispan -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release
       mvn package -Pprod -Pkafkasql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release
       mvn package -Pprod -Psql -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release
       mvn package -Pprod -Pstreams -DskipTests -Ddocker -Ddocker.tag.name=${BRANCH_NAME}-release
       ;; 
esac