# ADR-0021: Observability

- **Status:** Accepted (documents the current implementation).
- **Decision date:** 2026-11-19.
- **Author:** Apicurio Registry maintainers.
- **Tracking:** [#8211](https://github.com/Apicurio/apicurio-registry/issues/8211),
  [#7150](https://github.com/Apicurio/apicurio-registry/issues/7150).
- **Scope:** Traces, metrics and logs from the registry server and the Java serdes, and how the
  operator turns them on. Not covered: usage telemetry ([ADR-0003](0003-usage-telemetry.md)), whose
  gauges are only listed here, and health checks.

## Reading this document

- Start with [the decision](#decision).
- For deployment, see [configuration](#configuration) and the
  [user documentation](../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc).
- For a running system, see the [example](../examples/otel-tracing/).
- For code changes, see the [source map](#source-map) and [verification](#example-and-verification).

## Context

A schema lookup is one hop in someone else's request: a producer serializing a record, a Debezium
connector converting a row, a consumer reading it. When that hop is slow or fails, the operator needs
to see it in the caller's trace, and to see which storage call inside the registry was responsible.
The registry also needs counters that a monitoring system can alert on, and they must count every
write once, whichever API it came through and however many replicas there are.

## Decision

1. **OpenTelemetry is built in and off at runtime.** The server uses the Quarkus OpenTelemetry
   extension. Traces, metrics and logs are enabled at build time (`quarkus.otel.traces|metrics|logs.enabled`
   are build-time properties); `quarkus.otel.sdk.disabled=true` switches the SDK off, and setting it to
   `false` is the one runtime switch for all three signals. Export is OTLP, gRPC to
   `http://localhost:4317` by default.
2. **HTTP server spans come from Quarkus; the registry adds attributes.** Quarkus creates the server
   span (named `<method> <route>`, for example `GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}`)
   and continues an incoming W3C `traceparent`. `TracingFilter` adds `apicurio.registry.request.path`,
   the path parameters as `apicurio.registry.path.group_id|artifact_id|version`, and the
   `X-Registry-*` headers as `apicurio.registry.group_id|artifact_id|version|artifact_type`.
3. **Every storage call is a span.** `StorageTracingInterceptor` wraps every `@StorageMetricsApply`
   method in an `INTERNAL` span named `storage.<method>`, with `apicurio.registry.storage.method`,
   `.class` and `.method_signature`. A failure sets status `ERROR` with the exception message and
   records the exception. With the SDK off, the global tracer is a no-op.
4. **The serdes trace themselves when the application has OpenTelemetry.** `SerDesTracer` creates
   spans only if `GlobalOpenTelemetry` has a real tracer provider; the serdes have no switch of their
   own and no dependency on an SDK. See [serdes spans](#serdes-spans).
5. **Business counters are OpenTelemetry metrics; timings are Micrometer.** `OTelMetricsProvider`
   holds the `apicurio.*` counters (exported over OTLP). Micrometer, exported in Prometheus format at
   `/metrics` on the management port (9000), keeps the `rest.requests` and `storage.method.call`
   timers, datasource metrics and cache meters. See [metrics](#metrics).
6. **Artifact and version counters are recorded in storage.** `MetricsStorageDecorator` counts
   successful, non-dry-run creates and deletes for every API (v3, v2, Confluent-compatible, MCP). With
   KafkaSQL only the replica that receives the write runs the decorators, so a write is counted once,
   by that replica.
7. **The operator only sets environment variables.** `spec.app.otel` (`enabled`, `endpoint`,
   `protocol`, `traceSamplingRatio`) becomes `QUARKUS_OTEL_SDK_DISABLED=false`, the OTLP endpoint and
   protocol, and, when a ratio is given, `parentbased_traceidratio` with that ratio. Nothing is set
   when `enabled` is not `true`.

### Why OpenTelemetry for traces and new counters, but Micrometer kept

OpenTelemetry is the only vendor-neutral way to join the registry's spans to the caller's trace, and
OTLP lets one collector receive traces, metrics and logs. The Micrometer meters and the Prometheus
endpoint predate it and existing dashboards scrape them, so they stay; the two coexist.

## Serdes spans

| Span | Where | Attributes |
|---|---|---|
| `serde.serialize` | `AbstractSerializer.serializeData` | `messaging.destination.name`, `apicurio.registry.serde.operation`, `.data_size` (bytes written), `.cache_hit`, `apicurio.registry.group_id`, `.artifact_id`, `.version` |
| `serde.deserialize` | `AbstractDeserializer.deserializeData` (ID in the payload) and `readData(topic, data, reference)` (ID in the headers) | as above, `data_size` is the bytes read |
| `serde.resolve_schema` | A schema lookup that misses the serde's cache, on either side, including the deserializer's fallback lookup | `messaging.destination.name`, `apicurio.registry.serde.operation` |

All are `INTERNAL`, children of the current span, and record errors as storage spans do. A producer
whose Kafka client is instrumented gets `publish` → `serde.serialize` → `serde.resolve_schema` → the
HTTP call → the registry's server span in one trace. The consumer side is different: deserialization
runs inside `poll()`, before the record's trace context is extracted, so `serde.deserialize` is in a
trace of its own, not the producer's.

The HTTP call to the registry carries the trace context if the application instruments its HTTP
client, or if `apicurio.registry.otel.enabled=true` (default `false`), which makes the Java SDK
(`OTelRequestAdapterDecorator`) inject the trace context through the globally configured propagator.
That needs a W3C propagator to be registered (the default one is a no-op) and a current span context:
then every request carries `traceparent`, and `tracestate` when the context has one.

## Metrics

| Metric (OTLP) | Kind | Attributes | Recorded by |
|---|---|---|---|
| `apicurio.artifacts.created`, `apicurio.artifacts.deleted`, `apicurio.versions.created` | Counter | `apicurio.registry.group_id`, `.artifact_type` | `MetricsStorageDecorator` |
| `apicurio.schema.validations` | Counter | `artifact_type`, `result` | `RulesServiceImpl` (validity rule) |
| `apicurio.rules.evaluated` | Counter | `operation` (rule type), `result` | `RulesServiceImpl` |
| `apicurio.search.requests` | Counter | `operation` (search kind) | v3 `SearchResourceImpl` |
| `apicurio.authz.decisions` | Counter | `result` (`allow`/`deny`), `entity_type`, `operation` | `GrantsAccessController` ([ADR-0005](0005-per-resource-authorization.md)) |
| `apicurio.usage.schemas.active`, `.stale`, `.dead` | Up-down counter | none | `UsageAggregationJob` ([ADR-0003](0003-usage-telemetry.md)) |
| `apicurio.usage.schema.fetches` | Counter | `client_id`, `identifier_type`, `identifier_value`, `operation` | `UsageTelemetryFilter`, only while `apicurio.usage.metrics.enabled` is true ([ADR-0003](0003-usage-telemetry.md)) |
| `apicurio.iceberg.*` | Counter | `operation`, `result`, `entity_type`, `error_type` | `IcebergMetricsService` |

Attribute names above are short for `apicurio.registry.<name>`. There are no OpenTelemetry
histograms of the registry's own; request durations come from Quarkus (`http.server.request.duration`
per route, over OTLP) and Micrometer (`rest.requests`, tagged with method, route template and status
group, configurable with `apicurio.metrics.rest.*`; `storage.method.call`, tagged with method and
success).

## Logs

The access log is on (`quarkus.http.access-log.enabled=true`, one `apicurio-registry.access` line per
request), and the operator sets `QUARKUS_HTTP_ACCESS_LOG_ENABLED=true` as well. With the SDK on, logs
are also exported over OTLP; with `QUARKUS_LOG_CONSOLE_JSON=true` the console entries carry the trace
and span IDs.

## Debezium and change data capture

Not registry code: the example shows the pattern. The service that writes a row stores its W3C
context in a column (`tracingspancontext`); an SMT (`OtelActivateTracingSpan`) starts a span from it
while Debezium converts the record, so the OpenTelemetry Java agent puts Debezium's registry calls in
that trace, and the CDC consumer continues it from the same column. Debezium's Kafka publish happens
later on another thread and is a separate trace.

## Configuration

| Property | Default | Meaning |
|---|---|---|
| `quarkus.otel.sdk.disabled` | `true` | Runtime switch for all signals |
| `quarkus.otel.exporter.otlp.endpoint` | `http://localhost:4317` | Collector endpoint |
| `quarkus.otel.exporter.otlp.protocol` | `grpc` | Or `http/protobuf` (port 4318) |
| `quarkus.otel.service.name` | `${apicurio.app.id:apicurio-registry}` | Service name |
| `quarkus.otel.traces.sampler`, `.sampler.arg` | `parentbased_traceidratio`, `0.1` | 10% of traces that start in the registry; a sampled caller is always followed |
| `quarkus.otel.resource.attributes` | `service.version`, `deployment.environment` | Resource attributes |
| `quarkus.otel.instrument.kafka` | `true` | Kafka client spans (KafkaSQL) |
| `apicurio.metrics.rest.enabled`, `.path-filter-pattern`, `.method-tag-enabled`, `.path-tag-enabled`, `.explicit-status-codes-list` | `true`, `/apis/.*`, `true`, `true`, `401` | Micrometer REST timers |
| `apicurio.registry.otel.enabled` (serdes, Java SDK) | `false` | Inject trace context into registry calls |
| `spec.app.otel` (operator) | none | See decision 7 |

## Source map

| Layer | Source | Responsibility |
|---|---|---|
| REST | [`TracingFilter`](../app/src/main/java/io/apicurio/registry/rest/TracingFilter.java) | Registry attributes on the server span |
| Storage | [`StorageTracingInterceptor`](../app/src/main/java/io/apicurio/registry/metrics/StorageTracingInterceptor.java), [`StorageMetricsInterceptor`](../app/src/main/java/io/apicurio/registry/metrics/StorageMetricsInterceptor.java) | `storage.*` spans and timers |
| Attributes, config docs | [`OTelAttributes`](../app/src/main/java/io/apicurio/registry/observability/OTelAttributes.java), [`OtelConfigProperties`](../app/src/main/java/io/apicurio/registry/observability/OtelConfigProperties.java) | Attribute keys; `@Info` for the `quarkus.otel.*` properties |
| Metrics | [`OTelMetricsProvider`](../app/src/main/java/io/apicurio/registry/metrics/OTelMetricsProvider.java), [`MetricsStorageDecorator`](../app/src/main/java/io/apicurio/registry/storage/decorator/MetricsStorageDecorator.java), [`RestMetricsResponseFilter`](../app/src/main/java/io/apicurio/registry/metrics/RestMetricsResponseFilter.java) | Counters, write counting, REST timers |
| Serdes | [`SerDesTracer`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/tracing/SerDesTracer.java), [`SerDesAttributes`](../serdes/generic/serde-common/src/main/java/io/apicurio/registry/serde/tracing/SerDesAttributes.java) | Serdes spans |
| Java SDK | [`OTelRequestAdapterDecorator`](../java-sdk/common/src/main/java/io/apicurio/registry/client/common/OTelRequestAdapterDecorator.java) | Context propagation to the registry |
| Operator | [`OTel`](../operator/controller/src/main/java/io/apicurio/registry/operator/feat/OTel.java), [`OTelSpec`](../operator/model/src/main/java/io/apicurio/registry/operator/api/v1/spec/OTelSpec.java) | `spec.app.otel` to environment |

## Limitations and open work

1. Deleting a group's artifacts (`deleteArtifacts`) and imports are not counted in
   `apicurio.artifacts.*` or `apicurio.versions.created`.
2. With KafkaSQL each replica exports only the writes it received: totals are the sum across
   replicas.
3. The Kafka serializer's header path (`apicurio.registry.headers.enabled=true`) resolves the schema
   without `serde.serialize` or `serde.resolve_schema` spans ([#11340](https://github.com/Apicurio/apicurio-registry/issues/11340)). A Protobuf record recovered
   by the deserializer's fallback keeps an `ERROR` `serde.deserialize` span ([#11341](https://github.com/Apicurio/apicurio-registry/issues/11341)).
4. Consumer-side deserialization is not in the producer's trace, and Debezium's publish is a separate
   trace (see above).
5. The `StorageMetricsStore` counts are not exported
   ([#4111](https://github.com/Apicurio/apicurio-registry/issues/4111)); non-Java SDKs have no
   instrumentation ([#8406](https://github.com/Apicurio/apicurio-registry/issues/8406)); serdes
   metrics ([#8156](https://github.com/Apicurio/apicurio-registry/issues/8156)); dashboards and alerts
   ([#11016](https://github.com/Apicurio/apicurio-registry/issues/11016)); agent traces
   ([#11088](https://github.com/Apicurio/apicurio-registry/issues/11088)).
6. The operator's `spec.app.otel` has no test of its own.

## Alternatives

| Alternative | Decision and trade-off |
|---|---|
| Micrometer only | Not chosen for tracing: no spans or context propagation. Kept for the existing timers and Prometheus endpoint |
| Moving all metrics to OpenTelemetry | Not done: existing Prometheus dashboards would break; the cost is two metric systems |
| Vendor SDKs or agents | Not used: OTLP reaches any backend through a collector |
| OpenTelemetry Java agent on the server | Not used: the Quarkus extension gives the same HTTP spans at build time, without an agent in the image |
| Manual spans in each REST method | Not chosen: one filter and one interceptor cover every endpoint and storage method |
| A serdes switch for spans | Not chosen: spans follow the application's own OpenTelemetry setup; only propagation over HTTP has a switch |

## Consequences

- **Benefits:** a schema lookup appears inside the caller's trace down to the storage call; one switch
  turns on all signals; counters are correct whichever API wrote.
- **Costs:** two metric systems; storage spans for every storage call add volume (the default 10%
  sampling bounds it); the signal set is fixed at build time.

## Example and verification

- **Example:** [`examples/otel-tracing`](../examples/otel-tracing/): `./build.sh && docker compose up -d && ./demo.sh`
  reads the traces back from Jaeger and checks the registry's server span in the caller's trace with
  `apicurio.registry.path.*` attributes and storage spans under it, an error storage span for a 404,
  the producer trace through `serde.serialize` and `serde.resolve_schema` to the registry, the Debezium
  trace continued from the row, the header-based `serde.deserialize` span, and that
  `apicurio.artifacts.created` covers every AVRO artifact registered through v2 and v3. CI runs it.
- **User documentation:**
  [`assembly-configuring-the-registry.adoc`](../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc),
  *Registry observability with OpenTelemetry*; operator:
  [`proc-registry-observability-otel.adoc`](../operator/docs/modules/ROOT/partials/proc-registry-observability-otel.adoc).

| Concern | Tests to read |
|---|---|
| Server spans and attributes | `OpenTelemetryTracingTest`, `OpenTelemetryHttpStatusCodeTest`, `TracingFilterTest` (app) |
| Storage spans | `StorageTracingInterceptorTest` (app) |
| Overhead | `OpenTelemetryPerformanceTest`, `OpenTelemetryPerformanceEnabledTest` (app) |
| Counters | `OTelMetricsProviderTest`, `MetricsStorageDecoratorTest`, `ArtifactMetricsAcrossApisTest`, `GrantsAccessControllerTest` (app) |
| Serdes spans | `SerDesTracerTest`, `DeserializerTracingTest` (serde-common) |
| SDK propagation | `OTelRequestAdapterDecoratorTest` (java-sdk/common) |

```bash
./mvnw test -pl app -Dtest='OpenTelemetry*Test,TracingFilterTest,StorageTracingInterceptorTest,OTelMetricsProviderTest,MetricsStorageDecoratorTest,ArtifactMetricsAcrossApisTest'
./mvnw test -pl serdes/generic/serde-common -Dtest='SerDesTracerTest,DeserializerTracingTest'
```

## History

| Milestone | Result |
|---|---|
| [#698](https://github.com/Apicurio/apicurio-registry/pull/698) | Micrometer REST request metrics with status code groups |
| [#7031](https://github.com/Apicurio/apicurio-registry/pull/7031) | OpenTelemetry: `TracingFilter`, storage spans, `OTelMetricsProvider`, operator `spec.app.otel` |
| [#7199](https://github.com/Apicurio/apicurio-registry/pull/7199) | `quarkus.otel.*` properties in the generated configuration reference |
| [#8408](https://github.com/Apicurio/apicurio-registry/pull/8408) | `SerDesTracer` |
| [#11269](https://github.com/Apicurio/apicurio-registry/pull/11269) | One verified example, with the Debezium path |
| [#11286](https://github.com/Apicurio/apicurio-registry/pull/11286) | Artifact and version counters moved to storage, for every API |
| [#11287](https://github.com/Apicurio/apicurio-registry/pull/11287) | Header-based deserialization and cache-miss lookups traced |
| This ADR | First written record |
| [#11381](https://github.com/Apicurio/apicurio-registry/pull/11381) | Configuration reference lists the effective sampler defaults (`parentbased_traceidratio`, `0.1`), [#11339](https://github.com/Apicurio/apicurio-registry/issues/11339) |
