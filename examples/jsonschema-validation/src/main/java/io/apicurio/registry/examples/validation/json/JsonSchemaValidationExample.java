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

package io.apicurio.registry.examples.validation.json;

import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.IfArtifactExists;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.schema.validation.json.JsonMetadata;
import io.apicurio.schema.validation.json.JsonRecord;
import io.apicurio.schema.validation.json.JsonValidationResult;
import io.apicurio.schema.validation.json.JsonValidator;
import io.kiota.http.vertx.VertXRequestAdapter;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static io.apicurio.registry.client.auth.VertXAuthFactory.buildOIDCWebClient;
import static io.apicurio.registry.client.auth.VertXAuthFactory.defaultVertx;

/**
 * This example demonstrates how to use Apicurio Registry Schema Validation library for JSON and JSON Schema.
 * <p>
 * The following aspects are demonstrated:
 * <ol>
 * <li>Register the JSON Schema in the registry</li>
 * <li>Configuring a JsonValidator that will use Apicurio Registry to fetch and cache the schema to use for
 * validation</li>
 * <li>Successfully validate Java objects using static configuration to always use the same schema for
 * validation</li>
 * <li>Successfully validate Java objects using dynamic configuration to dynamically choose the schema to use
 * for validation</li>
 * </ol>
 * <p>
 * Pre-requisites:
 * <ul>
 * <li>Apicurio Registry must be running on localhost:8080</li>
 * </ul>
 *
 * @author eric.wittmann@gmail.com
 */
public class JsonSchemaValidationExample {

    private static final String REGISTRY_URL = "http://localhost:8080/apis/registry/v2";

    public static final String SCHEMA = "{" + "    \"$id\": \"https://example.com/message.schema.json\","
            + "    \"$schema\": \"http://json-schema.org/draft-07/schema#\"," + "    \"required\": ["
            + "        \"message\"," + "        \"time\"" + "    ]," + "    \"type\": \"object\","
            + "    \"properties\": {" + "        \"message\": {" + "            \"description\": \"\","
            + "            \"type\": \"string\"" + "        }," + "        \"time\": {"
            + "            \"description\": \"\"," + "            \"type\": \"number\"" + "        }"
            + "    }" + "}";

    public static final void main(String[] args) throws Exception {
        System.out.println("Starting example " + JsonSchemaValidationExample.class.getSimpleName());

        // Register the schema with the registry (only if it is not already registered)
        String artifactId = JsonSchemaValidationExample.class.getSimpleName();
        RegistryClient client = createRegistryClient(REGISTRY_URL);

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType(ArtifactType.JSON);
        createArtifact.setFirstVersion(new CreateVersion());
        createArtifact.getFirstVersion().setContent(new VersionContent());
        createArtifact.getFirstVersion().getContent()
                .setContent(IoUtil.toString(SCHEMA.getBytes(StandardCharsets.UTF_8)));
        createArtifact.getFirstVersion().getContent().setContentType("application/json");

        client.groups().byGroupId("default").artifacts().post(createArtifact, config -> {
            config.queryParameters.ifExists = IfArtifactExists.FIND_OR_CREATE_VERSION;
        });

        // Create an artifact reference pointing to the artifact we just created
        // and pass it to the JsonValidator
        ArtifactReference artifactReference = ArtifactReference.builder().groupId("default")
                .artifactId(artifactId).build();

        // Create the JsonValidator providing an ArtifactReference
        // this ArtifactReference will allways be used to lookup the schema in the registry when using
        // "validateByArtifactReference"
        JsonValidator validator = createJsonValidator(artifactReference);

        // Test successfull validation

        MessageBean bean = new MessageBean();
        bean.setMessage("Hello world");
        bean.setTime(System.currentTimeMillis());

        System.out.println();
        System.out.println("Validating valid message bean");
        JsonValidationResult result = validator.validateByArtifactReference(bean);
        System.out.println("Validation result: " + result);
        System.out.println();

        // Test validation error

        InvalidMessageBean invalidBean = new InvalidMessageBean();
        invalidBean.setMessage("Hello from invalid bean");
        invalidBean.setTime(new Date());

        System.out.println("Validating invalid message bean");
        JsonValidationResult invalidBeanResult = validator.validateByArtifactReference(invalidBean);
        System.out.println("Validation result: " + invalidBeanResult);
        System.out.println();

        // Test validate method providing a record to dynamically resolve the artifact to fetch from the
        // registry

        JsonRecord record = new JsonRecord(bean, new JsonMetadata(artifactReference));

        System.out.println("Validating message bean using dynamic ArtifactReference resolution");
        JsonValidationResult recordValidationResult = validator.validate(record);
        System.out.println("Validation result: " + recordValidationResult);
        System.out.println();

    }

    /**
     * Creates the registry client
     */
    private static RegistryClient createRegistryClient(String registryUrl) {
        final String tokenEndpoint = System.getenv(SchemaResolverConfig.AUTH_TOKEN_ENDPOINT);

        // Just if security values are present, then we configure them.
        if (tokenEndpoint != null) {
            final String authClient = System.getenv(SchemaResolverConfig.AUTH_CLIENT_ID);
            final String authSecret = System.getenv(SchemaResolverConfig.AUTH_CLIENT_SECRET);
            var adapter = new VertXRequestAdapter(buildOIDCWebClient(tokenEndpoint, authClient, authSecret));
            adapter.setBaseUrl(registryUrl);
            return new RegistryClient(adapter);
        } else {
            VertXRequestAdapter vertXRequestAdapter = new VertXRequestAdapter(defaultVertx);
            vertXRequestAdapter.setBaseUrl(registryUrl);
            return new RegistryClient(vertXRequestAdapter);
        }
    }

    /**
     * Creates the json validator
     */
    private static JsonValidator createJsonValidator(ArtifactReference artifactReference) {
        Map<String, Object> props = new HashMap<>();

        // Configure Service Registry location
        props.putIfAbsent(SchemaResolverConfig.REGISTRY_URL, REGISTRY_URL);

        // Just if security values are present, then we configure them.
        configureSecurityIfPresent(props);

        // Create the json validator
        JsonValidator validator = new JsonValidator(props, Optional.ofNullable(artifactReference));
        return validator;
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
