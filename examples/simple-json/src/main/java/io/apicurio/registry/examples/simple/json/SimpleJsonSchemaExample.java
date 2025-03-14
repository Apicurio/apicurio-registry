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

package io.apicurio.registry.examples.simple.json;

import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.vertx.core.Vertx;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.Properties;

/**
 * This example demonstrates how to use the Apicurio Registry in a very simple publish/subscribe scenario with
 * JSON as the serialization type (and JSON Schema for validation). Because JSON Schema is only used for
 * validation (not actual serialization), it can be enabled and disabled without affecting the functionality
 * of the serializers and deserializers. However, if validation is disabled, then incorrect data could be
 * consumed incorrectly.
 * <p>
 * The following aspects are demonstrated:
 * <ol>
 * <li>Register the JSON Schema in the registry</li>
 * <li>Configuring a Kafka Serializer for use with Apicurio Registry</li>
 * <li>Configuring a Kafka Deserializer for use with Apicurio Registry</li>
 * <li>Data sent as a MessageBean</li>
 * </ol>
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Kafka must be running on localhost:9092</li>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author eric.wittmann@gmail.com
 */
public class SimpleJsonSchemaExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    private static final String TOPIC_NAME = SimpleJsonSchemaExample.class.getSimpleName();
    private static final String SUBJECT_NAME = "Greeting";
    public static final String SCHEMA = "{" + "    \"$id\": \"https://example.com/message.schema.json\","
            + "    \"$schema\": \"http://json-schema.org/draft-07/schema#\"," + "    \"required\": ["
            + "        \"message\"," + "        \"time\"" + "    ]," + "    \"type\": \"object\","
            + "    \"properties\": {" + "        \"message\": {" + "            \"description\": \"\","
            + "            \"type\": \"string\"" + "        }," + "        \"time\": {"
            + "            \"description\": \"\"," + "            \"type\": \"number\"" + "        }"
            + "    }" + "}";

    public static void main(String[] args) throws Exception {
        System.out.println("Starting example " + SimpleJsonSchemaExample.class.getSimpleName());
        String topicName = TOPIC_NAME;

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = TOPIC_NAME + "-value"; // use the topic name as the artifactId because we're going
                                                   // to map topic name to artifactId later on.

        Vertx vertx = Vertx.vertx();
        VertXRequestAdapter vertXRequestAdapter = new VertXRequestAdapter(vertx);
        vertXRequestAdapter.setBaseUrl(REGISTRY_URL);

        RegistryClient client = new RegistryClient(vertXRequestAdapter);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent()
                .setContent(IoUtil.toString(SCHEMA.getBytes(StandardCharsets.UTF_8)));
        createArtifact.getFirstVersion().getContent().setContentType("application/json");

        final VersionMetaData created = client.groups().byGroupId("default").artifacts()
                .post(createArtifact, config -> {
                    config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                }).getVersion();

        // Create the producer.
        Producer<Object, Object> producer = createKafkaProducer();
        // Produce 5 messages.
        int producedMessages = 0;
        try {
            System.out.println("Producing (5) messages.");
            for (int idx = 0; idx < 5; idx++) {
                // Create the message to send
                MessageBean message = new MessageBean();
                message.setMessage("Hello (" + producedMessages++ + ")!");
                message.setTime(System.currentTimeMillis());

                // Send/produce the message on the Kafka Producer
                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(topicName, SUBJECT_NAME,
                        message);
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
        KafkaConsumer<Long, MessageBean> consumer = createKafkaConsumer();

        // Subscribe to the topic
        System.out.println("Subscribing to topic " + topicName);
        consumer.subscribe(Collections.singletonList(topicName));

        // Consume the 5 messages.
        try {
            int messageCount = 0;
            System.out.println("Consuming (5) messages.");
            while (messageCount < 5) {
                final ConsumerRecords<Long, MessageBean> records = consumer.poll(Duration.ofSeconds(1));
                messageCount += records.count();
                if (records.count() == 0) {
                    // Do nothing - no messages waiting.
                    System.out.println("No messages waiting...");
                } else
                    records.forEach(record -> {
                        MessageBean msg = record.value();
                        System.out.println(
                                "Consumed a message: " + msg.getMessage() + " @ " + new Date(msg.getTime()));
                    });
            }
        } finally {
            consumer.close();
        }

        vertx.close();
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
        // Use the Apicurio Registry provided Kafka Serializer for JSON Schema
        props.putIfAbsent(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSchemaKafkaSerializer.class.getName());
        // Send metadata in message headers (includes the schema coordinates and the message bean type)
        props.putIfAbsent(KafkaSerdeConfig.ENABLE_HEADERS, true);

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.putIfAbsent(SerdeConfig.AUTO_REGISTER_ARTIFACT, Boolean.FALSE);
        props.putIfAbsent(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");
        props.putIfAbsent(SerdeConfig.VALIDATION_ENABLED, Boolean.TRUE);

        // Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka producer
        Producer<Object, Object> producer = new KafkaProducer<>(props);
        return producer;
    }

    /**
     * Creates the Kafka consumer.
     */
    private static KafkaConsumer<Long, MessageBean> createKafkaConsumer() {
        Properties props = new Properties();

        // Configure Kafka
        props.putIfAbsent(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.putIfAbsent(ConsumerConfig.GROUP_ID_CONFIG, "Consumer-" + TOPIC_NAME);
        props.putIfAbsent(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        props.putIfAbsent(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.putIfAbsent(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.putIfAbsent(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        // Use the Apicurio Registry provided Kafka Deserializer for JSON Schema
        props.putIfAbsent(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JsonSchemaKafkaDeserializer.class.getName());
        // Read metadata from message headers (includes the schema coordinates and the message bean type)
        props.putIfAbsent(KafkaSerdeConfig.ENABLE_HEADERS, true);

        // Configure Service Registry location
        props.putIfAbsent(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        // Enable validation in the deserializer to ensure that the data we receive is valid.
        props.putIfAbsent(SerdeConfig.VALIDATION_ENABLED, Boolean.TRUE);

        // No other configuration needed for the deserializer, because the globalId of the schema
        // the deserializer should use is sent as part of the payload. So the deserializer simply
        // extracts that globalId and uses it to look up the Schema from the registry.

        // Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the Kafka Consumer
        KafkaConsumer<Long, MessageBean> consumer = new KafkaConsumer<>(props);
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
            props.putIfAbsent(SaslConfigs.SASL_LOGIN_CALLBACK_HANDLER_CLASS,
                    "io.strimzi.kafka.oauth.client.JaasClientOauthLoginCallbackHandler");
            props.putIfAbsent("security.protocol", "SASL_SSL");

            props.putIfAbsent(SaslConfigs.SASL_JAAS_CONFIG,
                    String.format(
                            "org.apache.kafka.common.security.oauthbearer.OAuthBearerLoginModule required "
                                    + "  oauth.client.id=\"%s\" " + "  oauth.client.secret=\"%s\" "
                                    + "  oauth.token.endpoint.uri=\"%s\" ;",
                            authClient, authSecret, tokenEndpoint));
        }
    }
}
