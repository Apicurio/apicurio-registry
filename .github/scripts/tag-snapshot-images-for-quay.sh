#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
BRANCH_REF="$1"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}


case $BRANCH_NAME in

  "1.2.x" | "1.3.x")
       # if branch "1.2.x" or "1.3.x"
       # variants to tag: mem, asyncmem, infinispan, jpa, kafka, streams
       echo "Tagging Snapshot Images for Branch '$BRANCH_NAME'"
       docker image tag apicurio/apicurio-registry-mem:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-mem:${BRANCH_NAME}-snapshot 
       docker image tag apicurio/apicurio-registry-asyncmem:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-asyncmem:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-infinispan:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-infinispan:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-jpa:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-jpa:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-kafka:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-kafka:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-streams:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-streams:${BRANCH_NAME}-snapshot
       ;;

   "2.0.x")
       # if branch "2.0.x"
       # variants to tag: mem, asyncmem, infinispan, kafkasql, sql, streams
       echo "Tagging Snapshot Images for Branch '$BRANCH_NAME'"
       docker image tag apicurio/apicurio-registry-mem:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-mem:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-asyncmem:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-asyncmem:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-infinispan:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-infinispan:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-kafkasql:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-kafkasql:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-sql:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-sql:${BRANCH_NAME}-snapshot
       docker image tag apicurio/apicurio-registry-streams:${BRANCH_NAME}-snapshot quay.io/riprasad/apicurio-registry-streams:${BRANCH_NAME}-snapshot
       ;;

  "master")
       # if master branch
       # variants to tag: mem, asyncmem, infinispan, kafkasql, sql, streams
       echo "Tagging Snapshot Images for Branch '$BRANCH_NAME'"
       docker image tag apicurio/apicurio-registry-mem:latest-snapshot quay.io/riprasad/apicurio-registry-mem:latest-snapshot
       docker image tag apicurio/apicurio-registry-asyncmem:latest-snapshot quay.io/riprasad/apicurio-registry-asyncmem:latest-snapshot
       docker image tag apicurio/apicurio-registry-infinispan:latest-snapshot quay.io/riprasad/apicurio-registry-infinispan:latest-snapshot
       docker image tag apicurio/apicurio-registry-kafkasql:latest-snapshot quay.io/riprasad/apicurio-registry-kafkasql:latest-snapshot
       docker image tag apicurio/apicurio-registry-sql:latest-snapshot quay.io/riprasad/apicurio-registry-sql:latest-snapshot
       docker image tag apicurio/apicurio-registry-streams:latest-snapshot quay.io/riprasad/apicurio-registry-streams:latest-snapshot
       ;;

  *)
       # any other branch
       echo "No Image available to tag for Branch '${BRANCH_NAME}'"
       exit 1
       ;;

esac
        