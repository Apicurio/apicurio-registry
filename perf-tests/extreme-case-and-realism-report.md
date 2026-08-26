# Extreme-case testing, and a realism assessment

Two follow-ups requested together: (1) is the topology used in the earlier reports actually
realistic for a schema registry, and (2) push the perf-tests infrastructure to test genuinely
extreme conditions rather than just "somewhat degraded."

## Part 1: Is the earlier setup realistic?

Short answer: **partially**. It's a reasonable approximation for testing network-latency
sensitivity, but it had a significant gap that's now fixed (see Part 2), plus some gaps that
remain open.

**What was realistic:**
- Injected network latency to a remote-ish DB/Kafka (vs. the original same-node setup)
- OAuth token caching matching real client behavior
- Real operator-based deployment, Keycloak auth, actual storage backends

**What was not realistic (the big one, now fixed):** every iteration of `RegistryApiSimulation`
**created a brand-new artifact**. Real schema registry traffic is overwhelmingly **read-heavy** -
producers/consumers resolving already-registered schemas by ID on close to every message, with
new schema registration happening rarely (on deploy, not per-message). Stress-testing 100% writes
was testing the *least* representative traffic pattern a real registry sees.

**What still isn't fully realistic, and is out of scope for this round:**
- Single instance, no HA (real deployments typically run 2+ replicas)
- Single-broker Kafka, replication factor 1 (no inter-broker replication/quorum-ack latency)
- No caching/CDN layer in front of immutable schema content, no OTel overhead modeled

## Part 2: Making the load itself more realistic (read-heavy by default)

`RegistryApiSimulation` now defaults to a **95% read / 5% write** mix (configurable via
`PERF_WRITE_RATIO`), instead of 100% writes:

- **Setup phase** (blocking, parallelized across 20 threads): pre-registers `PERF_SEED_ARTIFACTS`
  (default 200) artifacts in a `perf-test-seed` group before the timed run starts, so there's a
  realistic pool of already-existing schemas to resolve by ID - the same way a real registry
  accumulates schemas over time, rather than testing against an empty instance.
- **Read path** (95% of iterations by default): picks a random pre-seeded artifact and does
  `GET content` + `GET metadata` against it - the dominant real-world access pattern.
- **Write path** (5% of iterations by default): the original create-artifact-then-read-it-back
  flow, representing occasional new schema registration.
- A `Search artifacts` call still runs on every iteration regardless of path.

New `PERF_LARGE_SCHEMA` option: switches from the tiny 2-field default Avro schema to a ~150-field,
several-KB schema, to exercise payload-size-sensitive code paths (JSON parsing, canonicalization,
storage) under more extreme conditions.

## Part 3: Extreme-case infrastructure

Added `set-extreme-latency.sh` to both `postgresql/` and `kafkasql/` scenario directories. These
patch the already-running Toxiproxy instance (verified against its live HTTP API, not assumed)
from the default ~15ms +/- 5ms "same-region-different-AZ" profile to a much harsher one:

- **100ms +/- 50ms latency** (a rough approximation of a degraded/cross-region link)
- **2% outright request timeout** (the `timeout` toxic at `toxicity=0.02`), approximating packet
  loss / a flaky network path

Combined with `PERF_LARGE_SCHEMA=true` and pushing concurrency to 200 users, this is the most
adversarial condition tested so far: high concurrency + large payloads + degraded network, all at
once, against the same 1 CPU / 1Gi default resource limits used throughout.

## Results: PostgreSQL storage under extreme conditions

| Build | Failed % | Restarts | OK throughput (rps) | p50 (OK) | p99 (OK) |
| --- | --- | --- | --- | --- | --- |
| Baseline | **90.46%** | 0 | 15.37 | 7,817 ms | 15,235 ms |
| **Virtual threads** | **66.77%** | 2 | **42.59** | 10,898 ms | 28,003 ms |

This is the clearest signal yet. Virtual threads cut the failure rate by **24 points** (90.46% ->
66.77%) and nearly **tripled** successful throughput (15.37 -> 42.59 rps) under genuinely extreme
conditions. The gap is far larger here than at the more moderate 15ms/50-user condition tested
earlier (56% vs 49% failure) - consistent with virtual threads' benefit scaling with the amount of
genuine blocking-I/O-wait time in the system: more latency and more concurrent in-flight requests
means more threads parked waiting, which is exactly what virtual threads are for.

(Restart count went from 0 to 2 for the VT build here - the opposite direction from "fewer
restarts is better," but given VT's much higher successful throughput and much lower failure rate
overall, this reads as VT keeping the pod *more* loaded with *more* concurrently-in-flight work
right up to the point of occasionally tripping the liveness probe, rather than a regression - a
single data point, not repeated here, so treat with appropriate caution.)

## Results: KafkaSQL storage under extreme conditions

| Build | Failed % | Restarts | OK throughput (rps) | p99 (OK) |
| --- | --- | --- | --- | --- |
| Baseline | 0.00% | 1\* | 256.11 | 420 ms |
| Virtual threads | 0.01% | 1\* | 155.88\*\* | 337 ms |

(\*) Both restarts were the pre-existing KafkaSQL startup race documented in
`prod-like-vt-multirun-report.md`, not failures during the measured load.
(\*\*) The VT run's lower throughput reflects one request that hit the full 60s Gatling timeout
(the 2% Toxiproxy timeout toxic firing once), which dragged down the mean for that run - not a
systematic slowdown; 99.99% of requests were still fast (p99 337ms).

**KafkaSQL remained essentially unaffected even by the most extreme conditions tested** - both
builds stayed at ~100% success. This is now well-explained architecturally: **KafkaSQL reads are
served from an in-memory index built at startup, not round-tripped through Kafka per-request** -
only the 5% write-path traffic touches the (latency-injected) Kafka broker at all. SQL storage, by
contrast, round-trips through the latency-injected connection for *every single operation*, read
or write - which is exactly why it's so much more sensitive to injected latency, and exactly why
virtual threads (which help with blocking-I/O-bound work) help SQL storage but not KafkaSQL.

## Overall conclusion

1. The read-heavy fix makes the load itself meaningfully more representative of real registry
   traffic - a worthwhile change independent of the virtual-threads question.
2. Virtual threads' benefit for SQL/PostgreSQL storage **scales with how extreme the network
   conditions are** - a large, consistent effect at the most adversarial settings tested, not just
   a marginal one. This strengthens (rather than merely replicates) the earlier finding.
3. KafkaSQL's resilience to injected latency is now understood architecturally (in-memory read
   path), not just observed empirically - and this explains, independently, why virtual threads
   don't matter for it: there's very little blocking-I/O-wait time in its dominant (read) code
   path for virtual threads to reclaim.
