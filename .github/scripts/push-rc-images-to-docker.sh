#!/bin/bash
set -euxo pipefail

# Initializing the variable with the Passed Parameters
VERSION="$1"

echo "Pushing Release Candidate Images"
docker push apicurio/apicurio-registry-mem:latest-snapshot
docker push apicurio/apicurio-registry-mem:${VERSION}
docker push apicurio/apicurio-registry-asyncmem:latest-snapshot
docker push apicurio/apicurio-registry-asyncmem:${VERSION}
docker push apicurio/apicurio-registry-infinispan:latest-snapshot
docker push apicurio/apicurio-registry-infinispan:${VERSION}
docker push apicurio/apicurio-registry-kafkasql:latest-snapshot
docker push apicurio/apicurio-registry-kafkasql:${VERSION}
docker push apicurio/apicurio-registry-sql:latest-snapshot
docker push apicurio/apicurio-registry-sql:${VERSION}
docker push apicurio/apicurio-registry-streams:latest-snapshot
docker push apicurio/apicurio-registry-streams:${VERSION}
docker push apicurio/apicurio-registry-tenant-manager-api:latest-snapshot
docker push apicurio/apicurio-registry-tenant-manager-api:${VERSION}
