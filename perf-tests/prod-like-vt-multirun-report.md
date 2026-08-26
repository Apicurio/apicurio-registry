# Multi-run comparison: virtual threads vs. platform threads (3 reps per condition)

Follow-up to `prod-like-vt-comparison-report.md`, which was based on single-run measurements per
condition. Repeated each condition **3 times** (deleting/recreating the app pod between runs for a
clean state) to check whether the earlier signal holds up, and to get a per-restart-count
comparison that isn't confounded by a single lucky/unlucky run.

## PostgreSQL @ 50 users, 45s (3 reps each)

| Run | Build | Failed | Failure % | Restarts | OK throughput (rps) |
| --- | --- | --- | --- | --- | --- |
| 1 | Baseline | 1796/3114 | 57.67% | 2 | 14.81 |
| 2 | Baseline | 1795/3166 | 56.70% | 2 | 14.43 |
| 3 | Baseline | 1664/3104 | 53.61% | 2 | 15.65 |
| **avg** | **Baseline** | | **55.99%** | **2** | **14.96** |
| 1 | Virtual threads | 1532/3092 | 49.55% | 2 | 20.00 |
| 2 | Virtual threads | 1562/3168 | 49.31% | 2 | 17.27 |
| 3 | Virtual threads | 1525/3198 | 47.69% | 2 | 20.65 |
| **avg** | **Virtual threads** | | **48.85%** | **2** | **19.31** |

The failure-rate and throughput improvement **holds up consistently across all 3 reps** - virtual
threads: ~7 points lower failure rate (48.85% vs 55.99%) and ~29% higher successful throughput
(19.31 vs 14.96 rps), with no overlap between the two builds' ranges (baseline: 53.6-57.7%; VT:
47.7-49.6%). This is a real, repeatable effect, not a one-off fluke.

**Correction to the previous report:** restart count was identical (2) for both builds in every
single run. The earlier single-run report's observation of "1 restart with VT vs 2 with baseline"
did not replicate - that was noise from a single measurement, not a genuine virtual-threads
benefit on restart count. The genuine, repeatable benefit is in failure rate and throughput, not
crash frequency - both builds crash-loop identically often at this load level.

## KafkaSQL @ 50 users, 45s (3 reps each)

| Run | Build | Failed | Failure % | Restarts\* | OK throughput (rps) |
| --- | --- | --- | --- | --- | --- |
| 1 | Baseline | 0/4700 | 0% | 1 | 83.93 |
| 2 | Baseline | 9/4686 | 0.19% | 0 | 59.96 |
| 3 | Baseline | 9/4688 | 0.19% | 0 | 83.55 |
| **avg** | **Baseline** | | **0.13%** | | **75.81** |
| 1 | Virtual threads | 1/4700 | 0.02% | 1 | 65.26 |
| 2 | Virtual threads | 0/4700 | 0% | 0 | 83.93 |
| 3 | Virtual threads | 0/4700 | 0% | 0 | 83.93 |
| **avg** | **Virtual threads** | | **0.007%** | | **77.71** |

(\*) The "1" restarts recorded for the first run of each build happened *before* the measured load
started (a separate, pre-existing KafkaSQL startup-race - see "Operational finding" below) - both
had 0 restarts *during* every measured test.

No meaningful difference between builds - both stay effectively perfect (<0.2% failure) across all
6 runs. This confirms the original finding: KafkaSQL's async write path isn't blocking-I/O-bound
the way SQL storage's synchronous JDBC round-trips are, so there's nothing here for virtual
threads to improve.

## Operational finding (unrelated to virtual threads): a KafkaSQL startup race

While running these repeated trials, the KafkaSQL app pod repeatedly crash-looped **before** the
measured load even started, in a way that took real investigation to rule out as a
virtual-threads-specific bug. Root causes, in the order they were found and eliminated:

1. **Accumulated journal history**: after many successive test runs against the same
   long-lived Kafka broker in one session, `kafkasql-journal` had built up 3,542 messages, making
   the on-startup replay-and-catch-up phase progressively slower with each successive test,
   eventually exceeding the liveness probe's kill window (`periodSeconds=10, failureThreshold=3`
   = 30s). Fixed by tearing down and recreating the Kafka broker (fresh, empty topic) between
   test sessions - not a code bug, just a consequence of reusing one broker across many
   consecutive runs without ever resetting it.
2. **Even with a fresh, empty journal**, a second, more subtle race remained: the consumer
   group's initial coordinator-discovery handshake (`FindCoordinator` -> `JoinGroup` ->
   `SyncGroup` -> `OffsetFetch` -> `Seek`) is a chain of several sequential round-trips, each
   now carrying the ~15ms+jitter Toxiproxy latency, and occasionally hit transient
   `NOT_COORDINATOR`/"coordinator unavailable" retries (particularly right after the broker
   itself was freshly restarted) - stacking up enough elapsed time to occasionally exceed the
   30s liveness kill window purely by chance, independent of which app build was running.
   Deleting the crash-looping pod and letting it retry usually succeeded within 1-2 attempts.
3. **A related self-reinforcing failure mode**: during a rolling update (old pod present while a
   new one starts), both pods compete for the same small CPU budget, which can make the new
   pod's startup slow enough to trip the same race, while the old pod is never released because
   the new one never becomes ready - a mutual-starvation loop. Scaling down to 0 replicas and
   back up (forcing exactly one pod, no contention) broke this cleanly.

None of this is a virtual-threads regression - `@RunOnVirtualThread` was only applied to the REST
resource impl classes, not to `KafkaSqlSink`/the Kafka consumer thread, and the same race was
observed and resolved identically for both builds. It's a genuine, pre-existing operational
characteristic of the KafkaSQL storage variant under injected network latency and constrained
node resources, worth being aware of independent of this experiment (e.g. the liveness probe's
`failureThreshold`/`periodSeconds` may need tuning up for KafkaSQL storage specifically, or a
`startupProbe` with a longer allowance, in latency-heavy real deployments).

## Revised overall conclusion

The multi-run data confirms the single-run finding from `prod-like-vt-comparison-report.md`:
virtual threads give a real, repeatable improvement for SQL/PostgreSQL storage under realistic
network latency (lower failure rate, higher throughput), and make no difference for KafkaSQL
storage. The one correction is that the restart-count difference reported for the single
PostgreSQL run does not hold up - restart count was identical between builds across all repeated
runs; the genuine benefit is in failure rate and throughput, not crash frequency.
