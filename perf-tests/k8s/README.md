# Kubernetes manifests for the perf-main workflow

These manifests deploy a topology that resembles a real installation, used by
`.github/workflows/perf-main.yaml` on merges to `main`:

- `postgresql.yaml` - PostgreSQL used as the registry's SQL storage backend.
- `kafka.yaml` - a single-node Kafka broker (KRaft mode, PLAINTEXT) used by the
  `KafkaLoadGenerator` client (see `../src/main/java/.../kafka/KafkaLoadGenerator.java`)
  to drive realistic serde-based produce/consume traffic against the registry.
- `keycloak.yaml` - Keycloak, pre-loaded with a purpose-built `registry` realm
  (see `realm-import.json`) defining a confidential `registry-api` client
  (service accounts enabled) that both the Gatling simulation and the Kafka
  load generator use for OAuth2 client-credentials.
- `registry-cr.yaml` - the `ApicurioRegistry3` CR, installed via the operator
  (already built/deployed earlier in the workflow), wiring the registry to
  PostgreSQL storage and Keycloak auth.
- `perf-job.yaml` - a Kubernetes `Job` that runs the Gatling REST simulation
  and the Kafka load generator from inside the cluster (so traffic reflects
  in-cluster network conditions, same as a real client application would
  see), writing the Gatling report to an `emptyDir` volume that the workflow
  copies out via `kubectl cp` once the Job completes.

None of these are meant to be used outside of CI; they intentionally hard-code
simple credentials/URLs for a throwaway, single-run cluster.
