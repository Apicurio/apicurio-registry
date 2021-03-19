#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameter
BRANCH_REF="$1"

# Removing string "refs/heads" from variable ${BRANCH_REF} using Parameter Substitution
BRANCH_NAME=${BRANCH_REF#refs/heads/}


case $BRANCH_NAME in

    "master")
       echo "Pushing Snapshot Images for Branch '$BRANCH_NAME'"
       docker push quay.io/apicurio/apicurio-registry-mem:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-kafkasql:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-sql:latest-snapshot
       docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:latest-snapshot
       ;;

    *)
       echo "Pushing Snapshot Images for Branch '$BRANCH_NAME'"
       docker push quay.io/apicurio/apicurio-registry-mem:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-kafkasql:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-sql:${BRANCH_NAME}-snapshot
       docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:${BRANCH_NAME}-snapshot
       ;;
esac
        
