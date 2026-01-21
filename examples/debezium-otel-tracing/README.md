# Debezium CDC with OpenTelemetry Distributed Tracing

This example demonstrates end-to-end distributed tracing with Debezium Change Data Capture (CDC), showing how traces flow from an application writing to a database, through Debezium capturing changes, to schema registration in Apicurio Registry, and finally to a consumer processing CDC events.

## Key Feature: Complete Trace Propagation

This example solves a common challenge with Debezium and OpenTelemetry: ensuring that **all HTTP calls** made during CDC processing (including schema registration to Apicurio Registry) appear in the same distributed trace as the original database write.

This is achieved through a custom OpenTelemetry SMT (`OtelActivateTracingSpan`) that keeps the trace context active during format conversion, allowing the OTEL Java Agent to automatically instrument HTTP calls to the schema registry.

See the related Debezium issue: https://github.com/debezium/dbz/issues/1557

## Architecture

```
                                                        ┌─────────────────┐
                                                        │    Apicurio     │
                                                   ┌───▶│    Registry     │◀───┐
                                                   │    └─────────────────┘    │
                                                   │                           │
┌─────────────────┐     ┌─────────────────┐     ┌──┴──────────────┐            │
│  Order Service  │────▶│   PostgreSQL    │────▶│    Debezium     │            │
│  (Quarkus)      │     │   (CDC source)  │     │    Server       │            │
└────────┬────────┘     └─────────────────┘     └────────┬────────┘            │
         │                                               │                     │
         │              ┌─────────────────┐              │                     │
         │              │     Kafka       │◀─────────────┘                     │
         │              │   (Strimzi)     │                                    │
         │              └────────┬────────┘                                    │
         │                       │                                             │
         │              ┌────────▼────────┐                                    │
         │              │  CDC Consumer   │────────────────────────────────────┘
         │              │   (Quarkus)     │
         │              └────────┬────────┘
         │                       │
         └───────┬───────────────┘
                 │
         ┌───────▼───────┐     ┌─────────────────┐
         │  OpenTelemetry │────▶│     Jaeger      │
         │   Collector    │     │  (Visualization)│
         └───────────────┘     └─────────────────┘
```

## Components

