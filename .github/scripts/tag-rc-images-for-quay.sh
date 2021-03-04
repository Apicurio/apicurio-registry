#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
VERSION="$1"

echo "Tagging Release Candidate Images"
docker image tag apicurio/apicurio-registry-mem:latest-snapshot quay.io/apicurio/apicurio-registry-mem:latest-snapshot
docker image tag apicurio/apicurio-registry-mem:${VERSION} quay.io/apicurio/apicurio-registry-mem:${VERSION}
docker image tag apicurio/apicurio-registry-asyncmem:latest-snapshot quay.io/apicurio/apicurio-registry-asyncmem:latest-snapshot
docker image tag apicurio/apicurio-registry-asyncmem:${VERSION} quay.io/apicurio/apicurio-registry-asyncmem:${VERSION}
docker image tag apicurio/apicurio-registry-infinispan:latest-snapshot quay.io/apicurio/apicurio-registry-infinispan:latest-snapshot
docker image tag apicurio/apicurio-registry-infinispan:${VERSION} quay.io/apicurio/apicurio-registry-infinispan:${VERSION}
docker image tag apicurio/apicurio-registry-kafkasql:latest-snapshot quay.io/apicurio/apicurio-registry-kafkasql:latest-snapshot
docker image tag apicurio/apicurio-registry-kafkasql:${VERSION} quay.io/apicurio/apicurio-registry-kafkasql:${VERSION}
docker image tag apicurio/apicurio-registry-sql:latest-snapshot quay.io/apicurio/apicurio-registry-sql:latest-snapshot
docker image tag apicurio/apicurio-registry-sql:${VERSION} quay.io/apicurio/apicurio-registry-sql:${VERSION}
docker image tag apicurio/apicurio-registry-streams:latest-snapshot quay.io/apicurio/apicurio-registry-streams:latest-snapshot
docker image tag apicurio/apicurio-registry-streams:${VERSION} quay.io/apicurio/apicurio-registry-streams:${VERSION}
docker image tag apicurio/apicurio-registry-tenant-manager-api:latest-snapshot quay.io/apicurio/apicurio-registry-tenant-manager-api:latest-snapshot
docker image tag apicurio/apicurio-registry-tenant-manager-api:${VERSION} quay.io/apicurio/apicurio-registry-tenant-manager-api:${VERSION}
 