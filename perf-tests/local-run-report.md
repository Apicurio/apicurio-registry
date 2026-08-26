# perf-main local dry run report

This is a one-off manual validation of the `perf-tests`/`perf-main.yaml` pipeline (PR #9839),
run locally against a real Minikube cluster to confirm the whole flow works end-to-end before
it runs for the first time on an actual merge to `main` in CI (which can't be exercised from a
PR, by design — see the workflow's own comments).

## Environment

- Local Minikube (`podman` driver, Kubernetes v1.28.3), single node
- Images built locally and loaded via `minikube image load` (no external registry)
- Namespace: `default`

## Topology deployed

- Operator (`apicurio-registry-operator-v3.3.2-snapshot`)
- PostgreSQL (single instance, `postgresql` storage backend)
- Kafka (single-node, KRaft mode, PLAINTEXT)
- Keycloak (`registry` realm, confidential `registry-api` client, service accounts enabled)
- `ApicurioRegistry3` CR (`perf-test`), wired to PostgreSQL + Keycloak (OAuth2 client-credentials)

## Bugs found and fixed during this dry run

The pipeline did **not** work on the first attempt. Running it end-to-end surfaced several real
bugs that static review/local unit testing had missed:

1. **Operator NullPointerException on CRs without `auth.tls`** - `registry-cr.yaml` omitted the
   `tls` block under `auth`; the operator unconditionally calls
   `AuthSpec.getTls().getTlsVerificationType()` without a null check. Fixed by adding
   `tls.tlsVerificationType: none` to the CR.
2. **Gatling `contentTypeHeader` conflict** - a protocol-level default
   `contentTypeHeader("application/json")` caused the OAuth `client_credentials` token request
   (which needs `application/x-www-form-urlencoded` via `.formParam()`) to be rejected by
   Keycloak with HTTP 400. Fixed by removing the protocol-level default and relying on
   `.asJson()` per-request where JSON bodies are actually sent.
3. **Wrong JMESPath on create-artifact response** - checked for a top-level `id` field; the v3
   API actually returns `{"artifact": {"artifactId": ...}, "version": {...}}`. Fixed to
   `artifact.artifactId`.
4. **Wrong REST paths (v2-style, not v3)** - `"Get artifact metadata"` hit a `/meta` suffix that
   doesn't exist in v3 (metadata is at `/groups/{g}/artifacts/{a}` directly), and `"Get artifact
   content"` was actually hitting the metadata path again instead of the real content endpoint,
   `/groups/{g}/artifacts/{a}/versions/{versionExpression}/content`. Both fixed.
5. **`KafkaLoadGenerator` OAuth env-var bug** - it called `System.getenv(SerdeConfig.AUTH_TOKEN_ENDPOINT)`
   etc., but those `SerdeConfig` constants are dotted *serde config property keys*
   (e.g. `"apicurio.registry.auth.service.token.endpoint"`), not environment variable names -
   so OAuth was silently never configured for the Kafka client, and every produce call failed
   (`produced=826 consumed=0 produceFailures=825` in the first run). Fixed to read the plain
   `AUTH_TOKEN_ENDPOINT`/`AUTH_CLIENT_ID`/`AUTH_CLIENT_SECRET` env vars (matching what
   `perf-job.yaml` actually sets) and map them onto the correct `SerdeConfig` property keys.
6. **`kubectl cp` cannot target a completed pod** - the original `perf-job.yaml` exited
   immediately after the test run, but `kubectl cp`/`exec` (used by `perf-main.yaml` to retrieve
   the Gatling report) require a *running* container. Fixed by having the Job's container write
   its real exit code to `/results/exit-code` and then sleep for a grace period before exiting,
   and changing the workflow to poll for that marker file (while the container is still running)
   instead of waiting on the Job's own `condition=complete`.

## Final successful run (60s duration, 10 users)

```
---- Global Information -------------------------------------------------------------|---Total---|-----OK----|----KO----
> request count                                                                      |     1,550 |     1,550 |         0
> min response time (ms)                                                             |         1 |         1 |         -
> max response time (ms)                                                             |        42 |        42 |         -
> mean response time (ms)                                                            |         7 |         7 |         -
> response time std deviation (ms)                                                   |         7 |         7 |         -
> response time 50th percentile (ms)                                                 |         4 |         4 |         -
> response time 75th percentile (ms)                                                 |         9 |         9 |         -
> response time 95th percentile (ms)                                                 |        21 |        21 |         -
> response time 99th percentile (ms)                                                 |        27 |        27 |         -
> mean throughput (rps)                                                              |     22.14 |     22.14 |         -
---- Response Time Distribution ----------------------------------------------------------------------------------------
> OK: t < 800 ms                                                                                          1,550   (100%)
> OK: 800 ms <= t < 1200 ms                                                                                   0     (0%)
> OK: t >= 1200 ms                                                                                            0     (0%)
> KO                                                                                                          0     (0%)
Global: percentage of failed events is less than or equal to 1.0 : true (actual : 0.0)
```

Kafka load generator (Avro serde, real schema registration/lookup through produce/consume):

```
Starting Kafka load generator: registry=http://perf-test-app-service.default.svc:8080/apis/registry/v3,
  bootstrap=kafka.default.svc:9092, duration=60s, rate=10/s
Done. produced=601 consumed=601 produceFailures=0
```

`PerfTestRunner` final result: `Gatling exit code: 0, Kafka load generator ok: true`.

### Threshold check against `baseline.json`

| Metric | Observed | Baseline | Status |
| --- | --- | --- | --- |
| Mean response time (ms) | 7 | 200 | ok |
| p95 response time (ms) | 21 | 500 | ok |
| p99 response time (ms) | 27 | 1000 | ok |
| Failed requests (%) | 0.00 | 1.0 | ok |

(Note: these baseline thresholds were set conservatively for a first cut and were never
calibrated against real cluster hardware - a single-node local Minikube unsurprisingly beats
them comfortably. They should be revisited once we have a few real `perf-main` runs in CI to
establish an actual baseline.)

## Conclusion

The pipeline works correctly end-to-end on real infrastructure (operator, Keycloak, Kafka,
PostgreSQL, Gatling, kubectl cp result retrieval) after the fixes above. All fixes have been
folded into the corresponding source files in this PR; this file is left as a record of the
validation that was performed, not as ongoing documentation (no future test run is expected to
update it).
