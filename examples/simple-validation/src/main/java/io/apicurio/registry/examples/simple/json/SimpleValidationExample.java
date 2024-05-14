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

package io.apicurio.registry.examples.simple.json;

import java.security.SecureRandom;
import java.util.Optional;

import org.everit.json.schema.ValidationException;

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
 * <li>JSON schema must be registered at coordinates default/SimpleValidationExample</li>
 * </ul>
 *
 * @author eric.wittmann@gmail.com
 */
public class SimpleValidationExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";
    private static final String GROUP = "Examples";
    private static final String ARTIFACT_ID = "MessageType";
    private static final SecureRandom rand = new SecureRandom();


    public static final void main(String[] args) throws Exception {
        System.out.println("Starting example " + SimpleValidationExample.class.getSimpleName());

        // Start a mock broker
        SimpleBroker broker = new SimpleBroker();
        broker.start();

        // Some configuration
        String registryUrl = Optional.ofNullable(System.getenv("REGISTRY_URL")).orElse(REGISTRY_URL);
        String group = Optional.ofNullable(System.getenv("GROUP")).orElse(GROUP);
        String artifactId = Optional.ofNullable(System.getenv("ARTIFACT_ID")).orElse(ARTIFACT_ID);

        // Create a message validator and message publisher
        MessageValidator validator = new MessageValidator(registryUrl, group, artifactId);
        MessagePublisher publisher = new MessagePublisher();

        // Produce messages in a loop.
        boolean done = false;
        while (!done) {
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
                e.printStackTrace();
            }

            Thread.sleep(5000);
        }

        System.out.println("Done (success).");
    }

}
