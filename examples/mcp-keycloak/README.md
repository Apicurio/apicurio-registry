# MCP Server + Keycloak + Apicurio Registry

Local development stack for testing the [Apicurio Registry MCP server](https://github.com/Apicurio/apicurio-registry/tree/main/mcp) against a **Keycloak-secured** Registry instance.

Two MCP modes are supported:

- **stdio + client credentials** — The MCP client starts the server as a local process; MCP authenticates to Registry with a service account (`APICURIO_MCP_AUTH_*`).
- **HTTP + OAuth token forwarding** — Local MCP clients or remote agents call a long-running MCP HTTP service; the caller's Keycloak bearer token is forwarded unchanged to Registry (same RBAC as the UI).

## Stack

| Service           | URL                       | Purpose                          |
| ----------------- | ------------------------- | -------------------------------- |
| Keycloak          | http://localhost:8080     | OIDC provider (`registry` realm) |
| Registry API      | http://localhost:8081     | Secured REST API                 |
| Registry UI       | http://localhost:8888     | Web console (OIDC login)         |
| MCP server (HTTP) | http://localhost:8082/mcp | OAuth-secured MCP endpoint       |

Keycloak imports the shared test realm from [`utils/tests/src/main/resources/realm.json`](../../utils/tests/src/main/resources/realm.json). A one-shot bootstrap container adds the **`apicurio-mcp`** public OAuth client (authorization code + PKCE) for HTTP-mode MCP clients. Service accounts from the shared realm include **`admin-client`** (secret: `test1`, `sr-admin`) for stdio mode.

Configuration properties, HTTP/OAuth setup, and client examples are documented in the [MCP server integration guide](../../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc).

## Quick start

```bash
cd examples/mcp-keycloak
docker compose up -d
```

The stack uses `quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot`. To exercise local MCP server changes, build and tag an image first:

```bash
# From repo root
./mvnw install -Pfull -pl mcp -am -DskipTests
cd mcp/target/docker
docker build -f Dockerfile.jvm -t quay.io/apicurio/apicurio-registry-mcp-server:latest-snapshot .
```

Verify Registry access with client credentials:

```bash
ACCESS_TOKEN="$(
  curl -sS -X POST "http://localhost:8080/realms/registry/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "grant_type=client_credentials" \
    -d "client_id=admin-client" \
    -d "client_secret=test1" \
  | jq -r '.access_token'
)"

curl -sS -H "Authorization: Bearer ${ACCESS_TOKEN}" \
  "http://localhost:8081/apis/registry/v3/system/info" | jq .
```

## Client configuration

- **stdio:** copy [`stdio.example.json`](stdio.example.json) into your MCP client's configuration.
- **HTTP:** copy [`http.example.json`](http.example.json) into your MCP client's configuration, or point the client at `http://localhost:8082/mcp` and complete OAuth against the `apicurio-mcp` client in Keycloak.

See [`docker-compose.yml`](docker-compose.yml) for the full HTTP MCP + OIDC environment and the [integration guide](../../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc) for property reference and HTTP/OAuth setup.

## Stop

```bash
docker compose down
```

## Notes

- This example is for **local development only** (Keycloak `start-dev`, known client secrets).
- Port **8081** is used for Registry so Keycloak can keep **8080**.
- HTTP transport is included in the MCP server image (`quarkus.mcp.server.http.enabled=true`). The `/mcp` endpoint is only reachable when `APICURIO_MCP_HTTP_ENABLED=true` with OIDC configured at runtime (see integration guide).
- The MCP service sets `QUARKUS_HTTP_CORS_ORIGINS=http://localhost:6274` for local browser tools (e.g. MCP Inspector). **Do not use localhost CORS origins or `*` in production** — set the exact origin(s) of your deployed MCP client.
