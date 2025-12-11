#!/usr/bin/env python3
"""
Python Avro Producer for the Java-Python interoperability example.

This producer sends Avro-serialized messages that can be consumed by
either a Java or Python consumer using the Apicurio Registry SerDes.

Pre-requisites:
- Kafka must be running on localhost:9092
- Apicurio Registry must be running on localhost:8080
- Install dependencies: pip install apicurio-registry-serdes[kafka]
"""

import time
from datetime import datetime

from kafka import KafkaProducer

from apicurio_registry_serdes import SerdeConfig
from apicurio_registry_serdes.kafka import KafkaAvroSerializer


# Configuration
REGISTRY_URL = "http://localhost:8080/apis/registry/v3"
BOOTSTRAP_SERVERS = ["localhost:9092"]
TOPIC_NAME = "java-python-interop-avro"

# Avro schema - same structure used by Java consumers
AVRO_SCHEMA = {
    "type": "record",
    "name": "Greeting",
    "namespace": "io.apicurio.registry.examples.interop",
    "fields": [
        {"name": "message", "type": "string"},
        {"name": "time", "type": "long"},
        {"name": "sender", "type": "string"},
        {"name": "source", "type": "string"},
    ],
}


def main():
    print("=== Python Avro Producer (for Java Consumer) ===")
    print("Starting Python Avro producer...")
    print(f"Messages will be produced to topic: {TOPIC_NAME}")
    print("These messages can be consumed by Java using Apicurio Registry SerDes")
    print()

    # Configure the SerDes
    config = SerdeConfig(
        registry_url=REGISTRY_URL,
        auto_register=True,
    )

    # Create the serializer
    serializer = KafkaAvroSerializer(config, schema=AVRO_SCHEMA)

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
                "sender": "PythonAvroProducer",
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
        print("  mvn exec:java -Dexec.mainClass=io.apicurio.registry.examples.interop.JavaAvroConsumer")

    finally:
        producer.close()
        serializer.close()


if __name__ == "__main__":
    main()
