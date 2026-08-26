# Production-like test topologies: PostgreSQL and KafkaSQL, with injected network latency

Adds two named, verified scenarios under `perf-tests/k8s/`, replacing the single flat manifest
set. Both add ~15ms +/- 5ms latency (via [Toxiproxy](https://github.com/Shopify/toxiproxy)) in
front of the storage backend, to approximate a real deployment (managed/remote DB or Kafka
cluster on a different node/AZ) instead of the original same-node Minikube setup, which had
effectively zero network latency and therefore couldn't show any benefit a different threading
model might have from genuine I/O wait.

## Layout

```
perf-tests/k8s/
  common/            keycloak.yaml, realm-import.json, perf-job.yaml (shared by both scenarios)
  postgresql/         postgresql.yaml, kafka.yaml (direct - serde traffic only), toxiproxy.yaml,
                       registry-cr.yaml, deploy.sh
  kafkasql/           kafka.yaml (advertised listener via toxiproxy), toxiproxy.yaml,
                       registry-cr.yaml, deploy.sh
```

## Two real problems found and fixed while building this

1. **The official `ghcr.io/shopify/toxiproxy` image has no shell** (`/bin/sh: no such file or
   directory`) - my first attempt tried to start `toxiproxy-server` in the background and
   configure it via `toxiproxy-cli` in the same container's startup script, which doesn't work on
   that image. Fixed by running Toxiproxy with its default entrypoint and adding a small
   `curlimages/curl` sidecar container that waits for the admin API, configures the proxy and
   latency toxic via plain HTTP calls, then sleeps.

2. **A plain TCP proxy in front of Kafka does not work transparently.** Kafka clients use the
   broker's *advertised listener* address (learned from the initial metadata response) for every
   subsequent connection, not the address they originally dialed - so proxying Kafka requires the
   broker itself to advertise the proxy's address (`kafkasql/kafka.yaml`:
   `KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://toxiproxy.default.svc:9094`), unlike PostgreSQL, where
   a plain TCP proxy works with no server-side awareness needed.

## Verification performed (both scenarios deployed and exercised against Minikube)

**PostgreSQL scenario** (`postgresql/deploy.sh`):
- Toxiproxy sidecar confirmed configured: `GET /proxies` returned the `postgres` proxy with its
  `postgres_latency` toxic (15ms latency, 5ms jitter) attached.
- Registry app started successfully against `jdbc:postgresql://toxiproxy.default.svc:5433/registry`
  (DB schema migration completed through the proxy).
- Functional smoke test: `GET /system/info` -> 200, `POST /groups/smoke/artifacts` -> 200.

**KafkaSQL scenario** (`kafkasql/deploy.sh`):
- Registry app logs confirm the advertised-listener fix is working:
  `Discovered group coordinator toxiproxy.default.svc:9094` (not the real broker address) and
  `KafkaSQL storage bootstrapped in 14062 ms` - i.e. all KafkaSQL storage traffic, not just the
  initial bootstrap connection, is routed through the latency-injected proxy.
- Functional smoke test: `GET /system/info` -> 200, `POST /groups/smoke/artifacts` -> 200.

## CI integration

`.github/workflows/perf-main.yaml` was converted from a single job to a
`strategy: matrix: [postgresql, kafkasql]` job, running both scenarios independently (each gets
its own results artifact: `perf-test-results-<storage>-<sha>`). `deploy.sh` in each scenario
directory centralizes the multi-step deploy sequence so the workflow step is a one-liner, and can
also be run identically by hand for local testing/reproduction.

## Not yet done

This PR only builds and validates the topologies themselves (functional smoke tests, both
scenarios deploy and serve requests correctly with latency confirmed injected). It does not yet
include a full Gatling/Kafka-load-generator run against either scenario, or a virtual-threads
comparison under these more realistic conditions - that's the natural next step, to properly test
the `feat/virtual-threads` hypothesis under conditions with genuine I/O wait, which the original
same-node setup couldn't provide.
