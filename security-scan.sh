#!/bin/bash

set -eo pipefail

ls -lrt

# The API Token for SourceClear will be provided during pipeline runtime and you should make sure that following environment variables are available
if [[ ! -z "${SRCCLR_API_TOKEN}" ]]; then
   echo "SRCCLR_API_TOKEN is set. Scanning can begin..."
else
   echo "SRCCLR_API_TOKEN is set. Aborting the process..."
   exit 1
fi

# Triggering the scan
docker run -v $(pwd):/opt/srs-service-registry:z \
	  -e SRCCLR_API_TOKEN=${SRCCLR_API_TOKEN} \
	  -e JAVA_OPTS="-Duser.home=/tmp" \
	  -w /opt/srs-service-registry \
	  -u $(id -u) \
	  quay.io/app-sre/mk-ci-tools:latest /bin/bash -c "mkdir -p logs/ && srcclr.sh | tee logs/scan_result.txt"