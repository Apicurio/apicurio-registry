# MCP server + Keycloak: step-by-step guide

This guide walks through the Apicurio Registry MCP server in front of a Keycloak-secured Registry.
You talk MCP by hand (JSON-RPC over stdin/stdout, then over HTTP) and see how each mode
authenticates, and how Registry's roles reach the MCP tools. Run the steps in order.

**Time:** about 20 minutes. **You will see:**

- the stdio mode, where the server logs in with a service account;
- that the service account's roles apply to every tool call;
- the HTTP mode, where every request needs a token issued for the MCP server;
- that the HTTP mode forwards the caller's own token, so each caller gets their own roles.

For the reference documentation, see *MCP server integration* in the Registry documentation
([source](../../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc)).

## Before you start

You need:

- Docker with Docker Compose v2, or Podman with a Compose provider;
- `curl` and `jq`;
- a shell: bash or zsh;
- free ports 8080 (Keycloak), 8081 (Registry API), 8082 (MCP server) and 8888 (Registry UI). If
  they are in use, see [Using other ports](#using-other-ports).

Run every command from this directory:

```bash
cd examples/mcp-keycloak
```

## Step 1: Start the example

```bash
docker compose up -d
docker compose ps --format '{{.Name}}: {{.Status}}'
```

Expected: `mcp-keycloak`, `mcp-keycloak-registry`, `mcp-keycloak-registry-ui` and
`mcp-keycloak-mcp-server` are `Up` (the first two `healthy`); `mcp-keycloak-bootstrap` has exited.
The stdio server is not listed: an MCP client starts one per session.

Define a few helpers for the next steps:

```bash
TOKEN_URL=http://localhost:8080/realms/registry/protocol/openid-connect/token
V3=http://localhost:8081/apis/registry/v3
token() { curl -s "$TOKEN_URL" -d "grant_type=client_credentials&client_id=$1&client_secret=test1" | jq -r .access_token; }
INIT='{"jsonrpc":"2.0","id":0,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"guide","version":"1"}}}'
INITIALIZED='{"jsonrpc":"2.0","method":"notifications/initialized"}'
```

## Step 2: The Registry is secured

```bash
curl -s -o /dev/null -w '%{http_code}\n' "$V3/groups"
curl -s -o /dev/null -w '%{http_code}\n' -H "Authorization: Bearer $(token admin-client)" "$V3/groups"
```

Expected: `401`, then `200`. Every MCP tool call ends up as one of these REST calls, so the
question for the rest of the guide is *whose* token the MCP server sends.

## Step 3: stdio mode, as a service account

An MCP client (Claude Desktop, Cursor, ...) starts the stdio server as a process and writes JSON-RPC
messages to its stdin, one per line. Do the same: initialize, then call `tools/list` and
`create_artifact`.

```bash
{
  echo "$INIT"; echo "$INITIALIZED"
  echo '{"jsonrpc":"2.0","id":1,"method":"tools/list","params":{}}'
  echo '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"create_artifact","arguments":{"groupId":"guide","artifactId":"orders","artifactType":"JSON","name":"Created over MCP"}}}'
  sleep 10
} | docker compose run --rm --no-deps -T apicurio-registry-mcp-stdio 2>/dev/null \
  | jq -c '{id, tools: (.result.tools // [] | map(.name) | length), text: (.result.content[0].text // null | if . then fromjson | {artifactId, owner} else null end)}'
```

Expected (the server may answer in a different order than it was asked; MCP clients match
responses by `id`):

```text
{"id":0,"tools":0,"text":null}
{"id":1,"tools":32,"text":null}
{"id":2,"tools":0,"text":{"artifactId":"orders","owner":"admin-client"}}
```

`create_artifact` requires `artifactType`: the artifact is created empty, so there is no content to
detect the type from. Without it, the MCP server refuses the call (`Missing required argument:
artifactType`).

The server logged in to Registry with `APICURIO_MCP_AUTH_CLIENT_ID=admin-client` (see
`docker-compose.yml`), so `admin-client` owns the artifact. Check it through REST:

```bash
curl -s -H "Authorization: Bearer $(token admin-client)" "$V3/groups/guide/artifacts/orders" | jq '{name, owner}'
```

## Step 4: stdio mode, the service account's roles apply

Start the server as `readonly-client` (`sr-readonly`) instead, and try to write:

```bash
{
  echo "$INIT"; echo "$INITIALIZED"
  echo '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_artifact","arguments":{"groupId":"guide","artifactId":"denied","artifactType":"JSON"}}}'
  sleep 10
} | docker compose run --rm --no-deps -T -e APICURIO_MCP_AUTH_CLIENT_ID=readonly-client \
    apicurio-registry-mcp-stdio 2>/dev/null | jq -c 'select(.id == 1) | .result'
```

Expected: `"isError": true` and a message carrying Registry's `403`. The MCP server does not decide
anything itself: Registry does, from the token's roles.

With a wrong `APICURIO_MCP_AUTH_CLIENT_SECRET`, the server requests a token at startup, logs
`Failed to obtain an OAuth2 token from <token endpoint> for client 'admin-client': HTTP 401
(unauthorized_client: ...)` to stderr, and exits with status 1 before any session opens. The secret
is not printed. If Keycloak is unreachable or answers 5xx, the server retries for at most 60 seconds
in total before it gives up.

## Step 5: HTTP mode needs a token issued for the MCP server

```bash
MCP=http://localhost:8082/mcp
H=(-H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream')
curl -s -o /dev/null -w '%{http_code}\n' "${H[@]}" "$MCP" -d "$INIT"
curl -s -o /dev/null -w '%{http_code}\n' "${H[@]}" -H "Authorization: Bearer $(token admin-client)" "$MCP" -d "$INIT"
curl -s -o /dev/null -w '%{http_code}\n' "${H[@]}" -H "Authorization: Bearer $(token developer-client)" "$MCP" -d "$INIT"
```

Expected: `401`, `401`, `200`. `admin-client` is `sr-admin`, but its token is not for the MCP
server: the server requires the `apicurio-mcp` audience (`QUARKUS_OIDC_TOKEN_AUDIENCE`), which the
bootstrap container added to `developer-client`. Compare:

```bash
for c in admin-client developer-client; do token $c | jq -Rc 'split(".")[1] | @base64d | fromjson | {azp, aud}'; done
```

Expected: `{"azp":"admin-client","aud":"account"}` and
`{"azp":"developer-client","aud":["apicurio-mcp","account"]}`.

MCP clients find where to log in at
<http://localhost:8082/.well-known/oauth-protected-resource>.

## Step 6: HTTP mode forwards the caller's token

Open a session as `developer-client` (`sr-developer`), then create an artifact:

```bash
mcp_session() {
  T=$(token "$1")
  SID=$(curl -s -D - -o /dev/null "${H[@]}" -H "Authorization: Bearer $T" "$MCP" -d "$INIT" \
    | awk -F': ' 'tolower($1)=="mcp-session-id"{print $2}' | tr -d '\r')
  mcp "$INITIALIZED" > /dev/null
}
mcp() { curl -s "${H[@]}" -H "Authorization: Bearer $T" -H "Mcp-Session-Id: $SID" "$MCP" -d "$1"; }

mcp_session developer-client
mcp '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_artifact","arguments":{"groupId":"guide","artifactId":"invoices","artifactType":"JSON"}}}' \
  | jq -r '.result.content[0].text | fromjson | .owner'
```

Expected: `developer-client`. The MCP server has no Registry credentials of its own in this mode; it
sent the caller's token.

Now as `readonly-client`, then as `no-role-client` (a valid token with no Registry role):

```bash
mcp_session readonly-client
mcp '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"create_artifact","arguments":{"groupId":"guide","artifactId":"denied","artifactType":"JSON"}}}' | jq -c .result
mcp_session no-role-client
mcp '{"jsonrpc":"2.0","id":1,"method":"tools/call","params":{"name":"list_groups","arguments":{"order":"asc","orderBy":"groupId"}}}' | jq -c .result
```

Expected: both `"isError": true`, with Registry's `403`.

## Step 7: Run the checks

```bash
./demo.sh
```

Expected: every line `ok`, then `All checks passed.`

## Clean up

```bash
docker compose down -v
```

## Presenter notes

- Start with Step 2: the whole story is "whose token reaches Registry".
- stdio = one identity per process (the service account in the config); HTTP = one identity per
  caller (their own token). Pick stdio for a personal assistant on a laptop, HTTP for a shared
  server where users must keep their own permissions.
- The 401 for `admin-client` in Step 5 surprises people: a powerful token for the wrong audience
  is still the wrong token.
- To show it in a real client, connect MCP Inspector (`npx @modelcontextprotocol/inspector`) to
  `http://localhost:8082/mcp` and log in through the `apicurio-mcp` client.

## Troubleshooting

- **`demo.sh` times out waiting:** `docker compose ps`; the Registry waits for Keycloak and the
  bootstrap container. `docker compose logs keycloak-bootstrap` should end with `Done.`
- **401 with a token you expect to work:** decode it (Step 5) and check that `aud` contains
  `apicurio-mcp`, and that `iss` is `http://localhost:<KEYCLOAK_PORT>/realms/registry`.
- **The stdio server prints nothing:** keep stdin open until the responses arrive (the `sleep` in
  Steps 3 and 4); the server exits when its stdin closes. Its logs go to stderr.
- **`/mcp` answers 503:** `APICURIO_MCP_HTTP_ENABLED` is not `true`.

## Using other ports

```bash
export KEYCLOAK_PORT=18080 REGISTRY_PORT=18081 MCP_PORT=18082 UI_PORT=18888
docker compose up -d && ./demo.sh
```

In the guide's commands, replace the ports in `TOKEN_URL`, `V3` and `MCP` too.
