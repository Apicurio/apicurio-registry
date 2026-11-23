#!/usr/bin/env bash
# Adds what this example needs to the shared test realm, once Keycloak has imported it:
# - the apicurio-mcp public OAuth client (authorization code + PKCE) for HTTP-mode MCP clients;
# - an "apicurio-mcp" audience on the developer, read-only and no-role service accounts, so
#   demo.sh can call the HTTP MCP endpoint (which requires that audience) without a browser login.
#   admin-client is left alone: its tokens lack the audience, and demo.sh checks they are refused.
# Safe to run again: each step is skipped when already done.
set -euo pipefail

KC_URL="${KC_URL:-http://keycloak:8080}"
REALM="${KC_REALM:-registry}"
ADMIN_USER="${KEYCLOAK_ADMIN:-admin}"
ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin}"
CLIENT_ID="apicurio-mcp"
CLIENT_JSON="/bootstrap/apicurio-mcp-client.json"
AUDIENCE_CLIENTS="developer-client readonly-client no-role-client"
kcadm=/opt/keycloak/bin/kcadm.sh

echo "Waiting for Keycloak at ${KC_URL}..."
until $kcadm config credentials --server "${KC_URL}" --realm master \
  --user "${ADMIN_USER}" --password "${ADMIN_PASS}" >/dev/null 2>&1; do
  sleep 2
done

if $kcadm get clients -r "${REALM}" -q "clientId=${CLIENT_ID}" 2>/dev/null | grep -q '"id"'; then
  echo "Keycloak client '${CLIENT_ID}' already exists in realm '${REALM}', skipping."
else
  echo "Creating Keycloak client '${CLIENT_ID}' in realm '${REALM}'..."
  $kcadm create clients -r "${REALM}" -f "${CLIENT_JSON}"
fi

for client in ${AUDIENCE_CLIENTS}; do
  id=$($kcadm get clients -r "${REALM}" -q "clientId=${client}" --fields id --format csv --noquotes)
  if $kcadm get "clients/${id}/protocol-mappers/models" -r "${REALM}" | grep -q '"audience-apicurio-mcp"'; then
    echo "'${client}' already has the apicurio-mcp audience, skipping."
    continue
  fi
  echo "Adding the apicurio-mcp audience to '${client}'..."
  $kcadm create "clients/${id}/protocol-mappers/models" -r "${REALM}" \
    -s name=audience-apicurio-mcp -s protocol=openid-connect -s protocolMapper=oidc-audience-mapper \
    -s 'config."included.client.audience"=apicurio-mcp' \
    -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=false'
done
echo "Done."
