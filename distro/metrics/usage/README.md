# Schema usage dashboard for Grafana

This is an optional monitoring asset. It shows how often schemas are fetched from Apicurio Registry, as charts and a table in Grafana, using the OpenTelemetry counter the Registry exports. The Registry does not need it, does not call Grafana or Prometheus, and stores no usage data for it; the dashboard only reads a Prometheus compatible backend that you already run. The Registry side is described in the *Usage telemetry* section of the Registry documentation ([source](../../../docs/modules/ROOT/pages/getting-started/assembly-usage-telemetry.adoc)).

There is one dashboard, [`grafana/schema-usage.json`](grafana/schema-usage.json). The runnable example in [`examples/usage-telemetry-otel`](../../../examples/usage-telemetry-otel) mounts this same file read-only into its Grafana; it keeps no copy. You can import the file into your own Grafana in a minute, without deploying the example.

This dashboard is not the Registry health and SLO dashboard of [`../grafana.json`](../grafana.json) and does not replace it.

## What it shows

Pick the Registry process, then read:

| Panel | What it answers | How to read it |
|---|---|---|
| Cumulative fetches (selected series) | The sum of the counts of the streams in the table below, each stream counted once | A count since the process started, as last exported inside the selected time range. Not a total over the range |
| Overflow (detail dropped) | Fetches whose client, identifier and operation were collapsed into the overflow series once the process hit its series limit | Never filtered by client or identifier, because the overflow series keeps neither. No series means none was observed, not zero. The overflow series is one stream of the selected process, counted once even when two Collector scrape routes report it |
| Export age at the range end | `time()` minus the measurement time of the newest `jvm_cpu_count` heartbeat of the process | Evaluated at the end of the selected range, so for an absolute range in the past it is the age at that moment, not now. No value means UNKNOWN: the process may have stopped, the pipeline may be down, or no heartbeat is exported. It is never zero use |
| Estimated fetch rate by operation | `rate()` of the counter, summed by operation (SERIALIZE, DESERIALIZE, UNKNOWN) | An estimate from sampled counters, as fetches per second. Activity before a series' first sample is not visible, and a series seen in a single sample has no rate. The legend shows the last and maximum values |
| Cumulative fetches by client, identifier and operation | One row per stream of the process | The exact last exported count of each series in the selected range |
| Process candidates with a heartbeat sample in the range | Every `service_instance_id` for which Prometheus holds at least one `jvm_cpu_count` sample in the selected range, one row per process however many Collector scrape routes report it | Candidates for the Registry process filter, nothing more. The range excludes its start and includes its end, like a PromQL range selector. It says nothing about when the process last exported, whether it is running or whether it was used; the export age panel answers the first for the selected process. `jvm_cpu_count` is a generic JVM metric, so another JVM service that exports it into the same Prometheus is listed too: use a Registry-scoped datasource or pipeline if that can happen |

The filters above the panels:

- **Prometheus datasource** is any Prometheus compatible datasource of your Grafana. Nothing is bound to a datasource name or UID.
- **Registry process** is exactly one `service_instance_id`. The Registry generates one (a random UUID) only when none is configured; an ID you configure, for example through `OTEL_RESOURCE_ATTRIBUTES`, is exported unchanged and must be unique for every process start and every installation, or the counters of two processes merge. There is no All choice, so the counters of two processes are never combined, even when a restarted in-memory Registry hands out the same IDs again. The filter lists processes that exported usage and processes that have exported only the heartbeat so far (a new process then shows an export age and no usage). Several processes in the range are listed in the filter in no particular order, and the candidate table lists those that have a heartbeat sample in the range. A process that restarts is a different choice.
- **Client ID (self-reported)**, **Identifier type**, **Operation**, **Global ID** and **Content ID** narrow the table, the stat and the rate chart. A global ID identifies one schema version. A content ID identifies content that several versions can share. The two numbering sequences are independent, so the same number in each is a different identifier, and the two ID filters never match across types: the Global ID filter applies only to `global_id` series and the Content ID filter only to `content_id` series. Use the Identifier type filter to look at one type only.

## Streams, scrape routes and processes

