# Registry :: Performance Tests

Load/performance test suite for Apicurio Registry, run against a topology that resembles a real
installation rather than a bare dev instance: registry deployed via the **operator**, secured with
**Keycloak** (OAuth2 client-credentials), backed by **PostgreSQL**, with a separate **Kafka**
client using the Avro serde generating realistic produce/consume traffic against the same
registry instance.

This module is **not** part of the default Maven build - it's only built when the `perf-tests`
profile is activated (`-Dperf-tests`), and it's driven end-to-end by
`.github/workflows/perf-main.yaml`, which runs **only on merges to `main`** (this is too slow/
expensive to run per-PR; see the workflow file for details).

## Components

- `src/main/java/.../simulations/RegistryApiSimulation.java` - a [Gatling](https://gatling.io)
  Java-DSL simulation exercising the REST API (create/get/search artifacts) with OAuth2
  client-credentials against Keycloak.
- `src/main/java/.../kafka/KafkaLoadGenerator.java` - a Kafka producer/consumer using
  `apicurio-registry-avro-serde-kafka`, so schema registration/lookup happens transparently as
  part of producing/consuming records, the same way a real Kafka application would use the
  registry (see `examples/simple-avro` for the non-perf-test equivalent).
- `src/main/java/.../PerfTestRunner.java` - the Job entry point: runs both of the above
  concurrently and exits non-zero if either fails.
- `k8s/` - manifests for the throwaway, single-run cluster topology used by CI (see
  `k8s/README.md`).
- `baseline.json` / `scripts/check-thresholds.py` - compares each run's Gatling stats against a
  committed baseline and posts a GitHub Actions job summary. This is informational (surfaces
  potential regressions for review), not a hard merge gate - see the script's docstring.

## Running locally

Build the fat jar:

```
./mvnw -Pperf-tests -pl perf-tests -am clean package -DskipTests
```

Then, against any running registry instance (adjust env vars for Keycloak/Kafka as needed - see
each class's Javadoc for the full list):

```
REGISTRY_URL=http://localhost:8080/apis/registry/v3 \
KAFKA_BOOTSTRAP_SERVERS=localhost:9092 \
java -Dgatling.resultsFolder=/tmp/gatling-results -jar target/apicurio-registry-perf-tests-*-jar-with-dependencies.jar
```

If `AUTH_TOKEN_ENDPOINT` / `AUTH_CLIENT_ID` / `AUTH_CLIENT_SECRET` are unset, requests are sent
without an `Authorization` header (fine for an anonymous-read/no-auth local instance).
