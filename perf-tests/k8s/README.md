# Kubernetes manifests for the perf-main workflow

Two storage-backend scenarios are provided, each intended to be as close as reasonably possible
to a real deployment rather than a bare-bones local dev setup:

- `postgresql/` - registry backed by PostgreSQL, with Toxiproxy injecting ~15ms +/- 5ms latency
  between the registry and the database (see `postgresql/toxiproxy.yaml`), approximating a
  managed DB service on a different node/AZ rather than a same-node, near-zero-latency
  connection. Also runs a separate, direct (non-latency-injected) Kafka broker used only by the
  Kafka load generator's serde traffic (see `../src/main/java/.../kafka/KafkaLoadGenerator.java`)
  - this scenario is about SQL storage, so the Kafka client path here represents an external
    application talking to the registry, not the registry's own storage backend.
- `kafkasql/` - registry backed by KafkaSQL, with Toxiproxy injecting the same ~15ms +/- 5ms
  latency in front of Kafka (see `kafkasql/toxiproxy.yaml` and the comments in
  `kafkasql/kafka.yaml` on why the broker's *advertised listener* - not just the bootstrap
  address - has to point at the proxy for this to actually apply to KafkaSQL's traffic). In this
  scenario there is only one Kafka broker, used both as the registry's storage backend and by the
  Kafka load generator - both paths are latency-injected identically.

Both scenarios' `registry-cr.yaml` set a 2 CPU / 2Gi memory limit (up from the {operator}'s
default 1 CPU / 1Gi) and `APICURIO_DATASOURCE_JDBC_MAX_SIZE=400` (up from the default 100) - both
found necessary for stable, representative results at a few hundred concurrent clients: 1Gi caused
GC-pressure-driven instability under sustained load (confirmed via `jcmd <pid> GC.heap_info`), and
the default JDBC pool size bottlenecked throughput well below what the database could otherwise
sustain. See the capacity planning and sizing guide (`docs/modules/ROOT/pages/getting-started/
assembly-registry-sizing-guide.adoc`) for the full findings.

Both scenarios share:

- `common/keycloak.yaml` / `common/realm-import.json` - Keycloak, pre-loaded with a purpose-built
  `registry` realm defining a confidential `registry-api` client (service accounts enabled) used
  for OAuth2 client-credentials by both the Gatling simulation and the Kafka load generator.
- `common/perf-job.yaml` - the Kubernetes `Job` that runs the Gatling REST simulation and the
  Kafka load generator from inside the cluster, writing results to an `emptyDir` volume the
  workflow copies out via `kubectl cp` once the Job's exit-code marker file appears (see the
  comments in the file itself for why - `kubectl cp`/`exec` cannot target an already-completed
  pod). Has two placeholders substituted at deploy time: `${PERF_TEST_IMAGE}` and
  `${KAFKA_BOOTSTRAP_SERVERS}` (different per scenario - see each scenario's `deploy.sh`). Good
  for quick, modest-concurrency runs (roughly up to 200-250 concurrent clients); see
  `common/run-external-load.sh` below for anything higher.
- `common/run-external-load.sh` - runs the Gatling REST simulation as a plain process *outside*
  the cluster instead of as an in-cluster Job, talking to the registry over a NodePort Service.
  Use this instead of `perf-job.yaml` whenever you want more than a couple hundred concurrent
  clients: a single Gatling process's own connection handling degrades above roughly 250-300
  concurrent connections (TCP `TIME_WAIT` accumulation, eventually exhausting ephemeral ports) -
  a client-tooling limit, not a registry one - and on a resource-constrained cluster (e.g. a local
  Minikube VM), the load generator competing with the registry/database/Kafka/Keycloak for the
  same node's CPU can itself become the bottleneck, masking the registry's real capacity. Running
  the load generator as a separate process against a NodePort-exposed registry avoids both. See
  the comments at the top of the script for the full rationale and usage.

## Running a scenario locally

```
perf-tests/k8s/postgresql/deploy.sh
# or
perf-tests/k8s/kafkasql/deploy.sh
```

Then either run the perf-test Job (each `deploy.sh` prints the exact command, with the correct
`KAFKA_BOOTSTRAP_SERVERS` for that scenario, once it finishes) for a quick, modest-concurrency
check, or run the load generator externally for higher concurrency:

```
perf-tests/k8s/common/run-external-load.sh perf-tests/target/apicurio-registry-perf-tests-*-jar-with-dependencies.jar [PERF_USERS] [PERF_DURATION_SECONDS] [PERF_WRITE_RATIO]
```

(Build the jar first with `./mvnw -Dperf-tests package -pl perf-tests -am -DskipTests`.)

None of these manifests are meant to be used outside of CI/local testing; they intentionally
hard-code simple credentials/URLs for a throwaway, single-run cluster.
