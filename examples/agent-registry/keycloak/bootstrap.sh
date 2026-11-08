#!/bin/bash
# Adds the apicurio-mcp client to the shared "registry" realm. Safe to run again.
set -euo pipefail
KCADM=/opt/keycloak/bin/kcadm.sh
until $KCADM config credentials --server http://keycloak:8080 --realm master --user admin --password admin >/dev/null 2>&1; do
  sleep 2
done
if $KCADM get clients -r registry -q clientId=apicurio-mcp | grep -q '"id"'; then
  echo "apicurio-mcp client already exists"
else
  $KCADM create clients -r registry -f /bootstrap/apicurio-mcp-client.json
  echo "apicurio-mcp client created"
fi
