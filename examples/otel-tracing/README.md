# OpenTelemetry tracing

A complete, local example of Apicurio Registry's OpenTelemetry support, and of following one request
through the Registry across two common data paths:

- **Kafka serdes:** a REST call to the **greeting producer** serializes an Avro message (the serializer
  registers its schema in the Registry) and sends it to Kafka; the **greeting consumer** reads it.
  One trace covers the producer, the Registry and the consumer.
- **Debezium CDC:** a REST call to the **order service** inserts a row in PostgreSQL; **Debezium
  Server** captures it, registers its Avro schema in the Registry and sends it to Kafka; the **CDC
  consumer** reads it and looks the schema up in the Registry. One trace covers the order service,
  Debezium, the Registry and the CDC consumer.
- **Apicurio Registry** traces its own REST API and storage layer, and exports metrics, through
  the **OpenTelemetry Collector** to **Jaeger**.

`demo.sh` does not only start things: it reads every trace back from Jaeger's query API and checks
which services' spans it contains and how they are linked.

**New to this? Follow the [step-by-step guide](GUIDE.md).** It walks through every trace, with
commands, expected output and what to look for in the Jaeger UI, in about 20 minutes.

## Quick start

Requires Docker (or Podman) with Compose v2, `curl`, `jq`, `openssl`, and JDK 21 or later for the
first build.

```bash
./build.sh               # builds the Java applications from this checkout (a few minutes the first time)
docker compose up -d     # builds their images and starts everything
./demo.sh                # sends requests through both paths and checks their traces in Jaeger
```

`demo.sh` prints one line per check and ends with `All checks passed.` (it exits non-zero if a check
fails). Then open Jaeger at <http://localhost:16686>.

Stop and remove everything with `docker compose down -v`.

## What demo.sh checks

1. **The Registry's own spans.** A request with a `traceparent` header produces a server span named
   after the route, in the caller's trace, with `apicurio.registry.path.*` attributes and storage
   spans under it; a failed lookup (404) has an error storage span with the exception.
