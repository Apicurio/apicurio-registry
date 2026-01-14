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

import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.kafka.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.types.ArtifactType;
import io.vertx.core.Vertx;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.Date;
import java.util.Properties;

/**
 * Java JSON Schema Producer for the Java-Python interoperability example.
 * <p>
 * This producer sends JSON Schema-validated messages that can be consumed by
 * either a Java or Python consumer using the Apicurio Registry SerDes.
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Kafka must be running on localhost:9092</li>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 */
public class JavaJsonSchemaProducer {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String SERVERS = "localhost:9092";
    public static final String TOPIC_NAME = "java-python-interop-json";
    public static final String SUBJECT_NAME = "Greeting";

    // JSON Schema for the Greeting message - same structure used by Python consumers
    public static final String SCHEMA = "{"
            + "\"$id\": \"https://example.com/greeting.schema.json\","
            + "\"$schema\": \"http://json-schema.org/draft-07/schema#\","
            + "\"title\": \"Greeting\","
            + "\"type\": \"object\","
            + "\"required\": [\"message\", \"time\", \"sender\", \"source\"],"
            + "\"properties\": {"
            + "  \"message\": {\"type\": \"string\", \"description\": \"The greeting message\"},"
            + "  \"time\": {\"type\": \"integer\", \"description\": \"Unix timestamp in milliseconds\"},"
            + "  \"sender\": {\"type\": \"string\", \"description\": \"Name of the sender\"},"
            + "  \"source\": {\"type\": \"string\", \"description\": \"Source language (java or python)\"}"
            + "}"
            + "}";

    public static void main(String[] args) throws Exception {
        System.out.println("=== Java JSON Schema Producer (for Python Consumer) ===");
        System.out.println("Starting Java JSON Schema producer...");
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

        // Register the schema first
        registerSchema();

        Producer<Object, Object> producer = createKafkaProducer();

        try {
            System.out.println("Producing " + messageCount + " messages...");
            for (int idx = 0; idx < messageCount; idx++) {
                Date now = new Date();
                GreetingBean greeting = new GreetingBean(
                        "Hello from Java (" + idx + ")!",
                        now.getTime(),
                        "JavaJsonSchemaProducer",
                        "java"
                );

                ProducerRecord<Object, Object> producedRecord = new ProducerRecord<>(
                        TOPIC_NAME, SUBJECT_NAME, greeting);
                producer.send(producedRecord);

                System.out.println("  Sent: " + greeting.getMessage() + " @ " + now);
                Thread.sleep(500);
            }
            System.out.println("All messages produced successfully.");
            System.out.println("\nYou can now run the Python consumer to consume these messages:");
            System.out.println("  cd python && python jsonschema_consumer.py");
        } finally {
            producer.flush();
            producer.close();
        }
    }

    private static void registerSchema() {
        System.out.println("Registering JSON Schema in Apicurio Registry...");
        Vertx vertx = Vertx.vertx();
        try {
            var client = RegistryClientFactory.create(RegistryClientOptions.create(REGISTRY_URL, vertx));

            String artifactId = TOPIC_NAME + "-value";

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactId(artifactId);
            createArtifact.setArtifactType(ArtifactType.JSON);
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(SCHEMA);
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            client.groups().byGroupId("default").artifacts()
                    .post(createArtifact, config -> {
                        config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
                    });

            System.out.println("Schema registered successfully.");
        } finally {
            vertx.close();
        }
    }

    private static Producer<Object, Object> createKafkaProducer() {
        Properties props = new Properties();

        // Kafka settings
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, SERVERS);
        props.put(ProducerConfig.CLIENT_ID_CONFIG, "JavaJsonSchemaProducer");
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSchemaKafkaSerializer.class.getName());

        // Apicurio Registry settings
        props.put(SerdeConfig.REGISTRY_URL, REGISTRY_URL);
        props.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");
        props.put(SerdeConfig.VALIDATION_ENABLED, Boolean.TRUE);
        props.put(KafkaSerdeConfig.ENABLE_HEADERS, true);

        return new KafkaProducer<>(props);
    }
}
