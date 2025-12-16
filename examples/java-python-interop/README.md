# Java-Python Interoperability Example

This example demonstrates **interoperability between Java and Python applications** using the Apicurio Registry SerDes (Serializers/Deserializers). Messages produced by Java can be consumed by Python and vice versa, using the same Apicurio Registry instance for schema management.

## Overview

The example includes producers and consumers for three schema types:

| Schema Type | Java Producer | Python Consumer | Python Producer | Java Consumer |
|-------------|---------------|-----------------|-----------------|---------------|
| **Avro** | `JavaAvroProducer` | `avro_consumer.py` | `avro_producer.py` | `JavaAvroConsumer` |
| **JSON Schema** | `JavaJsonSchemaProducer` | `jsonschema_consumer.py` | `jsonschema_producer.py` | `JavaJsonSchemaConsumer` |
| **Protobuf** | `JavaProtobufProducer` | `protobuf_consumer.py` | `protobuf_producer.py` | `JavaProtobufConsumer` |

### Wire Format Compatibility

Both Java and Python SerDes use the same wire format:
```
[MAGIC_BYTE (1 byte: 0x00)]
[SCHEMA_ID (4 bytes: big-endian unsigned int)]
[MESSAGE DATA (N bytes)]
```

This ensures that messages serialized by one language can be deserialized by the other.

## Prerequisites

- **Apache Kafka** running on `localhost:9092`
- **Apicurio Registry** running on `localhost:8080`
- **Java 17** or later
- **Python 3.9** or later
- **Maven 3.6** or later

### Starting the Infrastructure

You can use Docker Compose to start Kafka and Apicurio Registry:

```bash
# Start Kafka
docker run -d --name kafka -p 9092:9092 \
  -e KAFKA_BROKER_ID=1 \
  -e KAFKA_LISTENERS=PLAINTEXT://:9092 \
  -e KAFKA_ADVERTISED_LISTENERS=PLAINTEXT://localhost:9092 \
  -e KAFKA_ZOOKEEPER_CONNECT=zookeeper:2181 \
  confluentinc/cp-kafka:latest

# Start Apicurio Registry
docker run -d --name apicurio-registry -p 8080:8080 \
  apicurio/apicurio-registry:latest-snapshot
```

Or use the docker-compose file in the `examples/tools/kafka-all` directory.

## Building the Java Examples

From the `examples/java-python-interop` directory:

```bash
mvn clean package
```

## Setting Up Python

From the `examples/java-python-interop/python` directory:

```bash
# Create a virtual environment (recommended)
python -m venv venv
source venv/bin/activate  # On Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# For Protobuf examples, compile the proto file
protoc --python_out=. greeting.proto
```

## Running the Examples

### Scenario 1: Java Produces, Python Consumes

#### Avro
```bash
# Terminal 1: Start Java producer
mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaAvroProducer

# Terminal 2: Start Python consumer
cd python
python avro_consumer.py
```

#### JSON Schema
```bash
# Terminal 1: Start Java producer
mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaJsonSchemaProducer

# Terminal 2: Start Python consumer
cd python
python jsonschema_consumer.py
```

#### Protobuf
```bash
# Terminal 1: Start Java producer
mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaProtobufProducer

# Terminal 2: Start Python consumer
cd python
python protobuf_consumer.py
```

### Scenario 2: Python Produces, Java Consumes

#### Avro
```bash
# Terminal 1: Start Python producer
cd python
python avro_producer.py

# Terminal 2: Start Java consumer
mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaAvroConsumer
```

#### JSON Schema
```bash
# Terminal 1: Start Python producer
cd python
python jsonschema_producer.py

# Terminal 2: Start Java consumer
mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaJsonSchemaConsumer
```

#### Protobuf
```bash
# Terminal 1: Start Python producer
cd python
python protobuf_producer.py

# Terminal 2: Start Java consumer
mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaProtobufConsumer
```

## Message Schema

All examples use a `Greeting` message with the following structure:

| Field | Type | Description |
|-------|------|-------------|
| `message` | string | The greeting message content |
| `time` | long/int64 | Unix timestamp in milliseconds |
| `sender` | string | Name of the producer class |
| `source` | string | Source language (`java` or `python`) |

### Avro Schema
```json
{
  "type": "record",
  "name": "Greeting",
  "namespace": "io.apicurio.registry.examples.interop",
  "fields": [
    {"name": "message", "type": "string"},
    {"name": "time", "type": "long"},
    {"name": "sender", "type": "string"},
    {"name": "source", "type": "string"}
  ]
}
```

### JSON Schema
```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Greeting",
  "type": "object",
  "required": ["message", "time", "sender", "source"],
  "properties": {
    "message": {"type": "string"},
    "time": {"type": "integer"},
    "sender": {"type": "string"},
    "source": {"type": "string"}
  }
}
```

### Protobuf Schema
```protobuf
syntax = "proto3";
message Greeting {
  string message = 1;
  int64 time = 2;
  string sender = 3;
  string source = 4;
}
```

## Topics Used

| Schema Type | Kafka Topic |
|-------------|-------------|
| Avro | `java-python-interop-avro` |
| JSON Schema | `java-python-interop-json` |
| Protobuf | `java-python-interop-protobuf` |

## Troubleshooting

### Common Issues

1. **Connection refused to Kafka**: Ensure Kafka is running on `localhost:9092`
2. **Connection refused to Registry**: Ensure Apicurio Registry is running on `localhost:8080`
3. **Schema not found**: The producer must run first to register the schema (with `auto_register=True`)
4. **Protobuf import error**: Make sure to compile `greeting.proto` with `protoc --python_out=. greeting.proto`

### Verifying the Registry

You can verify schemas are registered by visiting:
- http://localhost:8080/ui (Apicurio Registry UI)
- http://localhost:8080/apis/registry/v3/groups/default/artifacts

## Key Concepts Demonstrated

1. **Schema Registry Integration**: Both Java and Python use Apicurio Registry to store and retrieve schemas
2. **Auto-registration**: Producers automatically register schemas if they don't exist
3. **Wire Format Compatibility**: The same wire format ensures cross-language interoperability
4. **Schema Evolution**: The registry maintains schema versions for compatibility
5. **Multiple Schema Types**: Support for Avro, JSON Schema, and Protobuf

## Related Documentation

- [Apicurio Registry Documentation](https://www.apicur.io/registry/docs/)
- [Python SerDes Documentation](../../docs/modules/ROOT/pages/getting-started/assembly-using-python-client-serdes.adoc)
- [Java SerDes Documentation](../../docs/modules/ROOT/pages/getting-started/assembly-using-kafka-client-serdes.adoc)
