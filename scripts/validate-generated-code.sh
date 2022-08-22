#!/bin/bash
set -euxo pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

TMP=$(mktemp -d)
./apicurio-codegen --spec="/${SCRIPT_DIR}/../common/src/main/resources/META-INF/openapi.json" --output="${TMP}/sources.zip" --java-package="io.apicurio.registry.rest.v2" --generator=QUARKUS

unzip ${TMP}/sources.zip -d ${TMP}
rm -f ${TMP}/sources.zip

cp -r ${TMP}/* ${SCRIPT_DIR}/../common
rm -rf ${TMP}

if [ -n "$(git status --untracked-files=no --porcelain)" ]; then
  echo "Open API code needs to be regenerated."
  git --no-pager diff
  exit 1
fi
