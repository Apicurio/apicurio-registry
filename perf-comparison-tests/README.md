# Product-neutral Schema Registry Performance Comparison

This opt-in benchmark compares Apicurio Registry, Confluent Schema Registry, Karapace, and
Redpanda Schema Registry through the Confluent-compatible REST API shared by all four products.
It is separate from `perf-tests`, which measures Apicurio-specific capacity and regressions.

## What Is Comparable

Each run uses identical client requests, dataset generation, warm-up, measurement, client-facing
TLS, and HTTP Basic authentication. Functional conformance checks run before timed traffic.

Supported operations:

- `READ_ID`: `GET /schemas/ids/{id}`
- `READ_VERSION`: `GET /subjects/{subject}/versions/{version}`
- `REGISTER_NEW_SUBJECT`: register one unique schema under one new subject per request
- `REGISTER_NEW_VERSION`: register compatible unique versions under seeded subjects
- `REGISTER_IDEMPOTENT`: repeatedly register an already registered schema
- `COMPATIBILITY`: check a compatible schema against the latest subject version

The benchmark uses distinct warm-up and measured Gatling scenarios. Reports and aggregation only
use requests whose name begins with `Measured`, so warm-up cannot silently dilute results.

## Resource And Durability Profile

The checked-in core profile uses one persistent metadata broker and one registry instance with
total limits of 4 CPU / 4Gi memory for Apicurio, Confluent, and Karapace: limits of 2 CPU / 2Gi
for Kafka and 2 CPU / 2Gi for the standalone registry (each requests 1 CPU / 1Gi). Redpanda
embeds Schema Registry in the broker and therefore receives the same combined 4 CPU / 4Gi limit
and requests 2 CPU / 2Gi in one process.

The identical TLS/Basic-auth proxy has a separate 2 CPU / 256Mi limit and is not counted in the
SUT budget. Its resource samples are captured with every run; discard any result where it
saturates. The manual GitHub workflow is intended for development/trend evidence because the SUT,
proxy, Minikube, and load generator share one runner; claim-grade runs require separate hardware.

Metadata is stored on persistent volumes with one metadata-log replica, and timed writes require a
successful synchronous API response. This is a common single-node durability class, not a claim
that every product has identical broker cache, flush, or fsync semantics. RF1 results must not be
presented as production-HA results. A production claim requires a dedicated multi-node profile
with RF3/minISR2, explicitly verified acknowledgement/flush semantics, and separate load-generator
hardware.

Every product is exposed through the same Nginx proxy, which terminates TLS and enforces the same
HTTP Basic credentials. This normalizes client-facing security overhead; it does not claim that
each product's proprietary internal authorization implementation is equivalent. Product-native
OIDC/RBAC must be a separate comparison profile.

The proxy uses a random high-entropy credential generated for each ephemeral run and a low-cost
SHA verifier. Password stretching is intentionally avoided because Nginx verifies Basic auth on
every request and a slow password hash makes the shared proxy, rather than the registry, the
benchmark bottleneck. The credential is transported only over TLS and is not persisted in result
artifacts. The proxy also uses a bounded upstream keepalive pool; without it, per-request upstream
connection churn can exhaust the proxy's ephemeral ports before a registry reaches capacity.

Product images are pinned to version tags. Every result records runtime digests for the registry,
metadata broker, and security proxy:

- Confluent Schema Registry `8.3.1`
- Karapace `6.2.2`
- Redpanda `v26.2.2`
- Apicurio image supplied by `APICURIO_IMAGE`

## Running Locally

Prerequisites: Java 21, Maven, `kubectl`, Minikube with metrics-server enabled, OpenSSL, keytool,
and a cluster with enough capacity for the 4 CPU / 4Gi SUT budget plus the proxy and Kubernetes
system services.

```bash
./mvnw -Pperf-comparison-tests -pl perf-comparison-tests clean package -DskipTests

export APICURIO_IMAGE=quay.io/apicurio/apicurio-registry:<version>
export PERF_USERS=100
export PERF_SEED_SCHEMAS=1000
export PERF_WARMUP_SECONDS=60
export PERF_DURATION_SECONDS=180

./perf-comparison-tests/run-comparison.sh READ_ID 5 results/read-id
```

Use `run-product.sh` for a single product during development:

```bash
./perf-comparison-tests/k8s/run-product.sh apicurio READ_ID results/apicurio
```

`run-comparison.sh` randomizes product order for every repetition. Five repetitions are the
minimum for publishing a comparison. The aggregation script emits JSON, CSV, and Markdown with
median successful measured-window RPS, p99, p99.9, failure rate, and bootstrap confidence
intervals. Measured-window RPS is the number of successful requests in the measured scenario
divided by its configured duration. JSON and CSV output also retain Gatling's simulation-wide
mean as `gatlingSimulationWindowRps`; that diagnostic includes warm-up and in-flight request drain
and must not be used as measured-phase throughput.
With only five repetitions, bootstrap confidence intervals are indicative and discrete; use more
runs for external claims.

Each run also captures image digests, exact parameters, node capacity, host/JDK identity,
Kubernetes resources, pod logs/events, and five-second container resource samples. A result is
invalid if the load generator, shared security proxy, metadata broker, or cluster host saturates
before the registry under test.

## Interpreting Results

Do not call a result product-neutral unless all of these match:

- operation and API semantics
- image versions/digests
- CPU and memory envelope
- persistence and durability profile
- TLS and authentication profile
- dataset cardinality/schema type/payload distribution
- warm-up and measurement durations
- load-generator hardware and headroom (the manual GitHub workflow shares one host with Minikube
  and is not suitable for published claim-grade results)

This initial harness provides uniform Avro schemas and closed-concurrency operations. Before a
public "fastest" claim, extend it with open arrival-rate/SLO sweeps, 10K/100K/1M datasets,
uniform and Zipfian access, cold/warm cache runs, JSON Schema, Protobuf, references, RF3, native
security profiles, dedicated load-generator hosts, and at least five runs with confidence
intervals. Track that work under #9863 and parent epic #9858.
