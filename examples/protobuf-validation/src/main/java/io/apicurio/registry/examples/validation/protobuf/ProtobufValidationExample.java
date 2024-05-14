/*
 * Copyright 2022 Red Hat
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

package io.apicurio.registry.examples.validation.protobuf;

import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.IfExists;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.rest.client.auth.OidcAuth;
import io.apicurio.rest.client.auth.exception.AuthErrorHandler;
import io.apicurio.rest.client.spi.ApicurioHttpClient;
import io.apicurio.rest.client.spi.ApicurioHttpClientFactory;
import io.apicurio.schema.validation.protobuf.ProtobufMetadata;
import io.apicurio.schema.validation.protobuf.ProtobufRecord;
import io.apicurio.schema.validation.protobuf.ProtobufValidationResult;
import io.apicurio.schema.validation.protobuf.ProtobufValidator;
import io.apicurio.schema.validation.protobuf.ref.MessageExampleOuterClass.MessageExample;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static io.apicurio.schema.validation.protobuf.ref.MessageExample2OuterClass.*;

/**
 * This example demonstrates how to use Apicurio Registry Schema Validation library for Protobuf
 * <p>
 * The following aspects are demonstrated:
 *
 * <ol>
 *   <li>Register the Protobuf Schema in the registry</li>
 *   <li>Configuring a Protobuf that will use Apicurio Registry to fetch and cache the schema to use for validation</li>
 *   <li>Successfully validate Java objects using static configuration to always use the same schema for validation</li>
 *   <li>Successfully validate Java objects using dynamic configuration to dynamically choose the schema to use for validation</li>
 * </ol>
 * <p>
 * Pre-requisites:
 *
 * <ul>
 *   <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author carnalca@redhat.com
 */
public class ProtobufValidationExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";

    public static final String SCHEMA =
            "syntax = \"proto3\";\n"
                    + "package io.apicurio.schema.validation.protobuf.ref;\n"
                    + "\n"
                    + "message MessageExample {\n"
                    + "\n"
                    + "  string key = 1;\n"
                    + "  string value = 2;\n"
                    + "\n"
                    + "}";

    public static final void main(String[] args) throws Exception {
        System.out.println("Starting example " + ProtobufValidationExample.class.getSimpleName());

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = ProtobufValidationExample.class.getSimpleName();
        RegistryClient client = createRegistryClient(REGISTRY_URL);
        client.createArtifact("default", artifactId, ArtifactType.PROTOBUF, IfExists.RETURN_OR_UPDATE,
                new ByteArrayInputStream(SCHEMA.getBytes(StandardCharsets.UTF_8)));

        // Create an artifact reference pointing to the artifact we just created
        // and pass it to the ProtobufValidator
        ArtifactReference artifactReference = ArtifactReference.builder().groupId("default")
                .artifactId(artifactId).build();

        // Create the ProtobufValidator providing an ArtifactReference
        // this ArtifactReference will allways be used to lookup the schema in the registry when using "validateByArtifactReference"
        ProtobufValidator validator = createProtobufValidator(artifactReference);

        // Test successfull validation

        MessageExample bean = MessageExample.newBuilder()
                .setKey(UUID.randomUUID().toString())
                .setValue("Hello world").build();

        System.out.println();
        System.out.println("Validating valid message bean");
        ProtobufValidationResult result = validator.validateByArtifactReference(bean);
        System.out.println("Validation result: " + result);
        System.out.println();

        // Test validation error

        MessageExample2 invalidBean = MessageExample2.newBuilder()
                .setKey2(UUID.randomUUID().toString())
                .setValue2(32)
                .build();


        System.out.println("Validating invalid message bean");
        ProtobufValidationResult invalidBeanResult = validator.validateByArtifactReference(invalidBean);
        System.out.println("Validation result: " + invalidBeanResult);
        System.out.println();

        // Test validate method providing a record to dynamically resolve the artifact to fetch from the registry

        ProtobufRecord record = new ProtobufRecord(bean, new ProtobufMetadata(artifactReference));

        System.out.println("Validating message bean using dynamic ArtifactReference resolution");
        ProtobufValidationResult recordValidationResult = validator.validate(record);
        System.out.println("Validation result: " + recordValidationResult);
        System.out.println();

    }

    /**
     * Creates the registry client
     */
    private static RegistryClient createRegistryClient(String registryUrl) {
        final String tokenEndpoint = System.getenv(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT);

        //Just if security values are present, then we configure them.
        if (tokenEndpoint != null) {
            final String authClient = System.getenv(SchemaResolverConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SchemaResolverConfig.AUTH_CLIENT_SECRET);
            ApicurioHttpClient httpClient = ApicurioHttpClientFactory.create(tokenEndpoint,
                    new AuthErrorHandler());
            OidcAuth auth = new OidcAuth(httpClient, authClient, authSecret);
            return RegistryClientFactory.create(registryUrl, Collections.emptyMap(), auth);
        } else {
            return RegistryClientFactory.create(registryUrl);
        }
    }

    /**
     * Creates the protobuf validator
     */
    private static ProtobufValidator createProtobufValidator(ArtifactReference artifactReference) {
        Map<String, Object> props = new HashMap<>();

        // Configure Service Registry location
        props.putIfAbsent(SchemaResolverConfig.REGISTRY_URL, REGISTRY_URL);

        //Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the protobuf validator
        return new ProtobufValidator(props, Optional.ofNullable(artifactReference));
    }

    private static void configureSecurityIfPresent(Map<String, Object> props) {
        final String tokenEndpoint = System.getenv(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT);
        if (tokenEndpoint != null) {

            final String authClient = System.getenv(SchemaResolverConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SchemaResolverConfig.AUTH_CLIENT_SECRET);

            props.putIfAbsent(SchemaResolverConfig.AUTH_CLIENT_SECRET, authSecret);
            props.putIfAbsent(SchemaResolverConfig.AUTH_CLIENT_ID, authClient);
            props.putIfAbsent(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT, tokenEndpoint);
        }
    }
}
