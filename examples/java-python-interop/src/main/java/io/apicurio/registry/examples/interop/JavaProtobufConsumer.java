/*
 * Copyright 2024 Red Hat Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.examples.interop;

import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

/**
 * Java Protobuf Consumer for the Java-Python interoperability example.
 * <p>
 * This consumer reads Protobuf-serialized messages that were produced by
 * either a Java or Python producer using the Apicurio Registry SerDes.
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Kafka must be running on localhost:9092</li>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * <li>Messages must have been produced using Python or Java producer</li>
 * </ul>
 */
public class JavaProtobufConsumer {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    public static final String TOPIC_NAME = "java-python-interop-protobuf";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Java Protobuf Consumer (for Python Producer messages) ===");
        System.out.println("Starting Java Protobuf consumer...");
        System.out.println("Listening on topic: " + TOPIC_NAME);
        System.out.println("This consumer can read messages produced by Python using apicurio-registry-serdes");

        int expectedMessages = 5;
        if (args.length > 0) {
            try {
                expectedMessages = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        KafkaConsumer<String, DynamicMessage> consumer = createKafkaConsumer();
        consumer.subscribe(Collections.singletonList(TOPIC_NAME));

        try {
            int messageCount = 0;
            System.out.println("Waiting for messages (expecting " + expectedMessages + ")...\n");

            while (messageCount < expectedMessages) {
                ConsumerRecords<String, DynamicMessage> records = consumer.poll(Duration.ofSeconds(1));

                if (records.count() == 0) {
                    System.out.println("  No messages waiting...");
                } else {
                    for (var record : records) {
                        DynamicMessage value = record.value();
                        messageCount++;

                        // Extract fields from DynamicMessage
                        String message = (String) value.getField(
                                value.getDescriptorForType().findFieldByName("message"));
                        long time = (Long) value.getField(
                                value.getDescriptorForType().findFieldByName("time"));
                        String sender = (String) value.getField(
                                value.getDescriptorForType().findFieldByName("sender"));
                        String source = (String) value.getField(
                                value.getDescriptorForType().findFieldByName("source"));

                        System.out.println("Received message #" + messageCount + ":");
                        System.out.println("  Message: " + message);
                        System.out.println("  Time: " + new Date(time));
                        System.out.println("  Sender: " + sender);
                        System.out.println("  Source: " + source);
                        System.out.println();
                    }
                }
            }

            System.out.println("Successfully consumed " + messageCount + " messages.");
        } finally {
            consumer.close();
        }
    }

    private static KafkaConsumer<String, DynamicMessage> createKafkaConsumer() {
        Properties props = new Properties();

        // Kafka settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "JavaProtobufConsumer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtobufKafkaDeserializer.class.getName());

        // Apicurio Registry settings
        props.put(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // Use DynamicMessage for dynamic deserialization (compatible with Python-produced messages)
        props.put(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, DynamicMessage.class.getName());

        return new KafkaConsumer<>(props);
    }
}
