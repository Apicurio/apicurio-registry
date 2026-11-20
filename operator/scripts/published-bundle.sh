#!/usr/bin/env bash
set -euo pipefail

# The OpenShift pipeline rebuilds submitted bundles. Never fall back to the
# upstream Apicurio image: FBC submissions only accept the pipeline registry.
VERSION=$1
[[ "$VERSION" =~ ^[0-9]+\.[0-9]+\.[0-9]+([.-][a-z0-9.-]+)?$ ]] || exit 1
REPOSITORY=community-operator-pipeline-prod/apicurio-registry-3
for ((attempt=1; attempt<=30; attempt++)); do
    if RESPONSE=$(curl --fail --silent --show-error --connect-timeout 10 --max-time 20 \
        "https://quay.io/api/v1/repository/$REPOSITORY/tag/?specificTag=$VERSION"); then
        DIGEST=$(jq -r --arg version "$VERSION" \
            '[.tags[] | select(.name == $version and .is_manifest_list != true) | .manifest_digest] | unique | if length == 1 then .[0] else empty end' <<< "$RESPONSE")
        if [[ "$DIGEST" =~ ^sha256:[0-9a-f]{64}$ ]]; then
            echo "quay.io/$REPOSITORY@$DIGEST"
            exit 0
        fi
    fi
    echo "Waiting for pipeline bundle $VERSION (attempt $attempt/30)" >&2
    if [ "$attempt" -lt 30 ]; then
        sleep 30
    fi
done
echo "Pipeline bundle $VERSION is not available; refusing to generate an invalid catalog." >&2
exit 1
