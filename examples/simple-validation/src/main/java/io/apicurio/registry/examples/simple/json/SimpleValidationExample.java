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

import io.apicurio.registry.client.common.DefaultVertxInstance;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.ProblemDetails;
import io.apicurio.registry.rest.client.models.VersionContent;
import org.everit.json.schema.ValidationException;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Optional;

/**
 * This example demonstrates how to integrate with Apicurio Registry when performing client-side validation of
 * JSON messages. This example imagines a generic scenario where JSON messages are sent/published to a custom
 * messaging system for later consumption. It assumes that the JSON Schema used for validation must already be
 * registered. The following aspects are demonstrated:
 * <ol>
 * <li>Fetch the JSON Schema from the registry</li>
 * <li>Generate and Validate JSON messages</li>
 * <li>Send validated messages to a messaging system</li>
 * </ol>
 * Pre-requisites:
 * <ul>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author eric.wittmann@gmail.com
 */
public class SimpleValidationExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v3";
    private static final String GROUP = "Examples";
    private static final String ARTIFACT_ID = "MessageType";
    private static final SecureRandom rand = new SecureRandom();

    /**
     * Registers the JSON Schema in the registry if it doesn't already exist.
     *
     * @param registryUrl the registry URL
     * @param group the artifact group
     * @param artifactId the artifact ID
     */
    private static void registerSchema(String registryUrl, String group, String artifactId) throws Exception {
        RegistryClient client = RegistryClientFactory.create(RegistryClientOptions.create(registryUrl));

        try (InputStream schemaStream = SimpleValidationExample.class.getResourceAsStream("/schemas/message.json")) {
            if (schemaStream == null) {
                throw new RuntimeException("Schema file not found in classpath: /schemas/message.json");
            }

            String schemaContent = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);

            CreateArtifact createArtifact = new CreateArtifact();
            createArtifact.setArtifactType("JSON");
            createArtifact.setArtifactId(artifactId);
            createArtifact.setFirstVersion(new CreateVersion());
            createArtifact.getFirstVersion().setContent(new VersionContent());
            createArtifact.getFirstVersion().getContent().setContent(schemaContent);
            createArtifact.getFirstVersion().getContent().setContentType("application/json");

            client.groups().byGroupId(group).artifacts().post(createArtifact, config -> {
                config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
            });

            System.out.println("Schema registered successfully at " + group + "/" + artifactId);
        }
    }

    public static final void main(String[] args) throws Exception {
        System.out.println("Starting example " + SimpleValidationExample.class.getSimpleName());

        // Start a mock broker
        SimpleBroker broker = new SimpleBroker();
        broker.start();

        // Some configuration
        String registryUrl = Optional.ofNullable(System.getenv("REGISTRY_URL")).orElse(REGISTRY_URL);
        String group = Optional.ofNullable(System.getenv("GROUP")).orElse(GROUP);
        String artifactId = Optional.ofNullable(System.getenv("ARTIFACT_ID")).orElse(ARTIFACT_ID);

        // Register the schema in the registry
        registerSchema(registryUrl, group, artifactId);

        // Create a message validator and message publisher
        MessageValidator validator = new MessageValidator(registryUrl, group, artifactId);
        MessagePublisher publisher = new MessagePublisher();

        try {
            // Produce 10 messages
            for (int i = 0; i < 10; i++) {
                // Create a message we want to produce/send
                MessageBean message = new MessageBean();
                message.setMessage("Hello!  A random integer is: " + rand.nextInt());
                message.setTime(System.currentTimeMillis());

                try {
                    // Validate the message before sending it
                    validator.validate(message);

                    // Send the message
                    publisher.publishMessage(message);
                } catch (ValidationException e) {
                    System.err.println("Message validation failed:");
                    System.err.println("  Message: " + e.getMessage());
                    System.err.println("  Failed message: " + message);
                }

                Thread.sleep(1000);
            }
        } catch (ProblemDetails pd) {
            System.err.println("Error communicating with the registry:");
            System.err.println("  Status: " + pd.getStatus());
            System.err.println("  Title: " + pd.getTitle());
            System.err.println("  Detail: " + pd.getDetail());
            if (pd.getName() != null) {
                System.err.println("  Name: " + pd.getName());
            }
            throw new RuntimeException(pd.getDetail());
        } finally {
            // If we do not provide our own instance of Vertx, then we must close the
            // default instance that will get used.
            DefaultVertxInstance.close();
            broker.stop();
        }

        System.out.println("Done (success).");
    }

}
