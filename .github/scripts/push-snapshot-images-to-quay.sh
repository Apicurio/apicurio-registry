#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_REF="$1"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}


case $BRANCH_NAME in

   "1.2.x" | "1.3.x")
      # if branch "1.2.x" or "1.3.x"
      # variants to push: mem, asyncmem, infinispan, jpa, kafka, streams
      echo "Pushing Snapshot Images for Branch '$BRANCH_NAME'"
      docker push quay.io/apicurio/apicurio-registry-mem:${BRANCH_NAME}-snapshot
      docker push quay.io/apicurio/apicurio-registry-asyncmem:${BRANCH_NAME}-snapshot
      docker push quay.io/apicurio/apicurio-registry-infinispan:${BRANCH_NAME}-snapshot
      docker push quay.io/apicurio/apicurio-registry-jpa:${BRANCH_NAME}-snapshot
      docker push quay.io/apicurio/apicurio-registry-kafka:${BRANCH_NAME}-snapshot
      docker push quay.io/apicurio/apicurio-registry-streams:${BRANCH_NAME}-snapshot
      ;;

    "2.0.x")
       # if branch "2.0.x"
       # variants to push: mem, asyncmem, infinispan, kafkasql, sql, streams
       echo "Pushing Snapshot Images for Branch '$BRANCH_NAME'"
       docker push quay.io/apicurio/apicurio-registry-mem:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-asyncmem:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-infinispan:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-kafkasql:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-sql:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-streams:${BRANCH_NAME}-snapshot
       ;;

    "master")
       # if master branch
       # variants to push: mem, asyncmem, infinispan, kafkasql, sql, streams
       echo "Pushing Snapshot Images for Branch '$BRANCH_NAME'"
       docker push quay.io/apicurio/apicurio-registry-mem:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-asyncmem:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-infinispan:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-kafkasql:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-sql:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-streams:latest-snapshot
       ;;

    *)
       # any other branch
       echo "No Image available to push for Branch '${BRANCH_NAME}'"
       exit 1
       ;;
esac
        
