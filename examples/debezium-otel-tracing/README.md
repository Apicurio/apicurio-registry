# Debezium CDC with OpenTelemetry Distributed Tracing

This example demonstrates end-to-end distributed tracing with Debezium Change Data Capture (CDC), showing how traces flow from an application writing to a database, through Debezium capturing changes, and finally to a consumer processing CDC events.

## Architecture

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│  Order Service  │────▶│   PostgreSQL    │────▶│    Debezium     │
│  (Quarkus)      │     │   (CDC source)  │     │    Connect      │
└────────┬────────┘     └─────────────────┘     └────────┬────────┘
         │                                               │
         │                                               │
         │              ┌─────────────────┐              │
         │              │     Kafka       │◀─────────────┘
         │              │   (Strimzi)     │
         │              └────────┬────────┘
         │                       │
         │              ┌────────▼────────┐     ┌─────────────────┐
         │              │  CDC Consumer   │────▶│    Apicurio     │
         │              │   (Quarkus)     │     │    Registry     │
         │              └────────┬────────┘     └─────────────────┘
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
- **Debezium Connect**: Kafka Connect with Debezium PostgreSQL connector and OpenTelemetry enabled
- **Kafka**: Message broker for CDC events (Strimzi image)
- **Apicurio Registry**: Schema registry for Avro schemas
- **CDC Consumer**: Quarkus application that consumes CDC events from Kafka
- **OpenTelemetry Collector**: Collects and exports traces to Jaeger
- **Jaeger**: Distributed tracing visualization

## Trace Flow

1. **Order Service** creates an order in PostgreSQL, storing the trace ID in the order record
2. **Debezium** captures the database change and publishes a CDC event to Kafka with trace context
3. **CDC Consumer** receives the event, extracting and correlating the trace context
4. All spans are collected and visible in **Jaeger**

## Prerequisites

- Docker and Docker Compose
- Java 17 and Maven

## Quick Start

### 1. Build the applications

```bash
cd examples/debezium-otel-tracing

# Build order-service
cd order-service
mvn clean package -DskipTests
cd ..

# Build cdc-consumer
cd cdc-consumer
mvn clean package -DskipTests
cd ..
```

### 2. Start the infrastructure

```bash
docker compose up -d
```

Wait for all services to be healthy:

```bash
docker compose ps
```

### 3. Register the Debezium connector

```bash
./scripts/setup.sh
```

Or manually:

```bash
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @register-connector.json
```

### 4. Access the services

- **Jaeger UI**: http://localhost:16686
- **Order Service**: http://localhost:8084
- **CDC Consumer**: http://localhost:8085
- **Kafka Connect**: http://localhost:8083
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
1. Select service: `order-service`, `debezium-connect`, or `cdc-consumer`
2. Click "Find Traces"
3. Observe the trace spanning from order creation through CDC to consumer

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
- Service health (Order Service, CDC Consumer, Kafka Connect)
- Order creation with trace ID
- CDC event propagation
- Trace correlation in events
- Jaeger accessibility

## What to Look for in Jaeger

### Services
- `order-service`: HTTP requests and database operations
- `debezium-connect`: CDC capture and Kafka producer spans
- `cdc-consumer`: Kafka consumer and event processing spans

### Span Attributes
- `order.id`, `order.product`, `order.quantity`
- `cdc.operation` (CREATE, UPDATE, DELETE)
- `kafka.topic`, `kafka.partition`, `kafka.offset`

### Trace Correlation
The trace ID stored in the order's `trace_id` column should correlate with:
- The original HTTP request span
- The Debezium CDC event span
- The consumer processing span

## Debezium OpenTelemetry Configuration

Debezium Connect is configured with OpenTelemetry via environment variables:

```yaml
ENABLE_OTEL: "true"
OTEL_SERVICE_NAME: "debezium-connect"
OTEL_TRACES_EXPORTER: "otlp"
OTEL_EXPORTER_OTLP_ENDPOINT: "http://otel-collector:4317"
OTEL_PROPAGATORS: "tracecontext,baggage"
```

The `ActivateTracingSpan` SMT (Single Message Transform) is configured in the connector to create trace spans for each CDC event.

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

1. Check connector status:
   ```bash
   curl http://localhost:8083/connectors/orders-connector/status
   ```

2. Check Kafka Connect logs:
   ```bash
   docker compose logs debezium-connect
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

### Connector failing to start

1. Check for existing replication slot:
   ```bash
   docker compose exec postgres-orders psql -U orderuser -d orders -c "SELECT pg_drop_replication_slot('orders_slot');"
   ```

2. Restart the connector:
   ```bash
   curl -X POST http://localhost:8083/connectors/orders-connector/restart
   ```

## Related Documentation

- [Debezium Distributed Tracing](https://debezium.io/documentation/reference/stable/integrations/tracing.html)
- [OpenTelemetry Documentation](https://opentelemetry.io/docs/)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Strimzi Documentation](https://strimzi.io/documentation/)

## Sources

- [Debezium Distributed Tracing Documentation](https://debezium.io/documentation/reference/stable/integrations/tracing.html)
- [Debezium Blog: Distributed Tracing](https://debezium.io/blog/2020/12/16/distributed-tracing-with-debezium/)
- [Debezium 3.1.0.Beta1 Release Notes](https://debezium.io/blog/2025/03/13/debezium-3-1-beta1-released/)
