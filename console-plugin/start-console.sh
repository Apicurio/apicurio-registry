#!/usr/bin/env bash

set -euo pipefail

CONSOLE_IMAGE=${CONSOLE_IMAGE:-"quay.io/openshift/origin-console:latest"}
CONSOLE_PORT=${CONSOLE_PORT:-9000}
PLUGIN_PORT=${PLUGIN_PORT:-9001}
PLUGIN_NAME="apicurio-registry-console-plugin"

echo "Starting OpenShift Console with ${PLUGIN_NAME} plugin..."
echo ""
echo "Prerequisites:"
echo "  - Plugin dev server running on http://localhost:${PLUGIN_PORT} (run 'yarn dev' first)"
echo "  - KUBECONFIG set to a valid cluster config"
echo ""

if [ -z "${KUBECONFIG:-}" ]; then
  echo "ERROR: KUBECONFIG environment variable is not set."
  echo "  export KUBECONFIG=/path/to/kubeconfig"
  exit 1
fi

BRIDGE_PLUGINS="${PLUGIN_NAME}=http://host.docker.internal:${PLUGIN_PORT}"

echo "Console URL: http://localhost:${CONSOLE_PORT}"
echo "Plugin URL:  http://localhost:${PLUGIN_PORT}"
echo ""

podman run \
  --rm \
  -p "${CONSOLE_PORT}:9000" \
  --env BRIDGE_USER_AUTH="disabled" \
  --env BRIDGE_K8S_MODE="off-cluster" \
  --env BRIDGE_K8S_AUTH="bearer-token" \
  --env BRIDGE_K8S_MODE_OFF_CLUSTER_SKIP_VERIFY_TLS=true \
  --env BRIDGE_K8S_MODE_OFF_CLUSTER_ENDPOINT="$(oc whoami --show-server)" \
  --env BRIDGE_K8S_AUTH_BEARER_TOKEN="$(oc whoami --show-token)" \
  --env BRIDGE_PLUGINS="${BRIDGE_PLUGINS}" \
  "${CONSOLE_IMAGE}"
