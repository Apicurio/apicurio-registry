# OpenTelemetry tracing: step-by-step guide

This guide follows requests through Apicurio Registry with a running example, and shows each trace
in Jaeger. Follow it on your own, or use it as the script for a live demo. Run the steps in order.

**Time:** about 20 minutes. **You will see:**

- the Registry's own spans for its REST API and storage layer;
- one trace from a Kafka producer, through the Registry, to a Kafka consumer;
- how errors are recorded on spans;
- one trace from an application's database write, through Debezium and the Registry, to a CDC consumer;
- the Registry's metrics, exported through the same Collector.

For the reference documentation, see *Registry observability with OpenTelemetry* in the Registry
documentation ([source](../../docs/modules/ROOT/pages/getting-started/assembly-configuring-the-registry.adoc)).

## Before you start

You need:

- Docker with Docker Compose v2, or Podman with a Compose provider;
- JDK 21 or later (to build the applications);
- `curl`, `jq` and `openssl`;
- a shell: bash or zsh;
- a browser, for Jaeger;
- free ports 8081, 8084 to 8087, 8889 and 16686. If they are in use, see
  [Using other ports](#using-other-ports).

Run every command from this directory:

```bash
cd examples/otel-tracing
```

## Step 1: Build and start the example

The applications use the Apicurio Registry serdes of this checkout, which are not published, so
build them first:

```bash
./build.sh
docker compose up -d
docker compose ps --format '{{.Name}}: {{.Status}}'
```

`docker compose up` builds five images (four Quarkus apps and Debezium Server with the
OpenTelemetry Java agent) and starts ten containers. Wait until the apps are up (about a minute).

## Step 2: Check everything works

```bash
./demo.sh
```

Expected output (abridged):

```
1. The Registry traces its own REST API:
  ok    GET /system/info with a traceparent header                             200
  ok    the Registry's server span joins the caller's trace                    apicurio-registry: GET /apis/registry/v3/system/info
...
4. Debezium CDC: one trace from the order service, through Debezium and the Registry, to the CDC consumer:
  ok    debezium: the tracing SMT continues the trace from the row             debezium-server: debezium-cdc-orders
...
All checks passed.
```

Each check reads a trace back from Jaeger's query API (`GET /api/traces/<trace id>`). `demo.sh`
starts every trace itself, with a `traceparent` header, so it knows which trace to read.

## Step 3: The Registry's own spans

Send a request with a trace ID of your own:

```bash
TRACE=$(openssl rand -hex 16)
curl -s -o /dev/null -w '%{http_code}\n' \
  -H "traceparent: 00-$TRACE-$(openssl rand -hex 8)-01" \
  http://localhost:8081/apis/registry/v3/groups/default/artifacts/no-such-artifact
sleep 3
curl -s http://localhost:16686/api/traces/$TRACE | jq -r '.data[0].spans[] | "\(.operationName)  \(.tags | map(select(.key == "http.response.status_code" or .key == "error")) | map("\(.key)=\(.value)") | join(" "))"'
```

Expected output (order may vary):

```
404
GET /apis/registry/v3/groups/{groupId}/artifacts/{artifactId}  http.response.status_code=404
storage.getArtifactMetaData  error=true
```

In Jaeger (<http://localhost:16686/trace/$TRACE>), open the server span: it has
`apicurio.registry.path.group_id` and `apicurio.registry.path.artifact_id`, and its storage child
has the `ArtifactNotFoundException` as an event.

## Step 4: Kafka serdes in one trace

```bash
TRACE=$(openssl rand -hex 16)
curl -s -X POST -H "traceparent: 00-$TRACE-$(openssl rand -hex 8)-01" \
  "http://localhost:8084/greetings?name=Alice" | jq .
sleep 5
curl -s http://localhost:16686/api/traces/$TRACE | jq -r '.data[0] as $d | $d.spans[]
  | select(.operationName | startswith("storage.") | not)
  | "\($d.processes[.processID].serviceName)  \(.operationName)"'
```

Expected output (order may vary):

```
greeting-producer  POST /greetings
greeting-producer  create-greeting
greeting-producer  greetings publish
greeting-producer  serde.serialize
greeting-producer  POST
apicurio-registry  POST /apis/registry/v3/groups/{groupId}/artifacts
greeting-consumer  greetings process
greeting-consumer  process-greeting-message
```

In Jaeger, the trace reads top to bottom: the REST call, the `@WithSpan` method `create-greeting`,
the Kafka `publish` span, the serializer's own `serde.serialize` span, its HTTP call to the
Registry, and the Registry's handling of it, with storage spans. The consumer's `process` span is a
child of the producer's `publish` span: the Kafka instrumentation (`KafkaTelemetry.wrap`) carried
the context in the record headers.

The consumer app returns what it read, with the trace ID it extracted:

```bash
curl -s "http://localhost:8085/consumer/greetings/batch?count=10" | jq '.messages[] | {message, extractedTraceId}'
```

The consumer's `serde.deserialize` span is in a trace of its own (service `greeting-consumer`,
operation `serde.deserialize` in Jaeger): deserialization runs inside `poll()`, before the record's
context is extracted.

## Step 5: Errors on spans

```bash
curl -s -X POST "http://localhost:8084/greetings/invalid?errorType=schema" | jq .
```

The response includes a `traceId`. In Jaeger, its `process-invalid-greeting` span is red: status
`ERROR`, attribute `error.type=schema`, the exception as an event, and an `error-handled` event.
Try `errorType=validation` and `errorType=kafka` too.

## Step 6: Debezium CDC in one trace

```bash
TRACE=$(openssl rand -hex 16)
curl -s -X POST -H "traceparent: 00-$TRACE-$(openssl rand -hex 8)-01" -H 'Content-Type: application/json' \
  -d '{"customerName": "Alice", "product": "Widget", "quantity": 2, "price": 9.99}' \
  http://localhost:8086/orders | jq .
sleep 8
curl -s http://localhost:16686/api/traces/$TRACE | jq -r '.data[0] as $d | $d.spans[]
  | select(.operationName | startswith("storage.") | not)
  | "\($d.processes[.processID].serviceName)  \(.operationName)"'
```

Expected output (order may vary):

```
order-service  POST /orders
order-service  create-order-entity
debezium-server  debezium-cdc-orders
cdc-consumer  process-cdc-event
cdc-consumer  GET /apis/registry/v3/ids/globalIds/{globalId}
apicurio-registry  GET /apis/registry/v3/ids/globalIds/{globalId}
```

How the trace crosses the database:

1. The order service stores its trace context in the row (`tracingspancontext` column,
   `traceparent=00-<trace id>-<span id>-01`).
2. In Debezium Server, the example's SMT `OtelActivateTracingSpan` reads the column from the
   change event, starts `debezium-cdc-orders` as a child of the order service's span, and keeps it
   current while Debezium converts the record. The OpenTelemetry Java agent then puts Debezium's
   HTTP calls to the Registry in this trace.
3. The CDC consumer reads the column too, and continues the trace with `process-cdc-event`; its
   schema lookup in the Registry (a Quarkus REST client) is a child span.

Debezium registers the schema only for the first change it captures, then caches it. Find that
trace in Jaeger: service `debezium-server`, operation `POST`. It contains
`POST /apis/registry/v2/groups/{groupId}/artifacts` (or `.../meta`) in the Registry, under the
order that triggered it. Debezium Server bundles the Apicurio Registry 2.6 serdes, which use the
Registry's v2 API.

Updates work the same way:

```bash
curl -s -X PUT "http://localhost:8086/orders/1/status?status=SHIPPED" | jq .
curl -s "http://localhost:8087/cdc/events/batch?count=10" | jq '.events[] | {operation, extractedTraceId}'
```

Debezium's Kafka sink sends the record later, on another thread, so its
`dbserver1.public.orders publish` span and the consumer's `dbserver1.public.orders process` span
form a separate trace, linked by the Kafka record headers.

## Step 7: The Registry's metrics

The Registry exports metrics through the same Collector, which exposes them in Prometheus format:

```bash
curl -s http://localhost:8889/metrics | grep -E '^otel_apicurio_|^otel_http_server_request_duration_seconds_count.*apicurio-registry' | head
```

`otel_apicurio_artifacts_created_total` counts the artifacts created through the v3 API.

## Step 8: Clean up

```bash
docker compose down -v
```

## Presenter notes

- Start with Step 3: one request, one trace, the Registry's spans explained. Then Step 4 shows why
  it matters: the schema registration is not a mystery call in another trace, it sits under the
  `send` that caused it.
- Open the traces in Jaeger's UI rather than with `jq`; the timeline shows the serializer's call
  to the Registry inside the Kafka send.
- In Step 6, show the `tracingspancontext` column
  (`docker compose exec postgres psql -U orderuser -d orders -c 'select id, tracingspancontext from orders'`):
  this is how the trace crosses the database.
- Sampling: the Registry samples 10% of the traces it starts by default
  (`parentbased_traceidratio`, `0.1`), but always follows a sampled caller. The example uses `1.0`.
- Custom spans: the producer shows both ways, `@WithSpan` with `@SpanAttribute`
  (`createGreeting`) and the `Tracer` API (`sendDetailedGreeting`,
  `POST /greetings/detailed?name=Bob&priority=high`).

## Application endpoints

| App | Method and path | Purpose |
|---|---|---|
| Producer | `POST /greetings?name=X` | Send a greeting |
| Producer | `POST /greetings/batch?baseName=X&count=N` | Send N greetings in one trace |
| Producer | `POST /greetings/detailed?name=X&priority=Y` | Send with a `Tracer`-API span, attributes and events |
| Producer | `POST /greetings/invalid?errorType=validation\|schema\|kafka` | Record an error on a span |
| Producer | `GET /greetings/trace-info` | The current trace context |
| Consumer | `GET /consumer/greetings`, `GET /consumer/greetings/batch?count=N` | Messages read, with their trace IDs |
| Consumer | `POST /consumer/greetings/process` | Process one message, with child spans |
| Consumer | `GET /consumer/stats`, `GET /consumer/trace-info` | Statistics, current trace context |
| Order service | `POST /orders`, `POST /orders/batch?count=N` | Create orders |
| Order service | `PUT /orders/{id}/status?status=X`, `GET /orders`, `GET /orders/{id}` | Update and read orders |
| CDC consumer | `GET /cdc/events`, `GET /cdc/events/batch?count=N` | Change events read, with their trace IDs |
| CDC consumer | `GET /cdc/stats`, `GET /cdc/trace-info` | Statistics, current trace context |

## Troubleshooting

- **`docker compose up` fails: `target/quarkus-app` not found** — run `./build.sh` first.
- **Podman: `buildx_buildkit_default ... already in use`** — build without BuildKit:
  `DOCKER_BUILDKIT=0 docker compose up -d --build`.
- **No traces in Jaeger** — check the Collector: `docker compose logs otel-collector`. The Registry
  only exports when `QUARKUS_OTEL_SDK_DISABLED=false`.
- **No Debezium spans, or no CDC events** — `docker compose logs debezium-server`. A
  `Could not find resource for full path: .../apis/registry/v3/apis/registry/v2/...` error means
  `apicurio.registry.url` includes an API path: Debezium's serdes add `/apis/registry/v2` themselves.
- **Replication slot errors after a restart** — `docker compose down -v` removes the database and
  the slot.
- **A check fails with a missing span** — spans arrive in batches; `demo.sh` waits up to 60 seconds
  per trace. Re-run it; if it fails again, open the trace ID it printed in Jaeger.

## Using other ports

Every host port can be changed through an environment variable, for both Compose and `demo.sh`:

```bash
export REGISTRY_PORT=28081 JAEGER_PORT=26686 COLLECTOR_METRICS_PORT=28889 \
  PRODUCER_PORT=28084 CONSUMER_PORT=28085 ORDER_SERVICE_PORT=28086 CDC_CONSUMER_PORT=28087
docker compose up -d
./demo.sh
```

The commands in this guide use the default ports; replace them accordingly.
