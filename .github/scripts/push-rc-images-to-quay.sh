#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
VERSION="$1"

echo "Pushing Release Candidate Images"
docker push quay.io/apicurio/apicurio-registry-mem:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-mem:${VERSION}
docker push quay.io/apicurio/apicurio-registry-asyncmem:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-asyncmem:${VERSION}
docker push quay.io/apicurio/apicurio-registry-infinispan:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-infinispan:${VERSION}
docker push quay.io/apicurio/apicurio-registry-kafkasql:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-kafkasql:${VERSION}
docker push quay.io/apicurio/apicurio-registry-sql:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-sql:${VERSION}
docker push quay.io/apicurio/apicurio-registry-streams:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-streams:${VERSION}
docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:latest-snapshot
docker push quay.io/apicurio/apicurio-registry-tenant-manager-api:${VERSION}
 
