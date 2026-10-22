# Camel Quarkus Kafka with Apicurio Registry Example

This example demonstrates how to use Apache Camel Quarkus with the Kafka component and Apicurio Registry
Avro SerDe for schema-managed messaging. Unlike the `kafka-order-processing` example (which uses raw Kafka
clients), this quickstart shows the fully declarative Camel approach — serializer/deserializer configuration
lives entirely in `application.properties`.

## Architecture Overview

```
┌─────────────────────┐
│  Timer (5 seconds)  │
└────────┬────────────┘
         │
         ▼
┌─────────────────────┐       ┌─────────────────┐
│  Camel Producer     │──────>│  Apache Kafka    │
│  Route              │       │  (greetings)     │
└─────────────────────┘       └────────┬─────────┘
         │                             │
         ▼                             ▼
┌─────────────────────┐       ┌─────────────────────┐
│  Apicurio Registry  │<──────│  Camel Consumer     │
│  (Schema Store)     │       │  Route              │
└─────────────────────┘       └─────────────────────┘
```

### Components

1. **Camel Producer Route** — A timer-triggered route that creates `Greeting` objects every 5 seconds and
   sends them to Kafka, serialized as Avro with the schema auto-registered in Apicurio Registry.

2. **Camel Consumer Route** — Reads from the same Kafka topic and logs the deserialized `Greeting` objects.

3. **Apicurio Registry** — Central schema registry storing and versioning the Avro schema.

4. **Apicurio Registry UI** — Web interface for browsing schemas.

5. **Apache Kafka** — Message broker.

## Greeting Schema

A simple Avro record with three fields:

- `name` (string) — Name of the person being greeted
- `message` (string) — The greeting message
- `timestamp` (long) — When the greeting was created (epoch millis)

## Prerequisites

- Java 21 or later
- Maven 3.8+
- Docker and Docker Compose
- At least 4GB of available RAM for Docker containers

## Quick Start

### 1. Start Infrastructure

Start Kafka, Zookeeper, Apicurio Registry, and the Registry UI:

```bash
cd examples/camel-quarkus-kafka
docker-compose up -d
```

Wait for all services to be healthy (~30-60 seconds):

```bash
docker-compose ps
```

Verify Apicurio Registry is running:

```bash
curl http://localhost:8080/apis/registry/v3/groups
```

### 2. Build the Application

```bash
mvn clean package
```

This compiles the Avro schema into a Java class and builds the Quarkus application.

### 3. Run the Application

```bash
mvn quarkus:dev
```

Both the producer and consumer routes run in the same application. You should see output like:

```
Sending greeting for Alice: Hello from Camel!
Received greeting — name: Alice, message: Hello from Camel!, timestamp: 1706450000000
```

## Verifying Schema Registration

### Using the Web UI

Open http://localhost:8888 in your browser. You should see the `Greeting` schema listed as an artifact.

### Using the REST API

```bash
# List all artifacts
curl http://localhost:8080/apis/registry/v3/groups/default/artifacts

# Get schema content
curl http://localhost:8080/apis/registry/v3/groups/default/artifacts/greetings-value/versions/1/content
```

## Configuration

All configuration is in `src/main/resources/application.properties`:

| Property | Description | Default |
|----------|-------------|---------|
| `camel.component.kafka.brokers` | Kafka broker address | `localhost:9092` |
| `camel.component.kafka.value-serializer` | Avro serializer class | `AvroKafkaSerializer` |
| `camel.component.kafka.value-deserializer` | Avro deserializer class | `AvroKafkaDeserializer` |
| `...additional-properties[apicurio.registry.url]` | Registry API URL | `http://localhost:8080/apis/registry/v3` |
| `...additional-properties[apicurio.registry.auto-register]` | Auto-register schemas | `true` |
| `...additional-properties[apicurio.registry.avro-datum-provider]` | Avro datum provider | `ReflectAvroDatumProvider` |

The `additional-properties[...]` syntax passes properties through the Camel Kafka component directly to the
underlying Kafka producer/consumer, where the Apicurio SerDe classes read them.

## Key Features Demonstrated

1. **Declarative SerDe Configuration** — No programmatic Kafka client setup; everything is configured via
   `application.properties` through the Camel Kafka component.

2. **Schema Auto-Registration** — The producer automatically registers the Avro schema on first use.

3. **Avro Code Generation** — The `avro-maven-plugin` generates the `Greeting` class from the `.avsc` schema
   at build time.

4. **Camel Quarkus Integration** — Routes are discovered automatically by Camel Quarkus CDI integration.

5. **Quarkus Apicurio Extension** — Uses the `quarkus-apicurio-registry-avro` extension for proper Quarkus
   integration with the Apicurio Avro SerDe.

## Stopping the Demo

1. Stop the application (Ctrl+C or press `q` in Quarkus dev mode)

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

- Ensure Docker containers are running: `docker-compose ps`
- Check Kafka logs: `docker-compose logs kafka`
- Verify port 9092 is not in use by another process

### Schema Registration Failures

- Verify Apicurio Registry is accessible: `curl http://localhost:8080/apis/registry/v3/groups`
- Check registry logs: `docker-compose logs apicurio-registry`

### Consumer Not Receiving Messages

- Verify the topic exists: `docker exec camel-demo-kafka bin/kafka-topics.sh --list --bootstrap-server localhost:9092`
- The consumer route starts at the same time as the producer; messages appear after the first timer tick (5s)

## Additional Resources

- [Apache Camel Quarkus Kafka](https://camel.apache.org/camel-quarkus/latest/reference/extensions/kafka.html)
- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Apache Avro Documentation](https://avro.apache.org/docs/)
- [Quarkus Documentation](https://quarkus.io/guides/)
