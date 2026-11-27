# Usage monitoring with OpenTelemetry, Prometheus and Grafana

Apicurio Registry can export a counter of the schema fetches it serves, with the client and the schema identifier on every count, through OpenTelemetry. This example runs the four components that turn that counter into history you can chart, and the scripts that start them, check them and stop them:

- **Registry** is the ordinary Registry image with the usage metric switched on. Its schemas live in memory and the SQL usage recording stays off, so Registry stores no usage data at all.
- **OpenTelemetry Collector** receives the Registry's export and exposes the usage metric in the Prometheus format. It keeps only the metric and the identity of the Registry process, and discards the traces and logs the Registry also exports.
- **Prometheus** scrapes the Collector and stores the history, outside Registry.
- **Grafana** starts with its Prometheus datasource and the schema usage dashboard already provisioned.

**The dashboard is the canonical one of [`distro/metrics/usage`](../../distro/metrics/usage/README.md).** This example mounts that file read-only and provisions it; it keeps no copy, so what you see here is exactly what you import into your own Grafana. The dashboard needs only the metric and labels described there, not this stack. Open it at `http://127.0.0.1:3000/d/apicurio-schema-usage` (the port you chose, see below) after `./demo.sh`, and pick the Registry process in the first filter.

**New to this? Follow the [step-by-step guide](GUIDE.md).**

## Quick start

Requires Podman (preferred) or Docker, with Compose v2, plus `curl` and `jq`. The Registry image must contain the usage metrics feature, so a published `latest-snapshot` older than that change cannot populate this stack: build the image from this checkout (next section) and pass it as `REGISTRY_IMAGE`.

```bash
cd examples/usage-telemetry-otel
export REGISTRY_IMAGE=localhost/apicurio/apicurio-registry:local   # the image you built below
./setup.sh      # starts the four services and waits until the export path works end to end
./demo.sh       # real schema registrations and fetches, then exact counter checks
./verify.sh     # re-checks the stack and the last demo run, at any later time
./teardown.sh   # stops the stack and keeps its data (./teardown.sh --purge deletes it)
```

`demo.sh` prints one line per check and ends with `All checks passed.`. It is safe to run again, and again after a Registry restart. With Docker instead of Podman set `CONTAINER_RUNTIME=docker`. The plain two-command form of the other examples also works: `docker compose up -d` and then `./demo.sh`, which is how CI runs it (`setup.sh` adds the preflight checks, the configuration validation and the diagnostics).

### Build the Registry image

From the repository root, using the packaging path of [`distro/docker`](../../distro/docker/README.md):

```bash
./mvnw package -pl app -am -DskipTests          # builds app/target/apicurio-registry-app-*-all.tar.gz
./mvnw process-resources -pl distro/docker      # copies it, with the Dockerfile, to distro/docker/target/docker
podman build -f distro/docker/target/docker/Dockerfile.jvm \
  -t localhost/apicurio/apicurio-registry:local distro/docker/target/docker
```

This is the standard Registry image, the same one CI builds; nothing in the example patches it. The full release build in `distro/docker/README.md` (`./mvnw clean install -DskipTests -Pprod -Dfull`) produces the same files and also needs the operator dependencies. With Docker, build with `docker build` and tag it `apicurio/apicurio-registry:local`; with Podman keep the `localhost/` prefix, which stops the runtime trying to pull the name from a public registry.

## Services

| Service | Host address (loopback only) | Role |
|---|---|---|
| `registry` | `127.0.0.1:8081` | Registry API, usage metric exported every 5 seconds |
| `otel-collector` | `127.0.0.1:13133` (health check only) | Receives OTLP on its private port 4318, serves Prometheus metrics on 8889 and its own metrics on 8888 |
| `prometheus` | `127.0.0.1:9090` | Scrapes both Collector ports every 5 seconds, keeps up to 15 days or 1 GB |
| `grafana` | `127.0.0.1:3000` | Datasource `apicurio-usage-prometheus` and the dashboard `Apicurio Registry schema fetch usage` (folder `Apicurio Registry`); user `admin`, password `admin` |

Override the ports with `REGISTRY_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT` and `COLLECTOR_HEALTH_PORT` (or the options of `setup.sh`), and the Compose project name with `COMPOSE_PROJECT_NAME` (or `--project`). Containers, network and volumes are all named after the project, so a second copy does not collide with the first. The Collector's OTLP port is not published: only the Registry talks to it.

