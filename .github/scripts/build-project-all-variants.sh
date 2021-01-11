#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_REF="$1"
SKIP_TESTS_FLAG="$2"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}


case $BRANCH_NAME in

  "1.2.x" | "1.3.x")
       echo "Building Branch '$BRANCH_NAME'"
       echo "Building variants: [mem, asyncmem, infinispan, jpa, kafka, streams]"
       mvn clean install -Pprod -Pasyncmem -Pinfinispan -Pjpa -Pkafka -Pstreams -pl !tests -DskipTests=${SKIP_TESTS_FLAG}
       ;; 

  "master" | "2.0.x")
       echo "Building Branch '$BRANCH_NAME'"
       echo "Building variants: [mem, asyncmem, infinispan, kafkasql, sql, streams]"
       mvn clean install -Pprod -Pasyncmem -Pinfinispan -Pkafkasql -Psql -Pstreams -pl !tests -DskipTests=${SKIP_TESTS_FLAG}
       ;;

   *)
       echo "Build Failed. Branch '$BRANCH_NAME' not supported."
       exit 1
       ;;
esac
        