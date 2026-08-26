# perf-tests stress run: pushing past the breaking point

A follow-up to `local-run-report.md` (which validated the pipeline end-to-end at modest load).
This run deliberately pushed load far higher, specifically to try to break the registry and
saturate its pod's CPU/memory, rather than just confirm the pipeline works.

## Environment

Same topology as the modest run (local Minikube, operator + PostgreSQL + Kafka + Keycloak +
`ApicurioRegistry3` CR `perf-test`). Cluster: 4 vCPU / node. Registry app pod resources (set by
the operator's defaults): **requests 500m CPU / 512Mi memory, limits 1 CPU / 1Gi memory**.

## Load applied

- `PERF_USERS=300` (vs. 10 in the modest run) - Gatling ramps 300 users over 10s, then holds
  `constantUsersPerSec(150)` for the full duration
- `PERF_DURATION_SECONDS=180` (vs. 60)
- `PERF_PRODUCE_RATE_PER_SEC=300` for the concurrent Kafka load generator (vs. 20)

## Result: it broke

### Registry REST API (Gatling)

```
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |    93,930 |    44,363 |    49,567
> min response time (ms)                                                             |         0 |         2 |         0
> max response time (ms)                                                             |    37,644 |    37,463 |    37,644
> mean response time (ms)                                                            |     9,795 |     6,998 |    12,299
> response time 50th percentile (ms)                                                 |    10,001 |        10 |    10,072
> response time 95th percentile (ms)                                                 |    26,608 |    27,645 |    26,202
> response time 99th percentile (ms)                                                 |    30,972 |    34,680 |    30,700
> mean throughput (rps)                                                              |    434.86 |    205.38 |    229.48
---- Errors ------------------------------------------------------------------------------------------------------------
> i.n.c.ConnectTimeoutException: connection timed out after 10000 ms                                    33,040 (35.86%)
> Get artifact metadata: No attribute named 'artifactId' is defined                                     21,285 (23.10%)  <- cascades from failed Create
> Get artifact content: No attribute named 'artifactId' is defined                                      21,285 (23.10%)  <- cascades from failed Create
> j.i.IOException: Premature close                                                                      16,060 (17.43%)
> j.n.ConnectException: finishConnect(..) failed with error(-111): Connection refused                       427 (0.46%)  <- pod was down mid-restart
> status.find.is(200), but actually found 500                                                                40 (0.04%)
```

**52.77% overall failure rate.** Mean response time went from **7ms** (modest run) to **9.8
seconds**; p99 from 27ms to **31 seconds**.

### `perf-tests/scripts/check-thresholds.py` against `baseline.json`

| Metric | Observed | Baseline | Status |
| --- | --- | --- | --- |
| Mean response time (ms) | 9,795 | 200 | **REGRESSION** (>250ms) |
| p95 response time (ms) | 26,608 | 500 | **REGRESSION** (>625ms) |
| p99 response time (ms) | 30,972 | 1000 | **REGRESSION** (>1250ms) |
| Failed requests (%) | 52.77 | 1.0 | **REGRESSION** |

Confirms the regression-detection script correctly flags a genuinely broken run, not just cosmetic
threshold nitpicks - useful evidence that `check-thresholds.py` does what it's supposed to.

### Registry app pod: crash-looped under load

```
kubectl top pods (mid-run):
NAME                                       CPU(cores)   MEMORY(bytes)
perf-test-app-deployment-97c5d74-6z46h     519m         268Mi
keycloak-699d8cd85f-tjfz7                  637m-739m    1.8Gi
```

The app pod **restarted 5 times** during the 180s run:

```
Events:
  Killing    kubelet   Container apicurio-registry-app failed liveness probe, will be restarted
  Unhealthy  kubelet   Readiness probe failed: Get ".../health/ready": EOF
  Unhealthy  kubelet   Liveness probe failed: HTTP probe failed with statuscode: 503
  Unhealthy  kubelet   Liveness/Readiness probe failed: context deadline exceeded
Last State: Terminated, Reason: Error, Exit Code: 143   (SIGTERM from kubelet, not OOMKilled)
```

**Root cause: CPU starvation, not memory.** Exit code 143 (SIGTERM) rather than 137 (OOMKilled) -
memory stayed under the 1Gi limit throughout (~268-437Mi observed), but CPU pegged at the 1-core
limit while **Keycloak** was simultaneously consuming 637-739m CPU (and up to 1.8Gi memory) on the
same 4-core node fielding constant OAuth token requests from 300 concurrent virtual users. Under
that combined contention, the app couldn't service its own `/health/live` and `/health/ready`
probes within their timeout, so Kubernetes killed and restarted it repeatedly - each restart adding
a cold-start window of near-total unavailability (explaining the `ConnectException: Connection
refused` errors and part of the `ConnectTimeoutException`/`Premature close` errors above).

### Kafka path: notably more resilient

```
Done. produced=10502 consumed=10498 produceFailures=3
```

Only **3 failures out of 10,502** produce calls (0.03%) despite the same overall load and the
registry itself being in a crash loop. Plausible reason: the Avro serde caches the resolved
schema/global-id after the first successful registration, so most subsequent produce calls don't
need to round-trip to the (struggling) registry at all - unlike the Gatling REST scenario, which
creates a brand-new artifact on every iteration and therefore hits the registry fresh every time.
This asymmetry itself is a useful signal about realistic failure modes: a REST-heavy client
workload degrades far faster under registry-side pressure than a typical Kafka/serde-based
consumer, which mostly reads from a local cache.

## Takeaways

1. **The pipeline works as a genuine load/breaking tool**, not just a smoke test - it reproduced a
   real crash-loop with plausible root cause (CPU contention with Keycloak under a fixed 1-CPU
   limit), not a bug in the test harness itself.
2. **Keycloak is a significant, easily-overlooked co-tenant cost** under token-heavy load
   (client-credentials fetched once per Gatling virtual-user iteration here) - worth keeping in
   mind when interpreting future `perf-main` results: a "registry regression" could actually be a
   Keycloac-contention artifact of the load generator's own token-fetch pattern, not the registry
   itself. A more realistic simulation might fetch/cache a token per session instead of
   per-iteration, closer to how a real client would behave.
3. **No evidence of a memory leak or unbounded memory growth** at this load level - the crash mode
   was purely CPU/liveness-probe related.
4. This was run against the **default operator resource limits** (1 CPU / 1Gi) on a shared 4-core
   node also running Keycloak/Kafka/Postgres - not a dedicated/production-sized node. This result
   says more about "what a small, shared, resource-constrained deployment produces" than it does
   about the registry's real ceiling on a dedicated node; still, it's a meaningful example of the
   pipeline being able to surface a genuine breaking point rather than just proving green.

## Recommendation for a follow-up

- Consider having `RegistryApiSimulation` fetch the OAuth token once per session (cached, and
  refreshed only near expiry) rather than once per iteration, to isolate "registry under load"
  from "Keycloak under load from an unrealistic token-fetch pattern."
- Consider whether `perf-main.yaml`'s default `PERF_USERS`/`PERF_PRODUCE_RATE_PER_SEC` should be
  tuned lower than what was used in this manual stress run (300) - this run intentionally went
  looking for a breaking point, whereas the actual CI defaults should represent a sustainable,
  repeatable load that doesn't crash-loop the pod (that's what happened in the 10-user/60s run in
  `local-run-report.md`, which stayed at 0% failures).