## What the Registry exports

The counter `apicurio.usage.schema.fetches` counts successful `GET /apis/registry/v3/ids/globalIds/{id}` and `.../ids/contentIds/{id}` requests that carry a valid `X-Registry-Client-Id` header. It is attributed to the client, the identifier type (`global_id` or `content_id`), the identifier and the operation (`SERIALIZE`, `DESERIALIZE` or `UNKNOWN`, from `X-Registry-Operation`). Requests without a valid client ID, requests for references, and failed requests are not counted. The contract is documented in the [usage telemetry guide](../../docs/modules/ROOT/pages/getting-started/assembly-usage-telemetry.adoc).

The Collector turns it into `apicurio_usage_schema_fetches_total` with the labels `apicurio_registry_client_id`, `apicurio_registry_identifier_type`, `apicurio_registry_identifier_value`, `apicurio_registry_operation` and `service_instance_id`. Prometheus adds `job` and `instance`, and `instance` is the Collector's address, not the Registry's. The Registry process is identified by `service_instance_id`. The Registry generates one (a random UUID) only when none is configured, through `quarkus.otel.resource.attributes` or `OTEL_RESOURCE_ATTRIBUTES`; an ID you configure is exported unchanged and must be unique for every process start and every installation, because that is what keeps the counters of two processes apart even though an in-memory Registry hands out the same IDs again after a restart. One more series per process, `jvm_cpu_count`, is passed through as a heartbeat: Prometheus keeps the time the Registry measured each sample, so `time() - timestamp(jvm_cpu_count)` is the age of the last export that reached the Collector.

## Run state

`./demo.sh` writes `.local/projects/<project>/run-state.json` (atomically; also `.local/projects/<project>/runs/<run id>.json`), where `<project>` is the Compose project (`usage-telemetry-otel` by default), and `./verify.sh` or a browser test reads it to know exactly what the last run did. State and failure logs are namespaced by project, so one project's `teardown.sh --purge` removes only its own files and never another project's, even when both use the same `.local`. The directory is created by the scripts, holds a marker file, is git-ignored, and the scripts refuse to write into any `LOCAL_DIR` they did not create or through a symbolic link. A run state is written only when every check of the run passed. An older unscoped `.local/run-state.json` (schema version 1) is not read, migrated or deleted: `verify.sh` rejects it and asks for a new `./demo.sh`.

| Field | Meaning |
|---|---|
| `schemaVersion` | `2` |
| `project` | The Compose project that owns the state; `verify.sh` refuses a state of another project |
| `runId`, `startedAt`, `settledAt` | The run, and when the demo finished checking it (UTC, informational), also as `startedAtEpochSeconds` and `settledAtEpochSeconds` |
| `evaluationTime`, `evaluationTimeSeconds` | The exact Prometheus time the run was sealed at: the newest sample time of the run's series, taken from Prometheus (not from this machine's clock). The demo checked that the same query at exactly this time returns exactly the expected series before writing it. Query history with `time=<evaluationTime>`; never a later time, and never a margin after it |
| `timeRange` | `fromMs` and `toMs`: a window around the run, ready for Grafana's `from` and `to` URL parameters |
| `endpoints` | The Registry API, Prometheus and Grafana base URLs, and the datasource UID |
| `source` | `label` (`service_instance_id`) and `instanceId`: the one Registry process that served this run |
| `metric` | The Prometheus metric name, the heartbeat metric name, and the label name of each attribute |
| `clients` | The two stable client names and the run's unique probe client |
| `schemas` | Per version: group, artifact, version, `globalId`, `contentId` |
| `expectedSeries` | The exact series this run created, each with its final `count` |
| `notCounted` | The requests that must not be counted, with the status they returned |

Two different questions, answered separately. **Historical verification** asks whether the run's counters, as they stood at `evaluationTime`, are exactly the expected ones. A later fetch of the same identifiers is exported later, so it has a later sample time and cannot change that answer; a Registry restart or a teardown cannot either, and a wrong sealed count still fails. The precision of the seal is the clock agreement between the Registry's export timestamps and Prometheus, which here share one host. **Current export health** asks whether anything is exporting now: the age of the newest sample of `jvm_cpu_count`. That age is not proof that a process is running (a process that stopped a few seconds ago looks the same, and the Collector keeps serving the last point for five minutes), and an old age is not proof of no use.

