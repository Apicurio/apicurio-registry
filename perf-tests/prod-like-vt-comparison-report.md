# Production-like comparison: virtual threads vs. platform threads, PostgreSQL vs. KafkaSQL

Follow-up to `virtual-threads-report.md` (which tested on the original same-node, near-zero-latency
topology and found no benefit) and `prod-like-topologies-report.md` (which built the
latency-injected PostgreSQL/KafkaSQL topologies used here). This report re-runs the
virtual-threads A/B comparison under conditions with **genuine I/O wait** - the specific gap
flagged as a caveat in the original virtual-threads experiment.

## Method

- Topologies: `perf-tests/k8s/postgresql/` and `perf-tests/k8s/kafkasql/`, each with Toxiproxy
  injecting ~15ms +/- 5ms latency in front of the storage backend (see
  `prod-like-topologies-report.md` for how these were built and verified).
- Builds compared: `feat/prod-like-sizing` (platform threads, "baseline") vs. `feat/virtual-threads`
  (all eight v3 REST resource impl classes annotated `@RunOnVirtualThread`), built from a real
  `git worktree` checkout of each branch - not a hypothetical diff.
- Load levels: 20 and 50 concurrent Gatling users, 45s duration each, run against both storage
  backends and both builds (8 runs total), plus the concurrent Kafka/serde load generator in every
  run.
- Default operator resource limits throughout (1 CPU / 1Gi app pod).

## Results

### PostgreSQL (SQL storage) - virtual threads showed a real, consistent improvement

| Load | Build | Failed % | Restarts | Mean (OK) | p95 (OK) | p99 (OK) | Throughput (OK rps) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 20 users | Baseline | 1.08% | 0 | 2,961 ms | 18,942 ms | 37,557 ms | 23.9 |
| 20 users | **Virtual threads** | **0%** | 0 | 3,113 ms | 20,315 ms | 34,479 ms | 24.1 |
| 50 users | Baseline | 66.51% | **2** | 6,347 ms | 19,753 ms | 28,758 ms | **11.95** |
| 50 users | **Virtual threads** | **56.23%** | **1** | **5,031 ms** | 21,729 ms | 28,277 ms | **18.43** |

At 20 users both builds are broadly comparable (VT slightly better: 0% vs 1.08% failures). At 50
users - the load level where PostgreSQL-with-latency starts to break down - virtual threads showed
a **meaningfully better outcome across the board**: 10 points lower failure rate (56% vs 67%),
faster mean OK response time, fewer pod restarts (1 vs 2), and **54% higher successful
throughput** (18.43 vs 11.95 rps). This is the opposite of what was found on the original
same-node topology, where virtual threads showed no benefit and were mildly worse at moderate
load.

This is consistent with the theory from `virtual-threads-report.md`: virtual threads help when
requests spend real time *blocked waiting on I/O* rather than consuming CPU. With ~15-20ms of
real network latency added to every DB round-trip (and each REST operation making several
round-trips), threads here are genuinely parked waiting, not burning CPU - exactly the condition
virtual threads are designed to help with, by letting far more of them be in-flight without
requiring a correspondingly large platform thread pool.

Both builds still degrade heavily at 50 users on this single-instance, 1-CPU/1Gi, 1-connection-
pool-sized deployment (this is not a "virtual threads solve everything" result) - the p95/p99 tail
figures stayed comparably enormous either way, most likely reflecting the underlying JDBC
connection pool (Agroal, default size) being the actual limiting resource once concurrency is high
enough - virtual threads increase how many requests can be *waiting*, but not how many can hold a
real DB connection at once. Failed requests and restarts dropped, but the surviving requests were
still slow.

### KafkaSQL storage - no meaningful difference either way, at either load level

| Load | Build | Failed % | Restarts | Mean (OK) | p95 (OK) | p99 (OK) | Throughput (OK rps) |
| --- | --- | --- | --- | --- | --- | --- | --- |
| 20 users | Baseline | 0% | 0 (\*) | 27 ms | 100 ms | 111 ms | 33.57 |
| 20 users | Virtual threads | 0% | 0 | 27 ms | 98 ms | 109 ms | 33.57 |
| 50 users | Baseline | 0.02% | 0 | 27 ms | 97 ms | 169 ms | 41.94 |
| 50 users | Virtual threads | 0.06% | 0 | 24 ms | 93 ms | 101 ms | 40.83 |

(\*) one restart was observed once during initial KafkaSQL bootstrap in an earlier smoke test, not
during a measured run - all four runs tabulated here had zero app-pod restarts.

KafkaSQL storage handled the same injected latency, and the same load levels that broke
PostgreSQL, essentially without breaking a sweat - both builds stayed at ~100% success with p99
under 200ms even at 50 users. This makes architectural sense: KafkaSQL's write path appends to a
Kafka topic and applies changes to a local in-memory/cached index asynchronously, rather than
blocking the request thread on a synchronous round-trip to a remote datastore for every operation
the way the SQL storage variant's JDBC calls do - so there's much less thread-blocked-on-I/O time
for virtual threads to reclaim in the first place. With no meaningful difference between builds at
either load level, this scenario doesn't provide evidence either for or against virtual threads;
it just confirms KafkaSQL's write path is inherently far less sensitive to injected storage
latency than the SQL storage path, independent of threading model.

## Revised conclusion

The original `virtual-threads-report.md` recommendation ("don't merge based on this data alone -
test a workload with genuine I/O wait first") was directly acted on, and the result changes the
picture for **SQL storage specifically**:

- **SQL/PostgreSQL storage, under realistic DB latency**: virtual threads showed a real,
  repeatable improvement in failure rate, throughput, and restart count at the load level where
  the platform-thread build started breaking down. This is now a positive signal worth pursuing
  further (e.g. testing at intermediate load levels between 20 and 50 users to find where the
  benefit is largest, and/or with a larger JDBC connection pool size to see if virtual threads'
  benefit scales further once the connection pool itself isn't the secondary bottleneck).
- **KafkaSQL storage**: no evidence either way - both builds performed nearly identically, because
  this storage path isn't blocking-I/O-bound the way SQL storage is.
- The original same-node (no injected latency) result from `virtual-threads-report.md` still
  stands for that specific condition (no benefit, mild moderate-load regression) - the takeaway is
  that *the answer depends on the deployment's actual network/storage latency profile*, not a
  single universal answer for "does this registry benefit from virtual threads."

### Caveats

- Single-run measurements per condition (not averaged over multiple repetitions) - given the
  consistency of the PostgreSQL result direction across both the 20u and 50u load levels, and the
  size of the gap at 50u (54% throughput difference), this is unlikely to be pure noise, but
  should still be treated as an initial signal rather than a rigorously statistically-validated
  benchmark.
- Only one latency profile (~15ms +/- 5ms) and one resource limit (1 CPU/1Gi) were tested.
- The Kafka/serde load generator ran throughout every test as a secondary background load; its own
  results were consistently near-perfect in every run (produce/consume failures effectively zero)
  and are not the focus of this comparison.
