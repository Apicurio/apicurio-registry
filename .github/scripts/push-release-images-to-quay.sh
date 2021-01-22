#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
BRANCH_NAME="$1"
VERSION="$2"

case $BRANCH_NAME in

    "master")
       echo "Pushing Release Images for Branch '$BRANCH_NAME'"
       docker push quay.io/apicurio/apicurio-registry-mem:latest-release
       docker push quay.io/apicurio/apicurio-registry-mem:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-asyncmem:latest-release
       docker push quay.io/apicurio/apicurio-registry-asyncmem:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-infinispan:latest-release
       docker push quay.io/apicurio/apicurio-registry-infinispan:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-kafkasql:latest-release
       docker push quay.io/apicurio/apicurio-registry-kafkasql:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-sql:latest-release
       docker push quay.io/apicurio/apicurio-registry-sql:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-streams:latest-release
       docker push quay.io/apicurio/apicurio-registry-streams:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:latest-release
       docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:${VERSION}
       ;;

    *)
       echo "Pushing Release Images for Branch '$BRANCH_NAME'"
       docker push quay.io/apicurio/apicurio-registry-mem:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-mem:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-asyncmem:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-asyncmem:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-infinispan:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-infinispan:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-kafkasql:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-kafkasql:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-sql:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-sql:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-streams:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-streams:${VERSION}
       docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:${BRANCH_NAME}-release
       docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:${VERSION}
       ;;
esac
        
