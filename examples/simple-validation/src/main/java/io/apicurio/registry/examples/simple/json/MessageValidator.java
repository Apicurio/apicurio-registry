/*
 * Copyright 2021 Red Hat
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.SpecVersionDetector;
import com.networknt.schema.ValidationMessage;
import com.networknt.schema.resource.DisallowSchemaLoader;
import io.apicurio.registry.client.RegistryClientFactory;
import io.apicurio.registry.client.common.RegistryClientOptions;
import io.apicurio.registry.rest.client.RegistryClient;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * @author eric.wittmann@gmail.com
 */
public class MessageValidator {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String group;
    private final String artifactId;
    private final RegistryClient client;

    /**
     * Constructor.
     *
     * @param registryUrl the URL of the Apicurio Registry
     * @param group the artifact group ID
     * @param artifactId the artifact ID
     */
    public MessageValidator(String registryUrl, String group, String artifactId) {
        this.group = group;
        this.artifactId = artifactId;

        this.client = RegistryClientFactory.create(RegistryClientOptions.create(registryUrl));
    }

    /**
     * Validates a message against the JSON Schema from the registry.
     *
     * @param message the message to validate
     * @return a description of each way the message doesn't match the schema; empty if it matches
     * @throws IOException if there's an error fetching the schema
     */
    public List<String> validate(MessageBean message) throws IOException {
        JsonNode jsonSchema;
        try (InputStream schemaIS = client.groups().byGroupId(group).artifacts().byArtifactId(artifactId)
                .versions().byVersionExpression("1").content().get()) {
            jsonSchema = MAPPER.readTree(schemaIS);
        }

        // The draft is the one the schema declares, or draft 7 if it declares none. The schema comes
        // from the registry, so the validator refuses to fetch anything it refers to, from the network
        // or the local file system or classpath; a $ref to another document fails instead.
        VersionFlag draft = SpecVersionDetector.detectOptionalVersion(jsonSchema, false).orElse(VersionFlag.V7);
        JsonSchemaFactory factory = JsonSchemaFactory.getInstance(draft, builder -> builder.schemaLoaders(
                loaders -> loaders.values(List::clear).add(DisallowSchemaLoader.getInstance())));
        return factory.getSchema(jsonSchema)
                .validate(MAPPER.valueToTree(message)).stream()
                .map(ValidationMessage::getMessage)
                .toList();
    }

}
