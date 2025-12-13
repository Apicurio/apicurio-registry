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

import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
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
 * Java JSON Schema Consumer for the Java-Python interoperability example.
 * <p>
 * This consumer reads JSON Schema-validated messages that were produced by
 * either a Java or Python producer using the Apicurio Registry SerDes.
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Kafka must be running on localhost:9092</li>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * <li>Messages must have been produced using Python or Java producer</li>
 * </ul>
 */
public class JavaJsonSchemaConsumer {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    public static final String TOPIC_NAME = "java-python-interop-json";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Java JSON Schema Consumer (for Python Producer messages) ===");
        System.out.println("Starting Java JSON Schema consumer...");
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

        KafkaConsumer<String, GreetingBean> consumer = createKafkaConsumer();
        consumer.subscribe(Collections.singletonList(TOPIC_NAME));

        try {
            int messageCount = 0;
            System.out.println("Waiting for messages (expecting " + expectedMessages + ")...\n");

            while (messageCount < expectedMessages) {
                ConsumerRecords<String, GreetingBean> records = consumer.poll(Duration.ofSeconds(1));

                if (records.count() == 0) {
                    System.out.println("  No messages waiting...");
                } else {
                    for (var record : records) {
                        GreetingBean value = record.value();
                        messageCount++;

                        System.out.println("Received message #" + messageCount + ":");
                        System.out.println("  Message: " + value.getMessage());
                        System.out.println("  Time: " + new Date(value.getTime()));
                        System.out.println("  Sender: " + value.getSender());
                        System.out.println("  Source: " + value.getSource());
                        System.out.println();
                    }
                }
            }

            System.out.println("Successfully consumed " + messageCount + " messages.");
        } finally {
            consumer.close();
        }
    }

    private static KafkaConsumer<String, GreetingBean> createKafkaConsumer() {
        Properties props = new Properties();

        // Kafka settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "JavaJsonSchemaConsumer");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.put(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonSchemaKafkaDeserializer.class.getName());

        // Apicurio Registry settings
        props.put(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.put(SerdeConfig.VALIDATION_ENABLED, Boolean.TRUE);
        props.put(KafkaSerdeConfig.ENABLE_HEADERS, true);

        return new KafkaConsumer<>(props);
    }
}
