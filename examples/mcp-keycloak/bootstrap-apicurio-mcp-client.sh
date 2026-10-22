#!/usr/bin/env bash
set -euo pipefail

KC_URL="${KC_URL:-http://keycloak:8080}"
REALM="${KC_REALM:-registry}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
CLIENT_ID="apicurio-mcp"
CLIENT_JSON="/bootstrap/apicurio-mcp-client.json"

echo "Waiting for Keycloak at ${KC_URL}..."
until /opt/keycloak/bin/kcadm.sh config credentials \
  --server "${KC_URL}" \
  --realm master \
  --user "${ADMIN_USER}" \
  --password "${ADMIN_PASS}" >/dev/null 2>&1; do
  sleep 2
done

if /opt/keycloak/bin/kcadm.sh get clients -r "${REALM}" -q "clientId=${CLIENT_ID}" 2>/dev/null | grep -q '"id"'; then
  echo "Keycloak client '${CLIENT_ID}' already exists in realm '${REALM}', skipping."
  exit 0
fi

echo "Creating Keycloak client '${CLIENT_ID}' in realm '${REALM}'..."
/opt/keycloak/bin/kcadm.sh create clients -r "${REALM}" -f "${CLIENT_JSON}"
echo "Done."
