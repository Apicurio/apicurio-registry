# MCP server + Keycloak + Apicurio Registry

The [Apicurio Registry MCP server](../../mcp/) in front of a **Keycloak-secured** Registry with
role-based access, in both of its modes:

- **stdio + client credentials:** the MCP client starts the server as a local process (one per
  session) and speaks JSON-RPC on its stdin/stdout. The server logs in to Registry with a service
  account (`APICURIO_MCP_AUTH_*`), so every caller gets that account's roles.
- **HTTP + token forwarding:** a long-running `/mcp` endpoint. Every request needs a Keycloak
  access token issued for the MCP server (audience `apicurio-mcp`); the server forwards it unchanged
  to Registry, so each caller gets their own roles, as in the web console.

`demo.sh` speaks MCP to the server in both modes and checks what each caller may do.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Docker (or Podman) with Compose v2, `curl` and `jq`.

```bash
docker compose up -d
./demo.sh
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a
check fails). It is safe to run again. It checks that:

- the Registry refuses anonymous calls;
- **stdio, as `admin-client` (`sr-admin`):** `initialize` and `tools/list` work and list the
  registry tools; `get_server_info` reads the Registry; `create_group` and `create_artifact` create
  an artifact that the REST API then shows, owned by `admin-client`; `create_artifact` without
  `artifactType` is refused by the MCP server (a required argument: an empty artifact has no content
  to detect the type from);
- **stdio, as `readonly-client` (`sr-readonly`):** `list_artifacts` works, `create_artifact` is a
  tool error carrying Registry's 403, and nothing is created;
- **stdio, with a wrong client secret:** the session opens, but every tool call fails with
  `Failed to obtain OAuth2 token`;
- **HTTP:** no token, a malformed token, and a valid token for another audience (even `sr-admin`'s)
  are refused with 401; `/.well-known/oauth-protected-resource` names Keycloak;
- **HTTP, as `developer-client` (`sr-developer`):** the tool list is the same as over stdio, and
  `create_artifact` creates an artifact owned by `developer-client`, not by an MCP service account;
- **HTTP, as `readonly-client` and `no-role-client`:** reads work for the first, writes are refused
  with Registry's 403, and a token with no Registry role cannot even list groups.

The web console is at <http://localhost:8888> (log in through Keycloak).

Stop and remove everything with `docker compose down -v`.

## Services

| Service | URL | Purpose |
|---|---|---|
| `keycloak` | <http://localhost:8080> | OIDC provider, `registry` realm (admin console: `admin` / `admin`) |
| `keycloak-bootstrap` | | One-shot: adds the `apicurio-mcp` OAuth client and audience (see below) |
| `apicurio-registry` | <http://localhost:8081> | Secured REST API with role-based access |
| `apicurio-registry-ui` | <http://localhost:8888> | Web console (OIDC login) |
| `apicurio-registry-mcp` | <http://localhost:8082/mcp> | MCP server, HTTP mode |
| `apicurio-registry-mcp-stdio` | (stdin/stdout) | MCP server, stdio mode. Not started by `up`; run one per session with `docker compose run --rm -T apicurio-registry-mcp-stdio` |

Keycloak imports the shared test realm from
[`utils/tests/src/main/resources/realm.json`](../../utils/tests/src/main/resources/realm.json). Its
service accounts (every secret is `test1`) are `admin-client` (`sr-admin`), `developer-client`
(`sr-developer`), `readonly-client` (`sr-readonly`) and `no-role-client` (no Registry role). The
bootstrap container then adds:

- the **`apicurio-mcp`** public OAuth client (authorization code + PKCE), for HTTP-mode MCP clients
  such as Cursor or MCP Inspector;
- an `apicurio-mcp` audience on `developer-client`, `readonly-client` and `no-role-client`, so
  `demo.sh` can get tokens for the HTTP endpoint without a browser. `admin-client` is left without
  it, to show that a token for another audience is refused.

## Files

| File | Purpose |
|---|---|
| `docker-compose.yml` | The stack, with both MCP modes |
| `demo.sh` | The checks above |
| `build.sh` | Builds the MCP server image from this checkout, to try local changes |
| `bootstrap-apicurio-mcp-client.sh`, `apicurio-mcp-client.json` | The Keycloak additions above |
| `stdio.example.json`, `http.example.json` | MCP client configuration for each mode |
| `ci.env` | CI settings |

## Client configuration

- **stdio:** copy [`stdio.example.json`](stdio.example.json) into your MCP client's configuration.
  It runs the server image with `docker run` and reaches Keycloak and Registry on the host.
  From this directory, `docker compose run --rm -T apicurio-registry-mcp-stdio` does the same inside
  the Compose network.
- **HTTP:** copy [`http.example.json`](http.example.json) into your MCP client's configuration, or
  point the client at `http://localhost:8082/mcp` and complete OAuth against the `apicurio-mcp`
  client in Keycloak.

## Images and local changes

The stack selects its images with `REGISTRY_IMAGE`, `REGISTRY_UI_IMAGE` and `MCP_SERVER_IMAGE`
(`latest-snapshot` by default); CI sets them to the images built for the pull request. To try local
MCP server changes:

```bash
./build.sh    # builds the mcp module and tags apicurio/apicurio-registry-mcp-server:local
MCP_SERVER_IMAGE=apicurio/apicurio-registry-mcp-server:local docker compose up -d
MCP_SERVER_IMAGE=apicurio/apicurio-registry-mcp-server:local ./demo.sh
```

`demo.sh` runs the stdio server through Compose, so it uses the same `MCP_SERVER_IMAGE`.

## Using other ports

Set `KEYCLOAK_PORT`, `REGISTRY_PORT`, `MCP_PORT` and `UI_PORT` for both `docker compose up` and
`./demo.sh`. Keycloak puts `localhost:$KEYCLOAK_PORT` in its tokens' issuer, so Registry and the MCP
server accept tokens fetched from the host or from inside the network alike.

## Notes

- This example is for **local development only**: Keycloak `start-dev`, known client secrets,
  in-memory Registry storage, `QUARKUS_OIDC_TLS_VERIFICATION=none`.
- The HTTP transport is in the image, but `/mcp` answers 503 unless `APICURIO_MCP_HTTP_ENABLED=true`,
  and the server refuses to start in HTTP mode without OIDC.
- Keep `QUARKUS_PROFILE=prod` on the HTTP-mode server: the image's default profile is `dev`.
- In stdio mode the server only asks Keycloak for a token on the first tool call, so bad
  credentials show up as tool errors, not as a failed start.
- The MCP service sets `QUARKUS_HTTP_CORS_ORIGINS=http://localhost:6274` for local browser tools
  (MCP Inspector). **Do not use localhost CORS origins or `*` in production.**
- [`agent-registry`](../agent-registry/) also runs the MCP server in HTTP mode, for its
  `discover_agents` tool and per-user agent visibility. This example covers the MCP server itself:
  both modes, the general tools, and how authentication and roles reach Registry.

## Learn more

- *MCP server integration* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-mcp-server-integration.adoc)):
  every property, HTTP/OAuth setup and client examples
- [ADR-0014: agent registry](../../adr/0014-agent-registry.md) and
  [ADR-0009: authentication](../../adr/0009-authentication.md)
- [`authentication`](../authentication/): every way to authenticate to Registry
