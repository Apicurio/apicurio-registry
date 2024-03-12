/*
 * Copyright 2020 JBoss Inc
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

package io.apicurio.registry.examples.mix.avro;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;
import java.util.UUID;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;

import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;

/**
 * This example application showcases a scenario where Apache Avro messages are published to the same
 * Kafka topic using different Avro schemas. This example uses the Apicurio Registry serdes classes to serialize
 * and deserialize Apache Avro messages using different schemas, even if received in the same Kafka topic.
 * The following aspects are demonstrated:
 *
 * <ol>
 *   <li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
 *   <li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
 *   <li>Auto-register the Avro schema in the registry (registered by the producer)</li>
 *   <li>Data sent as a simple GenericRecord, no java beans needed</li>
 *   <li>Producing and consuming Avro messages using different schemas mapped to different Apicurio Registry Artifacts</li>
 * </ol>
 * <p>
 * Pre-requisites:
 *
 * <ul>
 *   <li>Kafka must be running on localhost:9092</li>
 *   <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author Fabian Martinez
 * @author Carles Arnal
 */
public class MixAvroExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = MixAvroExample.class.getSimpleName();
    private static final String SCHEMAV1 = "{\"type\":\"record\",\"name\":\"Greeting\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";
    private static final String SCHEMAV2 = "{\"type\":\"record\",\"name\":\"Greeting\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"},{\"name\":\"Extra\",\"type\":\"string\"}]}";
    private static final String FAREWELLSCHEMAV1 = "{\"type\":\"record\",\"name\":\"Farewell\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"}]}";
    private static final String FAREWELLSCHEMAV2 = "{\"type\":\"record\",\"name\":\"Farewell\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Time\",\"type\":\"long\"},{\"name\":\"Extra\",\"type\":\"string\"}]}";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting example " + MixAvroExample.class.getSimpleName());
        String topicName = TOPIC_NAME;

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();

        int producedMessages = 0;
        try {
            producedMessages += produceMessages(producer, topicName, SCHEMAV1, null);

            producedMessages += produceMessages(producer, topicName, SCHEMAV2, "extra greeting");

            producedMessages += produceMessages(producer, topicName, FAREWELLSCHEMAV1, null);

            producedMessages += produceMessages(producer, topicName, FAREWELLSCHEMAV2, "extra farewell");


        } finally {
            System.out.println("Closing the producer.");
            producer.flush();
            producer.close();
        }

        // Create the consumer
        System.out.println("Creating the consumer.");
        KafkaConsumer<Long, GenericRecord> consumer = createKafkaConsumer();

        // Subscribe to the topic

        // Consume the 5 messages.
        try (consumer) {
            System.out.println("Subscribing to topic " + topicName);
            consumer.subscribe(Collections.singletonList(topicName));
            int messageCount = 0;
            System.out.println("Consuming (" + producedMessages + ") messages.");
            while (messageCount < producedMessages) {
                final ConsumerRecords<Long, GenericRecord> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else records.forEach(record -> {
                    GenericRecord value = record.value();
                    value.getSchema().getFullName();
                    if (value.hasField("Extra")) {
                        System.out.println("Consumed " + value.getSchema().getFullName() + ": " + value.get("Message") + " @ " + new Date((long) value.get("Time")) + " @ " + value.get("Extra"));
                    } else {
                        System.out.println("Consumed " + value.getSchema().getFullName() + ": " + value.get("Message") + " @ " + new Date((long) value.get("Time")));
                    }
                });
            }
        }

        System.out.println("Done (success).");
    }

    private static int produceMessages(Producer<Object, Object> producer, String topicName, String schemaContent, String extra) throws InterruptedException {
        int producedMessages = 0;
        Schema schema = new Schema.Parser().parse(schemaContent);
        System.out.println("Producing (5) messages.");
        for (int idx = 0; idx < 5; idx++) {
            // Use the schema to create a record
            GenericRecord record = new GenericData.Record(schema);
            Date now = new Date();
            String message = "Hello (" + producedMessages++ + ")!";
            record.put("Message", message);
            record.put("Time", now.getTime());
            if (extra != null) {
                record.put("Extra", extra);
            }

            // Send/produce the message on the Kafka Producer
            ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, UUID.randomUUID().toString(), record);
            producer.send(producedRecord);

            Thread.sleep(100);
        }
        System.out.println("Messages successfully produced.");
        return producedMessages;
    }

    /**
     * Creates the Kafka producer.
     */
    private static Producer<Object, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + TOPIC_NAME);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        // Use the Apicurio Registry provided Kafka Serializer for Avro
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, AvroKafkaSerializer.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.putIfAbsent(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, MixAvroExample.class.getSimpleName());
        // Map the topic name to the artifactId in the registry
        props.putIfAbsent(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, RecordIdStrategy.class.getName());
        // Get an existing schema or auto-register if not found
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.TRUE);

        //Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka producer
        Producer<Object, Object> producer = new KafkaProducer<>(props);
        return producer;
    }

    /**
     * Creates the Kafka consumer.
     */
    private static KafkaConsumer<Long, GenericRecord> createKafkaConsumer() {
        Properties props = new Properties();

        // Configure Kafka
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + TOPIC_NAME);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use the Apicurio Registry provided Kafka Deserializer for Avro
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, AvroKafkaDeserializer.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload.  So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        //Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka Consumer
        KafkaConsumer<Long, GenericRecord> consumer = new KafkaConsumer<>(props);
        return consumer;
    }

    private static void configureSecurityIfPresent(Properties props) {
        final String tokenEndpoint = System.getenv(SerdeConfig.AUTH_TOKEN_ENDPOINT);
        if (tokenEndpoint != null) {

            final String authClient = System.getenv(SerdeConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SerdeConfig.AUTH_CLIENT_SECRET);

            props.putIfAbsent(SerdeConfig.AUTH_CLIENT_SECRET, authSecret);
            props.putIfAbsent(SerdeConfig.AUTH_CLIENT_ID, authClient);
            props.putIfAbsent(SerdeConfig.AUTH_TOKEN_ENDPOINT, tokenEndpoint);
            props.putIfAbsent(SaslConfigs.SASL_MECHANISM, "OAUTHBEARER");
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS, "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
            props.putIfAbsent("security.protocol", "SASL_SSL");

            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG, String.format("org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required " +
                    "  oauth.client.id=\"%s\" "+
                    "  oauth.client.secret=\"%s\" "+
                    "  oauth.token.endpoint.uri=\"%s\" ;", authClient, authSecret, tokenEndpoint));
        }
    }
}
