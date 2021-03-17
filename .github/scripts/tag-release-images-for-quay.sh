#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
BRANCH_NAME="$1"
VERSION="$2"

case $BRANCH_NAME in

  "master")
       echo "Tagging Release Images for Branch '$BRANCH_NAME'"
       docker image tag apicurio/apicurio-registry-mem:latest-release quay.io/apicurio/apicurio-registry-mem:latest-release
       docker image tag apicurio/apicurio-registry-mem:${VERSION} quay.io/apicurio/apicurio-registry-mem:${VERSION}
       docker image tag apicurio/apicurio-registry-kafkasql:latest-release quay.io/apicurio/apicurio-registry-kafkasql:latest-release
       docker image tag apicurio/apicurio-registry-kafkasql:${VERSION} quay.io/apicurio/apicurio-registry-kafkasql:${VERSION}
       docker image tag apicurio/apicurio-registry-sql:latest-release quay.io/apicurio/apicurio-registry-sql:latest-release
       docker image tag apicurio/apicurio-registry-sql:${VERSION} quay.io/apicurio/apicurio-registry-sql:${VERSION}
       docker image tag apicurio/apicurio-registry-tenant-manager-api:latest-release quay.io/apicurio/apicurio-registry-tenant-manager-api:latest-release
       docker image tag apicurio/apicurio-registry-tenant-manager-api:${VERSION} quay.io/apicurio/apicurio-registry-tenant-manager-api:${VERSION}
       ;;

   *)
       echo "Tagging Release Images for Branch '$BRANCH_NAME'"
       docker image tag apicurio/apicurio-registry-mem:${BRANCH_NAME}-release quay.io/apicurio/apicurio-registry-mem:${BRANCH_NAME}-release
       docker image tag apicurio/apicurio-registry-mem:${VERSION} quay.io/apicurio/apicurio-registry-mem:${VERSION}
       docker image tag apicurio/apicurio-registry-kafkasql:${BRANCH_NAME}-release quay.io/apicurio/apicurio-registry-kafkasql:${BRANCH_NAME}-release
       docker image tag apicurio/apicurio-registry-kafkasql:${VERSION} quay.io/apicurio/apicurio-registry-kafkasql:${VERSION}
       docker image tag apicurio/apicurio-registry-sql:${BRANCH_NAME}-release quay.io/apicurio/apicurio-registry-sql:${BRANCH_NAME}-release
       docker image tag apicurio/apicurio-registry-sql:${VERSION} quay.io/apicurio/apicurio-registry-sql:${VERSION}
       docker image tag apicurio/apicurio-registry-tenant-manager-api:${BRANCH_NAME}-release quay.io/apicurio/apicurio-registry-tenant-manager-api:${BRANCH_NAME}-release
       docker image tag apicurio/apicurio-registry-tenant-manager-api:${VERSION} quay.io/apicurio/apicurio-registry-tenant-manager-api:${VERSION}
       ;;

esac
        