The run's source is found by matching the unique probe client, never by taking the newest data. Every check is keyed by `source.instanceId` and the run's own identifiers. Stable client names with new schemas still create new series, so the series count of a Registry process grows with every run (6 per demo run).

## Files

| File | Purpose |
|---|---|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough, presenter notes and troubleshooting |
| [`docker-compose.yml`](docker-compose.yml) | The four services; every Registry setting is commented |
| [`setup.sh`](setup.sh) | Preflight, configuration validation, start, bounded readiness checks, diagnostics on failure |
| [`demo.sh`](demo.sh) | Real traffic, exact counter checks, run state |
| [`verify.sh`](verify.sh) | Read-only re-check of the stack and of a saved run |
| [`teardown.sh`](teardown.sh) | Stops this project only; keeps data unless `--purge` |
| [`lib.sh`](lib.sh) | Helpers the scripts share: endpoints, bounded HTTP, runtime detection, Prometheus queries |
| [`config/`](config) | Collector and Prometheus configuration, Grafana datasource and dashboard provisioning (the dashboard file itself is [`distro/metrics/usage/grafana/schema-usage.json`](../../distro/metrics/usage/grafana/schema-usage.json), mounted read-only by `docker-compose.yml`) |
| [`tests/lifecycle-test.sh`](tests/lifecycle-test.sh) | Behavior tests of the scripts against real containers (own project and ports, cleans up only itself) |
| [`tests/dashboard-test.sh`](tests/dashboard-test.sh) | Read-only checks of the canonical dashboard against the running stack: the file, the provisioned copy and mount, and every panel query through Prometheus and through Grafana, for the last demo run or for the scenario file of `dashboard-scenarios.sh` |
| [`tests/dashboard-scenarios.sh`](tests/dashboard-scenarios.sh) | The entry point of the `dashboard-scenarios` case of `tests/lifecycle-test.sh`, which owns the run (claim, preflight, own directories, interruptible calls, cleanup): a stack of its own with a series limit of 4, the situations the finite demo cannot show (a process that has only exported its heartbeat, overflow, a restart that reuses typed identifiers, steady traffic for rates, a stopped process read after Prometheus and the Collector forgot it), then `dashboard-test.sh` on them and three rate mutants, each judged by its own exit status |
| [`tests/dashboard-promql-test.sh`](tests/dashboard-promql-test.sh) | The dashboard's PromQL on controlled series with the `promtool` of the pinned Prometheus image: one stream seen through two Collector scrape routes, rates, the process table's range bounds, the process selector, and mutants that must fail |
| [`tests/dashboard-lib.sh`](tests/dashboard-lib.sh), [`tests/dashboard-unit-test.sh`](tests/dashboard-unit-test.sh) | The strict query helpers of the dashboard checks, and their fast tests against a throwaway backend (no query failure is read as an empty answer, scenario files are validated first, provisioning is required unless `--external`) |
| [`test-ui.sh`](test-ui.sh), [`screenshots.sh`](screenshots.sh), [`tests/ui/`](tests/ui) | Optional Playwright tests and screenshots of the dashboard in a real Grafana, with a real form login and the exact rows of the last demo run. A separate npm package with locked dependencies, outside Registry's build; the scripts change nothing in the stack, but they do download npm packages and Chromium when missing and write their own output directories. See the [guide](GUIDE.md#optional-test-the-dashboard-in-a-real-browser) |
| [`images/`](images) | The overview and filtered screenshots of the guide, produced by `screenshots.sh` |
| [`tests/unit-test.sh`](tests/unit-test.sh) | Fast regression tests with a fake container runtime (`tests/fixtures/fake-runtime.sh`) and throwaway listeners; no daemon needed |

## Notes

