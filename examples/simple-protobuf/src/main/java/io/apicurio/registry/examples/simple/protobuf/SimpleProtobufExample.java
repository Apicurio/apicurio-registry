/*
 * Copyright 2023 JBoss Inc
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

package io.apicurio.registry.examples.simple.protobuf;

import java.time.Duration;
import java.util.Collections;
import java.util.Properties;
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

import com.google.protobuf.DynamicMessage;

import io.apicurio.registry.examples.AddressBookProtos;
import io.apicurio.registry.examples.AddressBookProtos.AddressBook;
import io.apicurio.registry.examples.AddressBookProtos.Person;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.IoUtil;

/**
 * This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe
 * scenario with Protobuf as the serialization type.  The following aspects are demonstrated:
 *
 * <ol>
 *   <li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
 *   <li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
 *   <li>Auto-register the Protobuf schema in the registry (registered by the producer)</li>
 *   <li>Data sent as a custom java bean and received as a generic DynamicMessage</li>
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
public class SimpleProtobufExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = SimpleProtobufExample.class.getSimpleName();
    private static final String SCHEMA_NAME = "AddressBook";


    public static final void main(String [] args) throws Exception {
        System.out.println("Starting example " + SimpleProtobufExample.class.getSimpleName());
        String topicName = TOPIC_NAME;
        String key = SCHEMA_NAME;

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();
        // Produce 2 messages.
        try {
            System.out.println("Producing (2) messages.");
            for (int idx = 0; idx < 2; idx++) {

                AddressBookProtos.AddressBook book = AddressBook.newBuilder()
                        .addPeople(Person.newBuilder()
                                .setEmail("aa@bb.com")
                                .setId(1)
                                .setName("aa")
                                .build())
                        .addPeople(Person.newBuilder()
                                .setEmail("bb@bb.com")
                                .setId(2)
                                .setName("bb")
                                .build())
                        .build();

                // Send/produce the message on the Kafka Producer
                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, key, book);
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
        KafkaConsumer<Long, DynamicMessage> consumer = createKafkaConsumer();

        // Subscribe to the topic
        System.out.println("Subscribing to topic " + topicName);
        consumer.subscribe(Collections.singletonList(topicName));

        // Consume the 2 messages.
        try {
            int messageCount = 0;
            System.out.println("Consuming (2) messages.");
            while (messageCount < 2) {
                final ConsumerRecords<Long, DynamicMessage> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else records.forEach(record -> {
                    DynamicMessage value = record.value();
                    System.out.println("Consumed a message: " + value.toString());
                });
            }
        } finally {
            consumer.close();
        }

        RegistryClient client = RegistryClientFactory.create(REGISTRY_URL);
        System.out.println("The artifact created in Apicurio Registry is: ");
        //because the default ArtifactResolverStrategy is TopicIdStrategy the artifactId is in the form of topicName-value
        System.out.println(IoUtil.toString(client.getArtifactVersion("default", topicName + "-value", "1")));
        System.out.println();
        System.out.println("Done (success).");
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
        // Use the Apicurio Registry provided Kafka Serializer for Protobuf
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ProtobufKafkaSerializer.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.putIfAbsent(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");

        // Register the artifact if not found in the registry.
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
    private static KafkaConsumer<Long, DynamicMessage> createKafkaConsumer() {
        Properties props = new Properties();

        // Configure Kafka
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + TOPIC_NAME);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use the Apicurio Registry provided Kafka Deserializer for Protobuf
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ProtobufKafkaDeserializer.class.getName());

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);

        //this configuration property forces the deserializer to return the generic DynamicMessage
        props.putIfAbsent(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, DynamicMessage.class.getName());

        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload.  So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        //Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka Consumer
        KafkaConsumer<Long, DynamicMessage> consumer = new KafkaConsumer<>(props);
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