2. **Kafka serdes, one trace:** `POST /greetings` (producer) → `create-greeting` → `greetings publish` →
   `serde.serialize` (the serializer's own span) → `serde.resolve_schema` (the schema lookup) → `POST`
   to the Registry →
   `POST /apis/registry/v3/groups/{groupId}/artifacts` (Registry, with storage spans) and, in the
   same trace, `greetings process` (consumer, child of the publish span) → `process-greeting-message`.
3. **Errors on spans:** a failed operation has status `ERROR`, attributes and the recorded exception.
4. **Debezium CDC, one trace:** `POST /orders` → `create-order-entity`, whose context the order
   service stores in the row; Debezium's `debezium-cdc-orders` span and the CDC consumer's
   `process-cdc-event` span both continue it, and the consumer's schema lookup
   (`GET /apis/registry/v3/ids/globalIds/{globalId}`) appears as a child in the Registry. Debezium's
   schema registration is in the trace of the first order it captures. An update
   (`PUT /orders/{id}/status`) gets a trace of its own the same way.
5. **One backend for everything:** Jaeger knows all six services, and the Collector exposes the
   Registry's metrics (artifacts created, HTTP requests per route) in Prometheus format.

## Services

| Service                 | URL                    | Purpose                                                 |
|-------------------------|------------------------|---------------------------------------------------------|
| Jaeger                  | http://localhost:16686 | Trace UI and query API                                  |
| Apicurio Registry API   | http://localhost:8081  | Schema registry, OpenTelemetry enabled, SQL storage     |
| Greeting producer       | http://localhost:8084  | `POST /greetings?name=...` sends an Avro greeting       |
| Greeting consumer       | http://localhost:8085  | `GET /consumer/greetings/batch` returns what it read    |
| Order service           | http://localhost:8086  | `POST /orders`, `PUT /orders/{id}/status`               |
| CDC consumer            | http://localhost:8087  | `GET /cdc/events/batch` returns the change events       |
| OpenTelemetry Collector | http://localhost:8889  | `/metrics`: the metrics it received, Prometheus format  |

Kafka, PostgreSQL and Debezium Server are only reachable inside the Compose network.

## Files

| File | Purpose |
|------|---------|
| [`GUIDE.md`](GUIDE.md) | Step-by-step walkthrough, presenter notes and troubleshooting |
| [`docker-compose.yml`](docker-compose.yml) | The services; the Registry's OpenTelemetry configuration is in its `environment` |
| [`otel-collector-config.yaml`](otel-collector-config.yaml) | Collector pipelines: traces to Jaeger, metrics to Prometheus format |
| [`build.sh`](build.sh) | Builds the Java applications from this checkout |
| [`demo.sh`](demo.sh) | Sends the requests and checks the traces |
| [`producer/`](producer), [`consumer/`](consumer) | Quarkus apps with the Avro serdes, wrapped with the OpenTelemetry Kafka instrumentation |
| [`order-service/`](order-service) | Quarkus app that stores each request's trace context in the `tracingspancontext` column |
| [`debezium-server/`](debezium-server) | Debezium Server image (OpenTelemetry Java agent plus the SMT) and its configuration |
| [`debezium-converter/`](debezium-converter) | `OtelActivateTracingSpan`, the SMT that continues the order's trace in Debezium |
| [`cdc-consumer/`](cdc-consumer) | Quarkus app that reads the change events and continues the order's trace from the row |
| [`init-db.sql`](init-db.sql) | The `orders` table and the Registry's database |

## Configuration

The Registry is built with OpenTelemetry, but the SDK is off by default. This example turns it on with:

| Variable | Value | Why |
|---|---|---|
| `QUARKUS_OTEL_SDK_DISABLED` | `false` | Enables traces, metrics and logs export (the signals themselves are build-time settings, already on) |
| `QUARKUS_OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4317` | Where to export (OTLP over gRPC, the default protocol) |
| `QUARKUS_OTEL_TRACES_SAMPLER_ARG` | `1.0` | The default sampler, `parentbased_traceidratio`, keeps 10% of the traces that start in the Registry; requests with a sampled `traceparent` are always kept |
| `QUARKUS_OTEL_METRIC_EXPORT_INTERVAL` | `5s` | The default is 60s |

The service name defaults to `apicurio-registry` (`quarkus.otel.service.name`). With the operator,
set the same through `spec.app.otel` (see *Configuring Registry observability with OpenTelemetry*
in the operator documentation).

## Notes

- **How the trace crosses Debezium.** Debezium does not read trace context from the database, so the
  order service stores it (W3C `traceparent`) in the `tracingspancontext` column. The example's SMT
  reads it, starts the `debezium-cdc-orders` span and keeps it current while Debezium converts the
  record, so the OpenTelemetry Java agent puts Debezium's calls to the Registry in that trace. The
  CDC consumer reads the column too. Debezium's Kafka sink sends the record later, on another
  thread, so its `publish` span and the consumer's Kafka `process` span form a separate trace.
- **Consumer-side lookups are in their own trace.** Deserialization (`serde.deserialize`, and the
  schema lookup on a cache miss) runs inside `poll()`, before the record's trace context is
  extracted, so it is not part of the producer's trace.
- **Debezium uses the Registry's v2 API.** Debezium Server bundles the Apicurio Registry 2.6 serdes;
  its `apicurio.registry.url` is the Registry's base URL, without `/apis/registry/...`.
- **Other ports:** see [Using other ports](GUIDE.md#using-other-ports).
- **Local build:** the example uses the `latest-snapshot` registry image. To run a locally built
  registry, build it (`./mvnw install -pl distro/docker -am -DskipTests`), build
  `distro/docker/target/docker/Dockerfile.jvm` as an image, and start the example with
  `REGISTRY_IMAGE=<that image> docker compose up -d`.
- **Local only:** 100% sampling, a single-node Kafka broker without security, database passwords in
  the Compose file, Jaeger's in-memory storage.

## Learn more

- *Registry observability with OpenTelemetry* in the Registry documentation
  ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc)).
- *Configuring Registry observability with OpenTelemetry* in the operator documentation
  ([source](../../operator/docs/modules/ROOT/partials/proc-registry-observability-otel.adoc)).
- [ADR-0021](../../adr/0021-observability.md): the observability design: spans, attributes, metrics
  and their limits.
- [ADR-0007](../../adr/0007-kafka-serdes.md): the Kafka serdes, whose `serde.serialize` and
  `serde.deserialize` spans appear here.
- [Debezium distributed tracing](https://debezium.io/documentation/reference/stable/integrations/tracing.html).
