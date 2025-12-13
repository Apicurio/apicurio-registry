#!/usr/bin/env python3
"""
Python Protobuf Consumer for the Java-Python interoperability example.

This consumer reads Protobuf-serialized messages that were produced by
either a Java or Python producer using the Apicurio Registry SerDes.

Pre-requisites:
- Kafka must be running on localhost:9092
- Apicurio Registry must be running on localhost:8080
- Messages must have been produced using Java or Python producer
- Install dependencies: pip install apicurio-registry-serdes[kafka,protobuf]
- Compile the proto file: protoc --python_out=. greeting.proto
"""

from datetime import datetime

from kafka import KafkaConsumer

from apicurio_registry_serdes import SerdeConfig
from apicurio_registry_serdes.kafka import KafkaProtobufDeserializer

# Import the generated protobuf message class
from greeting_pb2 import Greeting


# Configuration
REGISTRY_URL = "http://localhost:8080/apis/registry/v3"
BOOTSTRAP_SERVERS = ["localhost:9092"]
TOPIC_NAME = "java-python-interop-protobuf"


def main():
    print("=== Python Protobuf Consumer (for Java Producer messages) ===")
    print("Starting Python Protobuf consumer...")
    print(f"Listening on topic: {TOPIC_NAME}")
    print("This consumer can read messages produced by Java using Apicurio Registry SerDes")
    print()

    # Configure the SerDes
    config = SerdeConfig(
        registry_url=REGISTRY_URL,
    )

    # Create the deserializer with the message type
    deserializer = KafkaProtobufDeserializer(config, message_type=Greeting)

    # Create the Kafka consumer
    consumer = KafkaConsumer(
        TOPIC_NAME,
        bootstrap_servers=BOOTSTRAP_SERVERS,
        group_id="PythonProtobufConsumer",
        auto_offset_reset="earliest",
        value_deserializer=deserializer,
    )

    expected_messages = 5
    message_count = 0

    try:
        print(f"Waiting for messages (expecting {expected_messages})...")
        print()

        for message in consumer:
            value = message.value  # This is a Greeting protobuf message
            message_count += 1

            # Convert timestamp to datetime
            dt = datetime.fromtimestamp(value.time / 1000)

            print(f"Received message #{message_count}:")
            print(f"  Message: {value.message}")
            print(f"  Time: {dt}")
            print(f"  Sender: {value.sender}")
            print(f"  Source: {value.source}")
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
