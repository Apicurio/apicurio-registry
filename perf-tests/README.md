# Registry :: Performance Tests

Load/performance test suite for Apicurio Registry, run against topologies that resemble a real
installation rather than a bare dev instance: registry deployed via the **operator**, secured with
**Keycloak** (OAuth2 client-credentials), with a separate **Kafka** client using the Avro serde
generating realistic produce/consume traffic against the same registry instance.

Two storage-backend scenarios are provided, each with the storage backend put behind
[Toxiproxy](https://github.com/Shopify/toxiproxy) injecting realistic network latency (~15ms
&plusmn; 5ms, approximating a managed/remote datastore) rather than a same-node, near-zero-latency
connection - see `k8s/README.md` for details on each:

- `k8s/postgresql/` - SQL storage, PostgreSQL behind Toxiproxy
- `k8s/kafkasql/` - KafkaSQL storage, Kafka behind Toxiproxy

This module is **not** part of the default Maven build - it's only built when the `perf-tests`
profile is activated (`-Pperf-tests`), and it's driven end-to-end by
`.github/workflows/perf-main.yaml`, which runs **only on merges to `main`** (this is too slow/
expensive to run per-PR; see the workflow file for details) as a matrix job across both scenarios.

## Components

- `src/main/java/.../simulations/RegistryApiSimulation.java` - a [Gatling](https://gatling.io)
  Java-DSL simulation exercising the REST API with OAuth2 client-credentials against Keycloak,
  using a closed (concurrent-user) injection model and a 95%-read/5%-write traffic mix against a
  pre-seeded pool of artifacts by default. The read path is a single independent lookup by ID
  (matching how a Kafka producer/consumer's serde resolves a schema per message), not a chain of
  calls, so it's directly comparable to real per-message registry traffic - see the class Javadoc
  for all `PERF_*` env vars, including `PERF_PAUSE_MIN_MS`/`PERF_PAUSE_MAX_MS` for disabling the
  default inter-iteration pause when measuring maximum throughput rather than a paced traffic
  shape.
- `src/main/java/.../kafka/KafkaLoadGenerator.java` - a Kafka producer/consumer using
  `apicurio-registry-avro-serde-kafka`, so schema registration/lookup happens transparently as
  part of producing/consuming records, the same way a real Kafka application would use the
  registry (see `examples/kafka-serdes` for the non-perf-test equivalent).
- `src/main/java/.../PerfTestRunner.java` - the Job entry point: runs both of the above
  concurrently and exits non-zero if either fails.
- `k8s/` - manifests for both scenarios' throwaway, single-run cluster topologies (see
  `k8s/README.md`).
- `baseline.json` / `scripts/check-thresholds.py` - compares each run's Gatling stats against a
  committed baseline and posts a GitHub Actions job summary. This is informational (surfaces
  potential regressions for review), not a hard merge gate - see the script's docstring.

## Agent registry scale baseline

`AgentDiscoverySimulation` records discovery latency and MCP import throughput for the agent
registry graduation plan (#10294, #11093). The numbers are evidence, not thresholds. `perf-main`
runs it on both scenarios after every other run, so its seeded artifacts don't affect the runs
compared with a baseline. A failure is reported in the job summary without failing the workflow.

- Setup: 1000 Agent Cards and 1000 MCP tools (`PERF_AGENT_SEED`, `PERF_TOOL_SEED`).
- Discovery: `PERF_USERS` users for `PERF_DURATION_SECONDS` each send, in turn, agent discovery by
  skill, ARD search by text and MCP tool search by name.
- Import: then 10 imports (`PERF_IMPORT_RUNS`) of 500 tools (`PERF_IMPORT_TOOLS`) from an MCP
  server the perf-test container serves (`McpToolsStub`, reached through the `perf-mcp` Service),
  each into a new server artifact.

The registry runs with the agent features enabled (`registry-cr.yaml`), the Job and Service are in
`k8s/common/agent-perf-job.yaml`, and `scripts/summarize-agent-baseline.py` turns the Gatling
report into the job summary table. To run it locally against a registry with those features
enabled and `apicurio.mcp-import.allowed-hosts` covering the MCP server:

```
PERF_SIMULATION=agent-discovery REGISTRY_URL=http://localhost:8080/apis/registry/v3 \
  PERF_MCP_URL=http://127.0.0.1:8099/mcp java -jar perf-tests/target/*-jar-with-dependencies.jar
```

(for a local registry, also set `apicurio.outbound.loopback.enabled` and
`apicurio.outbound.insecure-http.enabled`, or their `apicurio.mcp-import.*` equivalents).

## Running locally

Deploy a scenario:

```
perf-tests/k8s/postgresql/deploy.sh
# or
perf-tests/k8s/kafkasql/deploy.sh
```

Build the fat jar:

```
./mvnw -Pperf-tests -pl perf-tests -am clean package -DskipTests
```

Then run it against the deployed scenario (each `deploy.sh` prints the exact `KAFKA_BOOTSTRAP_SERVERS`
value to use, since it differs per scenario):

```
REGISTRY_URL=http://localhost:8080/apis/registry/v3 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
java -Dgatling.resultsFolder=/tmp/gatling-results -jar target/apicurio-registry-perf-tests-*-jar-with-dependencies.jar
```

If `AUTH_TOKEN_ENDPOINT` / `AUTH_CLIENT_ID` / `AUTH_CLIENT_SECRET` are unset, requests are sent
without an `Authorization` header (fine for an anonymous-read/no-auth local instance).

For concurrency beyond a couple hundred clients, run the load generator outside the cluster
instead (see `k8s/README.md`'s `run-external-load.sh` section) - a single Gatling process's own
connection handling degrades well before the registry's actual capacity does at that point.

