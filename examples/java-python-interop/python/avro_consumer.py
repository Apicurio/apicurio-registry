#!/usr/bin/env python3
"""
Python Avro Consumer for the Java-Python interoperability example.

This consumer reads Avro-serialized messages that were produced by
either a Java or Python producer using the Apicurio Registry SerDes.

Pre-requisites:
- Kafka must be running on localhost:9092
- Apicurio Registry must be running on localhost:8080
- Messages must have been produced using Java or Python producer
- Install dependencies: pip install apicurio-registry-serdes[kafka]
"""

from datetime import datetime

from kafka import KafkaConsumer

from apicurio_registry_serdes import SerdeConfig
from apicurio_registry_serdes.kafka import KafkaAvroDeserializer


# Configuration
REGISTRY_URL = "http://localhost:8080/apis/registry/v3"
BOOTSTRAP_SERVERS = ["localhost:9092"]
TOPIC_NAME = "java-python-interop-avro"


def main():
    print("=== Python Avro Consumer (for Java Producer messages) ===")
    print("Starting Python Avro consumer...")
    print(f"Listening on topic: {TOPIC_NAME}")
    print("This consumer can read messages produced by Java using Apicurio Registry SerDes")
    print()

    # Configure the SerDes
    config = SerdeConfig(
        registry_url=REGISTRY_URL,
    )

    # Create the deserializer
    deserializer = KafkaAvroDeserializer(config)

    # Create the Kafka consumer
    consumer = KafkaConsumer(
        TOPIC_NAME,
        bootstrap_servers=BOOTSTRAP_SERVERS,
        group_id="PythonAvroConsumer",
        auto_offset_reset="earliest",
        value_deserializer=deserializer,
    )

    expected_messages = 5
    message_count = 0

    try:
        print(f"Waiting for messages (expecting {expected_messages})...")
        print()

        for message in consumer:
            value = message.value
            message_count += 1

            # Extract fields from the deserialized dict
            msg_text = value.get("message", "")
            msg_time = value.get("time", 0)
            sender = value.get("sender", "")
            source = value.get("source", "")

            # Convert timestamp to datetime
            dt = datetime.fromtimestamp(msg_time / 1000)

            print(f"Received message #{message_count}:")
            print(f"  Message: {msg_text}")
            print(f"  Time: {dt}")
            print(f"  Sender: {sender}")
            print(f"  Source: {source}")
            print()

            if message_count >= expected_messages:
                break

        print(f"Successfully consumed {message_count} messages.")

    except KeyboardInterrupt:
        print("\nInterrupted by user.")
    finally:
        consumer.close()
        deserializer.close()


if __name__ == "__main__":
    main()
