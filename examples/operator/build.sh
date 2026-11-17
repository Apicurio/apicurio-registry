#!/usr/bin/env bash
# Builds the operator from this checkout: the controller (Maven), its image, and the install file
# (CRD, RBAC, Deployment) that points at it and at the registry images to deploy.
# Usage: ./build.sh                  everything (JDK 21 or later, Docker or Podman, kubectl, envsubst)
#        ./build.sh --install-file   only the install file, for the current image variables
# Images: OPERATOR_IMAGE (default apicurio/apicurio-registry-3-operator:example), and the operands
# REGISTRY_IMAGE and REGISTRY_UI_IMAGE (default latest-snapshot). Maven reads MAVEN_ARGS.
set -euo pipefail
cd "$(dirname "$0")"
OPERATOR_IMAGE=${OPERATOR_IMAGE:-apicurio/apicurio-registry-3-operator:example}
if [ "${1:-}" != --install-file ]; then
  (cd ../.. && ./mvnw -q package -Dfull -pl operator/controller -am \
    -DskipTests -Dmaven.javadoc.skip=true -Dmaven.source.skip=true)
  make -s -C ../../operator image-build IMAGE="$OPERATOR_IMAGE"
fi
mkdir -p target
make -s -C ../../operator dist-install-file INSTALL_FILE="$PWD/target/install.yaml" IMAGE="$OPERATOR_IMAGE" \
  INSTALL_NAMESPACE=apicurio-operator \
  REGISTRY_APP_IMAGE="${REGISTRY_IMAGE:-apicurio/apicurio-registry:latest-snapshot}" \
  REGISTRY_UI_IMAGE="${REGISTRY_UI_IMAGE:-apicurio/apicurio-registry-ui:latest-snapshot}"
echo "target/install.yaml: operator $OPERATOR_IMAGE, registry ${REGISTRY_IMAGE:-apicurio/apicurio-registry:latest-snapshot}"
