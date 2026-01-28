# Kafka Order Processing Example

This example demonstrates a complete, realistic Apache Kafka architecture that both produces and consumes
messages, using Apicurio Registry as the schema registry. The example simulates an order processing system
with separate producer and consumer applications built with Quarkus.

## Architecture Overview

```
┌─────────────────┐
│ Order Producer  │──┐
│  (Quarkus App)  │  │
└─────────────────┘  │
                     │
                     ├──> Apache Kafka ──>  ┌─────────────────┐
                     │    (Topic: orders)   │ Order Consumer  │
┌─────────────────┐  │                      │  (Quarkus App)  │
│ Apicurio        │<─┘                      └─────────────────┘
│ Registry        │                                 │
│ (Schema Store)  │<────────────────────────────────┘
└─────────────────┘
```

### Components

1. **Order Producer** - A Quarkus application that automatically generates and sends random order events to
   Kafka every 5 seconds. Orders are serialized using Avro with schemas managed by Apicurio Registry.

2. **Order Consumer** - A Quarkus application that consumes order events from Kafka, deserializes them using
   the schema from Apicurio Registry, and processes them.

3. **Apicurio Registry** - Acts as the central schema registry, storing and versioning Avro schemas. Both
   producer and consumer applications use it to ensure schema compatibility.

4. **Apicurio Registry UI** - Web-based user interface for browsing and managing schemas in the registry.

5. **Apache Kafka** - The message broker that facilitates communication between producer and consumer.

6. **Apache Zookeeper** - Required for Kafka cluster coordination.

## Order Schema

The example uses an Avro schema that defines a realistic order structure:

- **Order** - Main record containing:
    - Order ID, Customer information (ID, name, email)
    - Timestamp, Status (PENDING, PROCESSING, COMPLETED, CANCELLED)
    - List of order items
    - Total amount

- **OrderItem** - Nested record containing:
    - Item ID, name
    - Quantity, unit price

## Prerequisites

- Java 17 or later
- Maven 3.8+
- Docker and Docker Compose
- At least 4GB of available RAM for Docker containers

## Quick Start

### 1. Start Infrastructure

Start Kafka, Zookeeper, Apicurio Registry, and the Registry UI using Docker Compose:

```bash
cd examples/kafka-order-processing
docker-compose up -d
```

Wait for all services to be healthy (approximately 30-60 seconds). You can check the status with:

```bash
docker-compose ps
```

Verify Apicurio Registry is running:

```bash
curl http://localhost:8080/apis/registry/v3/groups
```

Access the Apicurio Registry UI in your browser:

```
http://localhost:8888
```

The UI provides a web interface to browse schemas, view schema versions, manage artifacts, and explore the
registry's contents. Once the producer starts sending orders, you'll be able to see the Order schema appear
in the UI.

### 2. Build the Applications

Build all modules (schema, producer, consumer):

```bash
mvn clean package
```

### 3. Run the Producer

In a terminal window, start the order producer:

```bash
cd order-producer
mvn quarkus:dev
```

You should see log messages indicating that orders are being generated and sent to Kafka every 5 seconds:

```
Successfully sent order #1 - ID: 123e4567-e89b-12d3-a456-426614174000, Customer: John Smith,
Total: $1,234.56, Items: 3
```

### 4. Run the Consumer

In a separate terminal window, start the order consumer:

```bash
cd order-consumer
mvn quarkus:dev
```

You should see detailed log messages showing the orders being consumed and processed:

```
========================================
Received order #1
Order ID: 123e4567-e89b-12d3-a456-426614174000
Customer: John Smith (CUST-012345)
Email: john.smith@example.com
Order Date: Tue Jan 28 10:30:45 EST 2025
Status: PENDING
Items:
  - 2 x Professional Laptop @ $899.99 each = $1799.98
  - 1 x Wireless Mouse @ $29.99 each = $29.99
Total Amount: $1829.97
========================================
```

## Verifying Schema Registration

You can verify that the Avro schema has been registered in Apicurio Registry using either the UI or the API:

### Using the Web UI

Open http://localhost:8888 in your browser. You should see the Order schema listed in the artifacts. Click
on it to view:
- Schema definition (Avro JSON)
- Schema versions
- Schema metadata
- Content rules and validation settings

### Using the REST API

```bash
# List all artifacts
curl http://localhost:8080/apis/registry/v3/groups/default/artifacts

# Get the Order schema details
curl http://localhost:8080/apis/registry/v3/groups/default/artifacts/Order
```

## Configuration

### Producer Configuration

Configuration is in `order-producer/src/main/resources/application.properties`:

- `kafka.bootstrap.servers` - Kafka broker address (default: localhost:9092)
- `kafka.topic.name` - Topic name for orders (default: orders)
- `apicurio.registry.url` - Registry URL (default: http://localhost:8080/apis/registry/v3)

### Consumer Configuration

Configuration is in `order-consumer/src/main/resources/application.properties`:

- `kafka.bootstrap.servers` - Kafka broker address (default: localhost:9092)
- `kafka.topic.name` - Topic name for orders (default: orders)
- `kafka.consumer.group.id` - Consumer group ID (default: order-consumer-group)
- `apicurio.registry.url` - Registry URL (default: http://localhost:8080/apis/registry/v3)

## Key Features Demonstrated

1. **Schema Registry Integration** - Both producer and consumer use Apicurio Registry for schema management,
   ensuring compatibility and evolution.

2. **Auto-registration** - The producer automatically registers the Avro schema with the registry on first
   use.

3. **Schema Evolution** - The architecture supports schema evolution. You can modify the schema and the
   registry will manage versioning.

4. **Avro Serialization** - Messages are efficiently serialized using Avro binary format, reducing payload
   size.

5. **Quarkus Framework** - Both applications use Quarkus for fast startup, low memory footprint, and
   developer-friendly features like live reload.

6. **Realistic Domain Model** - The order processing domain model represents a real-world use case with
   complex nested structures.

## Stopping the Demo

1. Stop the producer and consumer applications (Ctrl+C in each terminal)

2. Stop the infrastructure:

```bash
docker-compose down
```

To remove all data and start fresh:

```bash
docker-compose down -v
```

## Troubleshooting

### Kafka Connection Issues

If the applications can't connect to Kafka:
- Ensure Docker containers are running: `docker-compose ps`
- Check Kafka logs: `docker-compose logs kafka`
- Verify port 9092 is not in use by another process

### Schema Registration Failures

If schema registration fails:
- Verify Apicurio Registry is accessible: `curl http://localhost:8080/apis/registry/v3/groups`
- Check registry logs: `docker-compose logs apicurio-registry`
- Ensure Kafka is running (registry uses Kafka for storage)

### Consumer Not Receiving Messages

If the consumer doesn't receive messages:
- Verify the topic exists: `docker exec order-demo-kafka bin/kafka-topics.sh --list --bootstrap-server
  localhost:9092`
- Check that producer and consumer are using the same topic name
- Verify consumer group settings

## Next Steps

To extend this example:

1. **Add REST API** - Expose a REST endpoint in the producer to create orders on demand
2. **Error Handling** - Add dead letter queue for failed message processing
3. **Monitoring** - Integrate Prometheus metrics and Grafana dashboards
4. **Schema Evolution** - Add new fields to the schema and observe backward compatibility
5. **Multiple Consumers** - Run multiple consumer instances to demonstrate load balancing

## Additional Resources

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Apache Kafka Documentation](https://kafka.apache.org/documentation/)
- [Quarkus Kafka Guide](https://quarkus.io/guides/kafka)
- [Apache Avro Documentation](https://avro.apache.org/docs/)
