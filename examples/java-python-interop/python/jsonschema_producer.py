#!/usr/bin/env python3
"""
Python JSON Schema Producer for the Java-Python interoperability example.

This producer sends JSON Schema-validated messages that can be consumed by
either a Java or Python consumer using the Apicurio Registry SerDes.

Pre-requisites:
- Kafka must be running on localhost:9092
- Apicurio Registry must be running on localhost:8080
- Install dependencies: pip install apicurio-registry-serdes[kafka,jsonschema]
"""

import time
from datetime import datetime

from kafka import KafkaProducer

from apicurio_registry_serdes import SerdeConfig
from apicurio_registry_serdes.kafka import KafkaJsonSchemaSerializer


# Configuration
REGISTRY_URL = "http://localhost:8080/apis/registry/v3"
BOOTSTRAP_SERVERS = ["localhost:9092"]
TOPIC_NAME = "java-python-interop-json"

# JSON Schema - same structure used by Java consumers
JSON_SCHEMA = {
    "$id": "https://example.com/greeting.schema.json",
    "$schema": "http://json-schema.org/draft-07/schema#",
    "title": "Greeting",
    "type": "object",
    "required": ["message", "time", "sender", "source"],
    "properties": {
        "message": {"type": "string", "description": "The greeting message"},
        "time": {"type": "integer", "description": "Unix timestamp in milliseconds"},
        "sender": {"type": "string", "description": "Name of the sender"},
        "source": {"type": "string", "description": "Source language (java or python)"},
    },
}


def main():
    print("=== Python JSON Schema Producer (for Java Consumer) ===")
    print("Starting Python JSON Schema producer...")
    print(f"Messages will be produced to topic: {TOPIC_NAME}")
    print("These messages can be consumed by Java using Apicurio Registry SerDes")
    print()

    # Configure the SerDes
    config = SerdeConfig(
        registry_url=REGISTRY_URL,
        auto_register=True,
    )

    # Create the serializer
    serializer = KafkaJsonSchemaSerializer(config, schema=JSON_SCHEMA)

    # Create the Kafka producer
    producer = KafkaProducer(
        bootstrap_servers=BOOTSTRAP_SERVERS,
        value_serializer=serializer,
    )

    message_count = 5

    try:
        print(f"Producing {message_count} messages...")
        for idx in range(message_count):
            now = datetime.now()
            greeting = {
                "message": f"Hello from Python ({idx})!",
                "time": int(now.timestamp() * 1000),  # Unix epoch milliseconds
                "sender": "PythonJsonSchemaProducer",
                "source": "python",
            }

            # Send the message
            future = producer.send(TOPIC_NAME, value=greeting)
            record_metadata = future.get(timeout=10)

            print(f"  Sent: {greeting['message']} @ {now}")
            time.sleep(0.5)

        producer.flush()
        print("All messages produced successfully.")
        print()
        print("You can now run the Java consumer to consume these messages:")
        print("  mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaJsonSchemaConsumer")

    finally:
        producer.close()
        serializer.close()


if __name__ == "__main__":
    main()
