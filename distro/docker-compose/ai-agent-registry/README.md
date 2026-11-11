# AI Agent Registry quick start

Starts Apicurio Registry with the experimental AI agent features enabled, the web console,
and a small demo catalog. **For local evaluation only:** nothing is authenticated, and the
database password is a fixed demo value.

```bash
docker compose -f distro/docker-compose/ai-agent-registry/docker-compose.yml up -d
```

| Service | URL |
|---|---|
| Web console (see the *Agents* tab) | http://localhost:8888 |
| REST API | http://localhost:8080/apis/registry/v3 |
| ARD catalog | http://localhost:8080/.well-known/ard.json |

The `seed` container loads the demo catalog into the `demo` group and exits. It creates only
what is missing, so restarting the stack changes nothing:

- `weather`: an `MCP_SERVER` that references `weather.get_forecast`
- `weather.get_forecast`: an `MCP_TOOL` with a `BACKWARD` compatibility rule
- `translator-agent` and `sentiment-agent`: `AGENT_CARD`s
- `support-reply`: a `PROMPT_TEMPLATE`

`test/smoke-test.sh` runs the quick start steps against the running stack; CI runs it on every
change to this directory. The full walkthrough is the "Agent registry quick start" page in the
docs (`docs/modules/ROOT/pages/getting-started/assembly-agent-registry-quickstart.adoc`).

Stop with `docker compose -f distro/docker-compose/ai-agent-registry/docker-compose.yml down`;
add `-v` to also delete the catalog.