- **Order Service**: Quarkus application that creates/updates orders in PostgreSQL with OpenTelemetry tracing
- **PostgreSQL (orders)**: Source database with CDC enabled (Debezium's postgres image with logical replication)
- **Debezium Server**: Standalone Debezium deployment with OpenTelemetry Java Agent and custom OTEL SMT
- **Kafka**: Message broker for CDC events (Strimzi image)
- **Apicurio Registry**: Schema registry for Avro schemas with OpenTelemetry enabled
- **CDC Consumer**: Quarkus application that consumes CDC events from Kafka
- **OpenTelemetry Collector**: Collects and exports traces to Jaeger
- **Jaeger**: Distributed tracing visualization

## Trace Flow

1. **Order Service** creates an order in PostgreSQL, storing the W3C trace context in a `tracingspancontext` column
2. **Debezium Server** captures the database change via the custom `OtelActivateTracingSpan` SMT which:
   - Extracts the trace context from the record
   - Activates an OpenTelemetry span that remains active during format conversion
3. **Avro format converter** serializes the event and registers the schema with Apicurio Registry (HTTP call is traced)
4. **CDC Consumer** receives the event, deserializes using schema from Registry, and processes it
5. All spans are collected and visible in **Jaeger** under the same trace ID

## Prerequisites

- Docker and Docker Compose
- Java 17 and Maven

## Quick Start

### 1. Build the applications

```bash
cd examples/debezium-otel-tracing

# Build all modules (order-service, cdc-consumer, debezium-converter)
mvn clean package -DskipTests
```

### 2. Start the infrastructure

```bash
docker compose up -d --build
```

Wait for all services to be healthy:

```bash
docker compose ps
```

### 3. Verify setup

```bash
./scripts/setup.sh
```

### 4. Access the services

- **Jaeger UI**: http://localhost:16686
- **Order Service**: http://localhost:8084
- **CDC Consumer**: http://localhost:8085
- **Debezium Server**: http://localhost:8083
- **Apicurio Registry**: http://localhost:8086

### 5. Create an order and observe the trace

```bash
# Create an order
curl -X POST http://localhost:8084/orders \
  -H "Content-Type: application/json" \
  -d '{
    "customerName": "Alice Smith",
    "product": "Widget Pro",
    "quantity": 2,
    "price": 49.99
  }'

# Wait for CDC propagation
sleep 3

# Check CDC events received
curl http://localhost:8085/cdc/events
```

### 6. View traces in Jaeger

Open http://localhost:16686 and:
1. Select service: `order-service`, `debezium-server`, `apicurio-registry`, or `cdc-consumer`
2. Click "Find Traces"
3. Observe the trace spanning from order creation through CDC to consumer, **including schema registration**

### 7. Stop the environment

```bash
docker compose down -v
```

## Testing Scenarios

### Basic Order Flow

```bash
# Create order
curl -X POST http://localhost:8084/orders \
  -H "Content-Type: application/json" \
  -d '{"customerName":"Test","product":"Widget","quantity":1,"price":9.99}'

# Check CDC consumer stats
curl http://localhost:8085/cdc/stats

# Get CDC events
curl http://localhost:8085/cdc/events
```

### Order Status Update (UPDATE CDC event)

```bash
# Update order status (assuming order ID 1)
curl -X PUT "http://localhost:8084/orders/1/status?status=SHIPPED"
```

### Batch Orders

```bash
# Create 5 orders
curl -X POST "http://localhost:8084/orders/batch?count=5"

# Get batch of CDC events
curl "http://localhost:8085/cdc/events/batch?count=5"
```

## Verification

Run the verification script:

```bash
./scripts/verify-tracing.sh
```

This checks:
- Service health (Order Service, CDC Consumer, Debezium Server)
- Order creation with trace ID
- CDC event propagation
- Trace correlation in events
- Jaeger accessibility

## What to Look for in Jaeger

### Services
- `order-service`: HTTP requests and database operations
- `debezium-server`: CDC capture, custom OTEL SMT spans, and schema registration HTTP calls
- `apicurio-registry`: Schema storage operations
- `cdc-consumer`: Kafka consumer and event processing spans

### Key Observation

In the trace, you should see HTTP POST/GET calls to `apicurio-registry` appearing as child spans under the `debezium-server` service. This confirms that schema registration is properly traced as part of the CDC flow.

### Span Attributes
- `order.id`, `order.product`, `order.quantity`
- `cdc.operation` (CREATE, UPDATE, DELETE)
- `kafka.topic`, `kafka.partition`, `kafka.offset`
- `http.method`, `http.url` (for registry calls)

## Custom OTEL SMT

The `debezium-converter` module contains `OtelActivateTracingSpan`, a custom Single Message Transform that:

1. Extracts W3C trace context from the `tracingspancontext` field in CDC records
2. Creates an OpenTelemetry span as a child of the extracted trace
3. **Keeps the span scope active** (unlike Debezium's built-in SMT) so that downstream HTTP calls inherit the context

Configuration in `debezium-server/application.properties`:
```properties
debezium.transforms=oteltracing,unwrap
debezium.transforms.oteltracing.type=io.apicurio.registry.examples.debezium.converter.OtelActivateTracingSpan
debezium.transforms.oteltracing.tracing.operation.name=debezium-cdc-orders
debezium.transforms.oteltracing.tracing.span.context.field=tracingspancontext
```

## API Reference

### Order Service Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/orders` | Create a new order |
| GET | `/orders` | List all orders |
| GET | `/orders/{id}` | Get order by ID |
| PUT | `/orders/{id}/status?status=X` | Update order status |
| POST | `/orders/batch?count=N` | Create batch of orders |
| GET | `/orders/health` | Health check |
| GET | `/orders/trace-info` | Current trace context |

### CDC Consumer Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/cdc/events` | Get next CDC event |
| GET | `/cdc/events/batch?count=N` | Get batch of CDC events |
| GET | `/cdc/stats` | Consumer statistics |
| GET | `/cdc/health` | Health check |
| GET | `/cdc/trace-info` | Current trace context |

## Troubleshooting

### No CDC events appearing

1. Check Debezium Server health:
   ```bash
   curl http://localhost:8083/q/health
   ```

2. Check Debezium Server logs:
   ```bash
   docker compose logs debezium-server
   ```

3. Verify PostgreSQL replication:
   ```bash
   docker compose exec postgres-orders psql -U orderuser -d orders -c "SELECT * FROM pg_replication_slots;"
   ```

### Traces not appearing in Jaeger

1. Check OTel Collector logs:
   ```bash
   docker compose logs otel-collector
   ```

2. Verify services are sending traces:
   ```bash
   docker compose logs order-service | grep -i trace
   ```

### Schema registration not in trace

1. Verify OTEL Java Agent is loaded:
   ```bash
   docker compose logs debezium-server | grep "opentelemetry-javaagent"
   ```

2. Check that the custom SMT is being used:
   ```bash
   docker compose logs debezium-server | grep "OtelActivateTracingSpan"
   ```

## Related Documentation

- [Debezium Distributed Tracing](https://debezium.io/documentation/reference/stable/integrations/tracing.html)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Debezium Issue: OTEL span context not active during format conversion](https://github.com/debezium/dbz/issues/1557)
