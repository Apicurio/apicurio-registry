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
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Date;
import java.util.Properties;

/**
 * Java Protobuf Producer for the Java-Python interoperability example.
 * <p>
 * This producer sends Protobuf-serialized messages that can be consumed by
 * either a Java or Python consumer using the Apicurio Registry SerDes.
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Kafka must be running on localhost:9092</li>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 */
public class JavaProtobufProducer {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    public static final String TOPIC_NAME = "java-python-interop-protobuf";
    public static final String KEY = "Greeting";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Java Protobuf Producer (for Python Consumer) ===");
        System.out.println("Starting Java Protobuf producer...");
        System.out.println("Messages will be produced to topic: " + TOPIC_NAME);
        System.out.println("These messages can be consumed by Python using apicurio-registry-serdes");

        int messageCount = 5;
        if (args.length > 0) {
            try {
                messageCount = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                // Use default
            }
        }

        Producer<Object, Object> producer = createKafkaProducer();

        try {
            System.out.println("Producing " + messageCount + " messages...");
            for (int idx = 0; idx < messageCount; idx++) {
                Date now = new Date();

                GreetingProtos.Greeting greeting = GreetingProtos.Greeting.newBuilder()
                        .setMessage("Hello from Java (" + idx + ")!")
                        .setTime(now.getTime())
                        .setSender("JavaProtobufProducer")
                        .setSource("java")
                        .build();

                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(
                        TOPIC_NAME, KEY, greeting);
                producer.send(producedRecord);

                System.out.println("  Sent: " + greeting.getMessage() + " @ " + now);
                Thread.sleep(500);
            }
            System.out.println("All messages produced successfully.");
            System.out.println("\nYou can now run the Python consumer to consume these messages:");
            System.out.println("  cd python && python protobuf_consumer.py");
        } finally {
            producer.flush();
            producer.close();
        }
    }

    private static Producer<Object, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Kafka settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "JavaProtobufProducer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufKafkaSerializer.class.getName());

        // Apicurio Registry settings
        props.put(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

        return new KafkaProducer<>(props);
    }
}