- **Local only.** Plain HTTP, no authentication on the Registry, Grafana's `admin` user with a well-known password (change it with `GRAFANA_ADMIN_PASSWORD` before starting the stack), host ports on loopback. Registry storage is in memory: a restart forgets the schemas, and the example relies on that being visible (see the guide). Prometheus and Grafana keep named volumes that survive `teardown.sh`. Container logs are rotated (10 MB, three files per service), and Prometheus keeps at most 15 days or 1 GB.
- **What the counts are.** Observed fetch requests to this Registry process, not messages, not exact billing, and not proof that a schema is unused. The Apicurio serdes cache schemas, so a busy client can fetch rarely. Client IDs are self-reported header values, not authenticated identities. A global ID identifies one version, a content ID can be shared by several versions (the demo shows it). No data is not evidence of no use: a stopped Collector, an unreachable Prometheus or a Registry that cannot export all look like missing data, and the scripts report them as unknown.
- **Bounded cardinality.** Each Registry process limits the series of this counter with a threshold, `apicurio.usage.metrics.max-series` (default 2000). It is not a hard ceiling: the SDK checks the series count and adds a series in two unlocked steps, so concurrent first-seen combinations can exceed it; beyond it the detail collapses into one overflow series without client or identifier labels, and the totals stay exact. In Prometheus the overflow series is `apicurio_usage_schema_fetches_total{otel_metric_overflow="true"}` (plus `service_instance_id`); `tests/lifecycle-test.sh` shows it with a limit of 4 (`APICURIO_USAGE_METRICS_MAX_SERIES=4 ./setup.sh`; six fetches by six clients one after another gave three detailed series and three in overflow with this pinned SDK, which is an observation of that sequence and not a statement about concurrent requests), and `demo.sh` then fails and says why instead of passing on series it cannot tell apart. Cumulative series stay until the process restarts.
- **Export can lose observations.** The SDK exports on an interval, and a Registry that stops before a successful export loses what it has not exported. In the one finite run that `tests/lifecycle-test.sh` performs (a short Collector outage, five fetches, the Registry process kept running) the Registry kept answering and the next successful export carried the cumulative totals. That observation is not a guarantee: it does not prove zero overhead or lossless delivery, and until the data arrives the answer is unknown, not zero.
- **Traces and logs are not stored.** Building the Registry fixes its trace and log exporters on, so it also sends traces and logs to the Collector. The Collector receives them and drops them (the `nop` exporter); nothing in this stack stores them.
- **Use your own Collector, Prometheus or Grafana.** The Registry only needs the settings in the `registry` service of [`docker-compose.yml`](docker-compose.yml): the experimental gate, `APICURIO_USAGE_METRICS_ENABLED=true`, `QUARKUS_OTEL_SDK_DISABLED=false` and an OTLP endpoint. To keep this stack and send the data elsewhere as well, point `OTEL_COLLECTOR_CONFIG` at your own Collector configuration (`setup.sh` validates it with the Collector's own `validate` command first). Queries written for the Prometheus names and labels above work only where the pipeline produces them: a different pipeline, a Prometheus using its native OTLP receiver, or a non-PromQL backend translates names and resource labels differently and was not tested here. The metric and label contract the dashboard expects, and its import steps, are in the [dashboard README](../../distro/metrics/usage/README.md).

## Tests

```bash
./tests/unit-test.sh                                                  # fast, no containers
./tests/dashboard-test.sh                                             # the canonical dashboard against the running stack and the last ./demo.sh run (read-only; --external for endpoints you did not start)
./tests/dashboard-unit-test.sh                                        # fast, no containers: the dashboard checks against a throwaway backend
./tests/dashboard-promql-test.sh                                      # the dashboard's PromQL with promtool on controlled series (needs the Prometheus image, no stack)
REGISTRY_IMAGE=<your image> ./tests/dashboard-scenarios.sh            # real stack of its own under the lifecycle runner: heartbeat-only, overflow, restart, rates, stopped process (set by phases and bounded waits, listed in the script's header; one observed run took six minutes)
REGISTRY_IMAGE=<your image> ./tests/lifecycle-test.sh                 # real stack, its own project usage-otel-test-<random>, ports 38081 and up
REGISTRY_IMAGE=<your image> ./tests/lifecycle-test.sh --keep          # the same, leaving the last case's stack running
./test-ui.sh                                                          # optional: Playwright tests of the dashboard in a real browser against the running stack and the last ./demo.sh run (first use downloads locked npm packages and Chromium; it does not change the stack, and it writes its own output directories)
./screenshots.sh                                                      # optional: the same flows, writing overview.png and filtered.png into a fresh directory
./test-ui.sh --scenario <scenarios.json> --grafana-url <url>          # optional: the process switch test on the stack kept by ./tests/dashboard-scenarios.sh --keep
./test-ui.sh --self-test                                             # optional: browser checks of what a trace stores and of the interception helpers (no Grafana, no stack, no run state)
./test-ui.sh --setup-only                                           # optional: prepare the locked tools and the browser, no run state needed
REGISTRY_IMAGE=<your image> ./tests/ci-suite.sh                       # what CI runs after the two demo.sh runs: every suite above, one phase after the other, with the artifacts of the run collected (see the guide, "In CI")
```

The test scripts need bash 3.2 or later, `curl`, `jq` and `od`; the unit tests also need `python3` (and `unit-test.sh` a shell with job control), and the lifecycle test, `dashboard-scenarios.sh` and `dashboard-promql-test.sh` need a container runtime (the last one only to run `promtool`; the others with Compose v2). `verify.sh` and `dashboard-test.sh` require the dashboard to be provisioned and, for `dashboard-test.sh`, the Grafana container to mount the canonical directory read-only; pass `--external` only for a Prometheus and Grafana that this example did not start, which checks the endpoints and nothing about the mount. The lifecycle test discards any API, URL or configuration override from your environment (it derives every endpoint from its own test ports), uses a Compose project of its own for every invocation (or `TEST_PROJECT` when you name one, which one invocation at a time can claim; a claim left by anything else is never taken over, and the message names the file to delete by hand), refuses to start when its project already has containers, volumes or networks or when it is asked to use the default project, and on any exit, signal or failure lets the script it was running settle and then purges only its own project. A signal sent to the test reaches the script it is running first, so that script ends the provider processes it started; the test waits for it up to `TEST_CHILD_SETTLE_SECONDS` (default 60) and only then kills it, and a second INT or TERM while it waits is ignored. A signal during the final cleanup releases the claim and ends the run. A cleanup that fails fails the run. Every scenario that changes something (a start, a restart, a purge, a demo) goes through one checked step: if its prerequisite fails, the whole run stops at once and nothing more is sent to any endpoint, while steps that are expected to fail stay explicit. A `--keep` run reads both inventories (containers and volumes) before it prints what is left, and when either query fails or times out it says the state is UNKNOWN and fails.

**Supervised commands.** The scripts run every runtime query and every long operation under a deadline, as a process group of its own, so a timeout, Ctrl-C or TERM ends the command and everything it started (such as the provider behind `podman compose`) and nothing else. A command that outlives its deadline is stopped and reported as timed out, and an answer that did not arrive is reported as unknown, never as no usage. When processes cannot be supervised on a host (no readable `ps -A -o pid=,pgid=,stat=`, no bash job control), the command is not started. HTTP calls are bounded by curl's own connect and total timeouts rather than by this supervision. A command that deliberately starts a session of its own is out of reach of the cancellation, and the process-group check relies on `ps`, which is not an atomic guarantee against process ID reuse. The helpers that do this live in `lib.sh`.

**State paths.** `LOCAL_DIR` is refused when any component of it is a symbolic link, however it is spelled (a trailing slash, repeated separators, `/.`, a linked directory below an ordinary base), before anything is written or purged and before the project's containers or volumes are touched; links directly under `/` (the macOS aliases `/tmp`, `/var`) are allowed, a `..` component is refused, and the default base is spelled physically. The error prints the physical path to use instead. `teardown.sh --purge` also refuses a linked marker, `projects/`, project directory or `runs/`, and creates nothing while it checks.

## Learn more

- *Usage telemetry* in the Registry documentation ([source](../../docs/modules/ROOT/pages/getting-started/assembly-usage-telemetry.adoc)), and [ADR-0003](../../adr/0003-usage-telemetry.md) and [ADR-0021](../../adr/0021-observability.md).
- [`usage-telemetry-demo`](../usage-telemetry-demo/): the SQL usage recording, with the Registry console and the governance endpoints. It is independent of this example and unchanged.
- [`otel-tracing`](../otel-tracing/): OpenTelemetry traces through Kafka, Debezium and the Registry.
