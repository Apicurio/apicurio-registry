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

package io.apicurio.registry.examples.confluent.serdes;

import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
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
import io.confluent.kafka.serializers.KafkaAvroSerializer;

/**
 * This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
 * scenario where applications use a mix of Confluent and Apicurio Registry serdes classes.  This
 * example uses the Confluent serializer for the producer and the Apicurio Registry deserializer
 * class for the consumer.
 *
 * <ol>
 *   <li>Configuring a Confluent Kafka Serializer for use with Apicurio Registry</li>
 *   <li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
 *   <li>Auto-register the Avro schema in the registry (registered by the producer)</li>
 *   <li>Data sent as a simple GenericRecord, no java beans needed</li>
 * </ol>
 *
 * Pre-requisites:
 *
 * <ul>
 *   <li>Kafka must be running on localhost:9092</li>
 *   <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author eric.wittmann@gmail.com
 */
public class ConfluentSerdesExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    private static final String CCOMPAT_API_URL = "http://localhost:8080/apis/ccompat/v6";

    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = ConfluentSerdesExample.class.getSimpleName();
    private static final String SUBJECT_NAME = "Greeting";
    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"Greeting\",\"fields\":[{\"name\":\"Message\",\"type\":\"string\"},{\"name\":\"Timestamp\",\"type\":\"long\"}]}";


    public static final void main(String [] args) throws Exception {
        System.out.println("Starting example " + ConfluentSerdesExample.class.getSimpleName());
        String topicName = TOPIC_NAME;
        String subjectName = SUBJECT_NAME;

        // Create the producer.
        Producer<String, Object> producer = createKafkaProducer();
        // Produce 5 messages.
        int producedMessages = 0;
        try {
            Schema schema = new Schema.Parser().parse(SCHEMA);
            System.out.println("Producing (5) messages.");
            for (int idx = 0; idx < 5; idx++) {
                // Use the schema to create a record
                GenericRecord record = new GenericData.Record(schema);
                Date now = new Date();
                String message = "Hello (" + producedMessages++ + ")!";
                record.put("Message", message);
                record.put("Timestamp", now.getTime());

                // Send/produce the message on the Kafka Producer
                ProducerRecord<String, Object> producedRecord = new ProducerRecord<>(topicName, subjectName, record);
                producer.send(producedRecord);

                Thread.sleep(100);
            }
            System.out.println("Messages successfully produced.");
        } finally {
            System.out.println("Closing the producer.");
            producer.flush();
            producer.close();
        }

        // Create the consumer
        System.out.println("Creating the consumer.");
        KafkaConsumer<Long, GenericRecord> consumer = createKafkaConsumer();

        // Subscribe to the topic
        System.out.println("Subscribing to topic " + topicName);
        consumer.subscribe(Collections.singletonList(topicName));

        // Consume the 5 messages.
        try {
            int messageCount = 0;
            System.out.println("Consuming (5) messages.");
            while (messageCount < 5) {
                final ConsumerRecords<Long, GenericRecord> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else records.forEach(record -> {
                    GenericRecord value = record.value();
                    System.out.println("Consumed a message: " + value.get("Message") + " @ " + new Date((long) value.get("Timestamp")));
                });
            }
        } finally {
            consumer.close();
        }

        System.out.println("Done (success).");
    }

    /**
     * Creates the Kafka producer.
     */
    private static Producer<String, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Configure kafka settings
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ProducerConfig.CLIENT_ID_CONFIG, "Producer-" + TOPIC_NAME);
        props.putIfAbsent(ProducerConfig.ACKS_CONFIG, "all");
        props.putIfAbsent(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());

        configureSecurityIfPresent(props);

        RestService restService = new RestService(CCOMPAT_API_URL);
        final Map<String, String> restServiceProperties = new HashMap<>();
        //If auth is enabled using the env var, we try to configure it
        if (Boolean.parseBoolean(System.getenv("CONFIGURE_AUTH"))) {
            restServiceProperties.put("basic.auth.credentials.source", "USER_INFO");
            restServiceProperties.put("schema.registry.basic.auth.user.info", String.format("%s:%s", System.getenv(SerdeConfig.AUTH_CLIENT_ID), System.getenv(SerdeConfig.AUTH_CLIENT_SECRET)));
        }

        CachedSchemaRegistryClient schemaRegistryClient = new CachedSchemaRegistryClient(restService, 100, restServiceProperties);

        Map<String, String> properties = new HashMap<>();

        // Configure Service Registry location (Confluent API)
        properties.put("schema.registry.url", CCOMPAT_API_URL);
        properties.put("auto.register.schemas", "true");
        // Map the topic name to the artifactId in the registry
        properties.put("value.subject.name.strategy", "io.confluent.kafka.serializers.subject.TopicRecordNameStrategy");

        // Use the Confluent provided Kafka Serializer for Avro
        KafkaAvroSerializer valueSerializer = new KafkaAvroSerializer(schemaRegistryClient, properties);
        StringSerializer keySerializer = new StringSerializer();

        // Create the Kafka producer
        Producer<String, Object> producer = new KafkaProducer<String, Object>(props, keySerializer, valueSerializer);
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
        // Enable "Confluent Compatible API" mode in the Apicurio Registry deserializer
        props.putIfAbsent(SerdeConfig.ENABLE_CONFLUENT_ID_HANDLER, Boolean.TRUE);
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
