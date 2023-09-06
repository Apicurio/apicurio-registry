#!/bin/bash
set -euo pipefail

# Usage:
# - no env vars exported -> download from public GH releases
# - `APICURIO_CLIENT_GEN_VERSION` -> Use a specific version instead of the default
# - `APICURIO_CLIENT_GEN_URL` -> completely override the download URL diregarding the version
# - `APICURIO_CLIENT_GEN_FRESH` -> forcefully download from remote the distribution archive

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

VERSION="${APICURIO_CLIENT_GEN_VERSION-"0.1.2"}"
echo "Using version: ${VERSION}"

UPSTREAM_DIST_URL="https://github.com/Apicurio/apicurio-client-gen/releases/download/${VERSION}/dist.tar.gz"

if [[ -z "${APICURIO_CLIENT_GEN_URL-}" ]]; then
  echo "Downloading from upstream github"
  URL="${UPSTREAM_DIST_URL}"
else
  echo "Downloading using custom url"
  URL="${APICURIO_CLIENT_GEN_URL}"
fi

if [[ -z "${APICURIO_CLIENT_GEN_FRESH-}" ]]; then
  echo "Attempting to use cached resources"
else
  echo "Running a fresh download"
  rm -f $SCRIPT_DIR/client-gen.tar.gz
fi

if [ -f "$SCRIPT_DIR/client-gen.tar.gz" ]; then
    echo "Using cached version of client-gen.tar.gz"
else
  echo "Fetching archive from ${URL}"

  OUTPUT_FILE="$SCRIPT_DIR/client-gen.tar.gz"

  STATUS_CODE=$(curl -sLS -o "$OUTPUT_FILE" --write-out "%{http_code}" "$URL")

  if [[ "${STATUS_CODE}" -lt 200 || "${STATUS_CODE}" -gt 299 ]]; then
    echo "Could not download $URL. Got HTTP code $STATUS_CODE."
    exit 1
  fi
fi

echo "Deleting client-gen/dist ..."
rm -rf "$SCRIPT_DIR/../client-gen/dist"

echo "Extracting client-gen.tar.gz ..."

tar -xvf "$SCRIPT_DIR/client-gen.tar.gz" -C "$SCRIPT_DIR/../client-gen"

echo "Done!"
