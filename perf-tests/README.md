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
profile is activated (`-Dperf-tests`), and it's driven end-to-end by
`.github/workflows/perf-main.yaml`, which runs **only on merges to `main`** (this is too slow/
expensive to run per-PR; see the workflow file for details) as a matrix job across both scenarios.

## Components

- `src/main/java/.../simulations/RegistryApiSimulation.java` - a [Gatling](https://gatling.io)
  Java-DSL simulation exercising the REST API with OAuth2 client-credentials against Keycloak,
  using a closed (concurrent-user) injection model and a 95%-read/5%-write traffic mix against a
  pre-seeded pool of artifacts by default - see the class Javadoc for all `PERF_*` env vars.
- `src/main/java/.../kafka/KafkaLoadGenerator.java` - a Kafka producer/consumer using
  `apicurio-registry-avro-serde-kafka`, so schema registration/lookup happens transparently as
  part of producing/consuming records, the same way a real Kafka application would use the
  registry (see `examples/simple-avro` for the non-perf-test equivalent).
- `src/main/java/.../PerfTestRunner.java` - the Job entry point: runs both of the above
  concurrently and exits non-zero if either fails.
- `k8s/` - manifests for both scenarios' throwaway, single-run cluster topologies (see
  `k8s/README.md`).
- `baseline.json` / `scripts/check-thresholds.py` - compares each run's Gatling stats against a
  committed baseline and posts a GitHub Actions job summary. This is informational (surfaces
  potential regressions for review), not a hard merge gate - see the script's docstring.

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