The unit of counting is one logical stream: the tuple (`service_instance_id`, client ID, identifier type, identifier value, operation), plus the overflow stream of a process. Prometheus `job` and `instance` describe the route by which the Collector was scraped, not the Registry, so the same stream can be seen under two routes when the Collector is replaced, scraped by two Prometheus jobs or relabeled, one after the other or at the same time. The dashboard counts a stream once: it takes the highest cumulative value over the scrape routes (cumulative counters only grow within a process, so the highest value is the latest), and then sums streams. A stream seen as 8 through one Collector and as 10 through another is 10, not 18. Distinct processes, clients, identifier types, identifiers and operations stay distinct.

The rate chart applies the same rule: the rate of a stream is the highest rate over its scrape routes, and the chart sums the streams by operation. When a stream moves from one scrape route to another, the increase between the last sample of the old route and the first sample of the new one is not visible to the rate; that is part of why the rate is an estimate.

The Registry preserves a `service_instance_id` that you supply. The dashboard relies on it being unique for every process start and every installation. If two processes, or one process before and after a restart, export the same ID, their counters are indistinguishable and the dashboard cannot separate them.

## What the numbers are, and are not

- A count is a successful schema fetch (`GET /ids/globalIds/{id}` or `/ids/contentIds/{id}`) that carried a valid `X-Registry-Client-Id` header. It is not a Kafka message count, not a count of unique consumers, and not a billing total. Apicurio SerDes cache schemas, so a busy client can fetch rarely.
- The client ID is a header value that the client chooses. It is not an authenticated identity.
- Missing data is not zero. A series that is absent for a selection means nothing was observed for it, not that the schema is unused, not that it is safe to delete. The dashboard says "No observations for this selection" in that case, and a failing datasource shows a panel error instead of an empty result.
- The process filter lists the values of `service_instance_id` of both the usage metric and the generic JVM metric `jvm_cpu_count`. Another JVM service that exports `jvm_cpu_count` with a `service_instance_id` into the same Prometheus appears as a process too. The filter's candidates come from Prometheus' label values for the selected time range, which can include a process slightly outside it; the candidate table is the one that follows the range exactly (a short life that ends in a stale marker is listed, because it is counted from the samples Prometheus holds, not from a sampled timestamp). To limit the candidates to Registry processes, use a Registry-scoped datasource or pipeline; the dashboard cannot tell them apart.
- Export can lose observations: the Registry exports on an interval, and a process that stops before a successful export loses what it had not exported. The dashboard does not infer first use, last use, ACTIVE or STALE state, schema drift or deprecation.
- Rates and the table are read from samples. The rate chart is an estimate, and the table is the exact last cumulative value that Prometheus holds for each series in the range.

## The metric and label contract

The queries need these series in a Prometheus compatible backend. They are what an OpenTelemetry Collector with the Prometheus exporter produces from the Registry's OTLP export, as set up in the example.

| Series | Labels | Meaning |
|---|---|---|
| `apicurio_usage_schema_fetches_total` | `apicurio_registry_client_id`, `apicurio_registry_identifier_type` (`global_id` or `content_id`), `apicurio_registry_identifier_value`, `apicurio_registry_operation` (`SERIALIZE`, `DESERIALIZE` or `UNKNOWN`), `service_instance_id` | Cumulative successful schema fetches of one Registry process |
| `apicurio_usage_schema_fetches_total` with `otel_metric_overflow="true"` | `service_instance_id` only | The overflow series: fetches whose detail was collapsed |
| `jvm_cpu_count` | `service_instance_id` | A heartbeat, one sample per export of each Registry process. Its sample time must be the time the Registry measured it |

- `service_instance_id` must be a plain label on every series. In the example Collector configuration a `transform` processor copies the resource attribute `service.instance.id` to the data point and drops the other resource attributes, so one Registry process is exactly one value.
- Prometheus adds `job` and `instance`. They identify the Collector that Prometheus scrapes, not the Registry, and the dashboard ignores them.
- The export age is only meaningful if the heartbeat keeps its original measurement time: the Collector's Prometheus exporter needs `send_timestamps: true` and Prometheus needs `honor_timestamps: true` for that scrape job (the example sets both). Without them every scrape looks fresh. A Collector keeps serving the last point of a stopped process for the exporter's `metric_expiration` (five minutes in the example), so a healthy scrape of the Collector only proves the Collector is up.
- The dashboard declares no other series. The example drops everything else on the Collector.

