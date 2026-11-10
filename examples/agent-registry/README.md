# AI agent registry demo

A complete local demo of Apicurio Registry as a registry for AI agents and tools, together with the
[Quarkus LangChain4j](https://docs.quarkiverse.io/quarkus-langchain4j/dev/index.html) A2A and MCP
Registry extensions:

- **Agent and tool definitions as versioned artifacts:** A2A Agent Cards, MCP tools, MCP servers,
  prompt templates and model schemas, validated, with references between them.
- **Governance:** compatibility rules reject a tool or agent change that would break its callers.
- **Discovery:** the Registry's own Agent Card, agent and MCP tool search, the AI Catalog, and
  Agentic Resource Discovery (ARD).
- **MCP:** the MCP Registry API, importing tools from a running MCP server, and the Registry MCP
  server for AI assistants.
- **Access:** who discovers which agents, from agent visibility and per-resource access rules.
- **Applications:** Quarkus agents that publish their own Agent Cards, discover each other and an
  MCP server through the Registry, and run a weather → summary → French translation workflow.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It goes through every feature in the
API, the web console and the Quarkus applications, with commands, expected output and screenshots,
and works as a script for a team demo.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq`, and [Ollama](https://ollama.com/) running
on the host with the `qwen2.5:7b` model (`ollama pull qwen2.5:7b`). Only the steps that call the
model need Ollama. The containers use about 3 GB of memory, and the model needs its own.

```bash
docker compose up -d     # the first run builds the Quarkus applications, which takes several minutes
./demo.sh                # creates the demo data, then checks every feature
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a
check fails). Run it with `SKIP_LLM=1` to skip the steps that call the model.

Then open:

- the open Registry's web console at <http://localhost:8889>
- the workflow dashboard at <http://localhost:11020>
- the secured Registry's web console at <http://localhost:8888>, as `user` / `user` or `developer` / `developer`

To explore the API yourself, load the helper commands: `source ./demo-env.sh` (see the guide).

Stop and remove everything with `docker compose down`. The Registries keep their data in memory, so
it is gone after `down`; run `./demo.sh` again after the next `up`.

## Services

| Service | URL | Purpose |
|---|---|---|
| `registry` | <http://localhost:8180> | Registry with every agent feature, **no authentication**. The Quarkus applications use it. |
| `registry-ui` | <http://localhost:8889> | Web console for `registry` |
| `registry-secured` | <http://localhost:8081> | The same Registry behind Keycloak, with role-based access and access rules |
| `registry-secured-ui` | <http://localhost:8888> | Web console for `registry-secured` |
| `registry-mcp` | <http://localhost:8082/mcp> | The Registry MCP server over HTTP, in front of `registry-secured`. Users log in through Keycloak. |
| `keycloak` | <http://localhost:8080> | Users and roles (admin console: `admin` / `admin`) |
| `weather-server` | (internal) | A Quarkus MCP server with one tool, `getWeather`, returning fictional data |
| `summarizer`, `translator` | (internal) | Quarkus A2A agents that call Ollama, and publish their Agent Cards with the A2A Registry extension |
| `orchestrator` | <http://localhost:11020> | The workflow and its dashboard. It discovers the agents and the weather server through the Registry. |

Every port is bound to `localhost`.

### Why two Registries

The Quarkus LangChain4j Registry extensions cannot authenticate to a Registry in any released
version, so the applications use the open Registry. Registry authentication for both extensions
([quarkiverse/quarkus-langchain4j#3145](https://github.com/quarkiverse/quarkus-langchain4j/pull/3145))
and credentials for discovered A2A agents
([#3157](https://github.com/quarkiverse/quarkus-langchain4j/pull/3157)) are in review. Once they are
released, the applications will use the secured Registry. The features that depend on who is asking (agent visibility, access rules, and the
Registry MCP server's token forwarding) are shown on the secured one.

## Users

Users of the secured Registry, from the shared example realm. Passwords equal the user name, except
`developer2` (password `developer`).

| User | Role | Access in [`authz/access.rules`](authz/access.rules) |
|---|---|---|
| `admin` | sr-admin | Everything: administrators bypass the rules |
| `developer` | sr-developer | Write the `agents` group; read `team-b` |
| `developer2` | sr-developer | Read `agents` only |
| `user` | sr-readonly | Read `agents` only |
| _anonymous_ | none | Read `agents/public-agent` only |

## Files

| File | Purpose |
|---|---|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough and presenter notes |
| [`docker-compose.yml`](docker-compose.yml) | All the services; each Registry's configuration is in its `environment` |
| [`demo.sh`](demo.sh) | Creates the demo data and checks every feature |
| [`demo-env.sh`](demo-env.sh) | Shell helpers to call the Registries and the MCP server as any user |
| [`content/`](content) | The Agent Cards, tools, servers, prompt and OpenAPI document the demo publishes |
| [`authz/access.rules`](authz/access.rules) | Access rules of the secured Registry; edits apply within seconds |
| [`apps/Dockerfile`](apps/Dockerfile) | Builds the Quarkus applications from the Quarkus LangChain4j 1.15.0 sample |
| [`keycloak/`](keycloak) | Adds the `apicurio-mcp` client, used by the Registry MCP server, to the realm |
| [`capture-screenshots.cjs`](capture-screenshots.cjs) | Refreshes the guide's screenshots in [`images/`](images) |

## Versions

- **Registry, web console and MCP server:** the `latest-snapshot` images. Several features shown here
  (MCP tool import, the MCP Registry API, references between agent types, Agent Cards from OpenAPI)
  are not in a released version yet. Override the images with `REGISTRY_IMAGE`, `REGISTRY_UI_IMAGE` and
  `MCP_SERVER_IMAGE`.
- **Quarkus LangChain4j:** the sample and extensions from release 1.15.0. Change `QL4J_VERSION` in
  `apps/Dockerfile` to use another release.

## Notes

- **Ollama elsewhere:** set `OLLAMA_URL` (default `http://host.docker.internal:11434`) and
  `OLLAMA_MODEL` before `docker compose up`.
- **Registry restarted on its own:** the agents publish their Agent Cards only when they start. After
  recreating `registry`, run `docker compose restart summarizer translator orchestrator`.
- **Ports in use:** the demo uses 8080, 8081, 8082, 8180, 8888, 8889 and 11020.
