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

import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.config.SerdeConfig;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Date;
import java.util.Properties;

/**
 * Java Avro Producer for the Java-Python interoperability example.
 * <p>
 * This producer sends Avro-serialized messages that can be consumed by
 * either a Java or Python consumer using the Apicurio Registry SerDes.
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Kafka must be running on localhost:9092</li>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 */
public class JavaAvroProducer {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    public static final String TOPIC_NAME = "java-python-interop-avro";
    public static final String SUBJECT_NAME = "Greeting";

    // Avro schema for the Greeting message - same structure used by Python consumers
    public static final String SCHEMA = "{"
            + "\"type\":\"record\","
            + "\"name\":\"Greeting\","
            + "\"namespace\":\"io.apicurio.registry.examples.interop\","
            + "\"fields\":["
            + "  {\"name\":\"message\",\"type\":\"string\"},"
            + "  {\"name\":\"time\",\"type\":\"long\"},"
            + "  {\"name\":\"sender\",\"type\":\"string\"},"
            + "  {\"name\":\"source\",\"type\":\"string\"}"
            + "]}";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Java Avro Producer (for Python Consumer) ===");
        System.out.println("Starting Java Avro producer...");
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
        Schema schema = new Schema.Parser().parse(SCHEMA);

        try {
            System.out.println("Producing " + messageCount + " messages...");
            for (int idx = 0; idx < messageCount; idx++) {
                GenericRecord record = new GenericData.Record(schema);
                Date now = new Date();
                String message = "Hello from Java (" + idx + ")!";

                record.put("message", message);
                record.put("time", now.getTime());
                record.put("sender", "JavaAvroProducer");
                record.put("source", "java");

                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(
                        TOPIC_NAME, SUBJECT_NAME, record);
                producer.send(producedRecord);

                System.out.println("  Sent: " + message + " @ " + now);
                Thread.sleep(500);
            }
            System.out.println("All messages produced successfully.");
            System.out.println("\nYou can now run the Python consumer to consume these messages:");
            System.out.println("  cd python && python avro_consumer.py");
        } finally {
            producer.flush();
            producer.close();
        }
    }

    private static Producer<Object, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Kafka settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "JavaAvroProducer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

        // Apicurio Registry settings
        props.put(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

        return new KafkaProducer<>(props);
    }
}