### Compatibility

The dashboard was checked with Grafana 13.3.1, Prometheus v3.16.0 and OpenTelemetry Collector contrib 0.139.0, with the Registry built from the same change. Its JSON uses dashboard schema version 42 and only the built-in `stat`, `timeseries`, `table` and `text` panels and a datasource template variable; no plugin is needed. Older Grafana versions were not tried, and a newer schema version cannot be assumed to import into older Grafana. The queries use `label_values`, `last_over_time`, `count_over_time`, `rate`, `timestamp`, the `max by` and `group by` aggregations and the `or` operator; there is no subquery. Other PromQL backends (Mimir, Thanos, VictoriaMetrics, the Prometheus native OTLP receiver) were not tested: they can translate metric and label names differently (for example with the resource attributes promoted under other names), and queries written for the names above work only where the pipeline produces them. A non-PromQL backend would need its own dashboard.

## Import it into your Grafana

The file has no import inputs and no fixed datasource, so any of these works and the datasource is chosen afterwards in the dashboard's first filter.

**Grafana UI.** Dashboards, New, Import, then upload `schema-usage.json`, choose a folder and select Import. If a dashboard with the same UID (`apicurio-schema-usage`) exists, Grafana asks whether to replace it. Then pick your Prometheus datasource, and a Registry process.

**HTTP API.**

```bash
jq '{dashboard: ., overwrite: true}' distro/metrics/usage/grafana/schema-usage.json \
  | curl -sS -X POST "$GRAFANA_URL/api/dashboards/db" -H "Authorization: Bearer $GRAFANA_TOKEN" -H 'Content-Type: application/json' -d @-
```

**File provisioning.** Put the file in a directory that Grafana reads and point a dashboard provider at it:

```yaml
apiVersion: 1
providers:
  - name: apicurio-usage
    type: file
    disableDeletion: true
    allowUiUpdates: false
    updateIntervalSeconds: 30
    options:
      path: /var/lib/grafana/dashboards/apicurio-usage
```

The example does this with its own [provider file](../../../examples/usage-telemetry-otel/config/grafana/provisioning/dashboards/usage.yml) and a read-only bind mount of the `grafana` directory next to this file. A provisioned dashboard cannot be saved from the UI; edit the file instead. With several Prometheus datasources, check the Prometheus datasource filter first: it does not follow your default datasource (in the check made here, Grafana 13.3.1 preselected `Broken Prometheus` over a default datasource named `Ops Prometheus`; the rule Grafana applies was not investigated).

## Notes for maintainers

- Edit `grafana/schema-usage.json` only; the example and any copy elsewhere must point at it. The checks live in `examples/usage-telemetry-otel/tests`: `dashboard-promql-test.sh` proves the PromQL with promtool on controlled series (scrape routes, rates, the candidates' range bounds and stale markers, the selector) and rejects mutants; `dashboard-test.sh` checks the file for demo specific values, the provisioned copy and the mount, and runs every panel query through Prometheus and through Grafana (the rate chart through its own range request) for the last demo run or for scenarios; `dashboard-scenarios.sh` runs the dashboard-scenarios case of `lifecycle-test.sh` (heartbeat-only process, overflow, restart with reused identifiers, steady rates, a stopped process after Prometheus and the Collector forgot it) under that runner's ownership; `dashboard-unit-test.sh` is the fast test of the helpers, the exit statuses and the mutation experiment. `dashboard-test.sh` exits 3 when a query failed, which proves nothing about the values it could not read. Run them after every change of the file.
- Keep the colors of the three operations fixed (SERIALIZE `#3987e5`, DESERIALIZE `#d95926`, UNKNOWN `#199e70`), so a filter never repaints the survivors. They are the first three slots of a palette validated for the dark and the light Grafana themes.
- This dashboard does not cover the health and SLO signals of the Registry (tracked separately), and it does not add queries, storage or a Prometheus client to the Registry.
