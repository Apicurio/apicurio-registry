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

Both scenarios share:

- `common/keycloak.yaml` / `common/realm-import.json` - Keycloak, pre-loaded with a purpose-built
  `registry` realm defining a confidential `registry-api` client (service accounts enabled) used
  for OAuth2 client-credentials by both the Gatling simulation and the Kafka load generator.
- `common/perf-job.yaml` - the Kubernetes `Job` that runs the Gatling REST simulation and the
  Kafka load generator from inside the cluster, writing results to an `emptyDir` volume the
  workflow copies out via `kubectl cp` once the Job's exit-code marker file appears (see the
  comments in the file itself for why - `kubectl cp`/`exec` cannot target an already-completed
  pod). Has two placeholders substituted at deploy time: `${PERF_TEST_IMAGE}` and
  `${KAFKA_BOOTSTRAP_SERVERS}` (different per scenario - see each scenario's `deploy.sh`).

## Running a scenario locally

```
perf-tests/k8s/postgresql/deploy.sh
# or
perf-tests/k8s/kafkasql/deploy.sh
```

Then run the perf-test Job (each `deploy.sh` prints the exact command, with the correct
`KAFKA_BOOTSTRAP_SERVERS` for that scenario, once it finishes).

None of these manifests are meant to be used outside of CI/local testing; they intentionally
hard-code simple credentials/URLs for a throwaway, single-run cluster.
