#!/bin/bash
set -euo pipefail

VERSION="0.1.0"

URL="https://github.com/Apicurio/apicurio-client-gen/releases/download/$VERSION/dist.zip"

SCRIPT_DIR=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" &>/dev/null && pwd)

OUTPUT_FILE="$SCRIPT_DIR/client-gen-$VERSION.zip"

STATUS_CODE=$(curl -sLS -o "$OUTPUT_FILE" --write-out "%{http_code}" "$URL")

if [[ "$STATUS_CODE" -lt 200 || ${STATUS_CODE} -gt 299 ]]; then
  echo "Could not download $URL. Got HTTP code $STATUS_CODE."
  exit 1
fi
