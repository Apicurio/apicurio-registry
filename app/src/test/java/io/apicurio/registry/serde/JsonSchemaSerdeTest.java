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

package io.apicurio.registry.serde;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.Person;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class JsonSchemaSerdeTest extends AbstractResourceTestBase {

    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        restClient = RegistryClientFactory.create(TestUtils.getRegistryV2ApiUrl());
    }

    @Test
    public void testJsonSchemaSerde() throws Exception {
        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        final Integer globalId = createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema));

        this.waitForGlobalId(globalId);

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Ales", person.getFirstName());
            Assertions.assertEquals("Justin", person.getLastName());
            Assertions.assertEquals(23, person.getAge());

            person.setAge(-1);

            try {
                serializer.serialize(artifactId, new RecordHeaders(), person);
                Assertions.fail();
            } catch (Exception ignored) {
            }

            serializer.setValidationEnabled(false); // disable validation
            // create invalid person bytes
            bytes = serializer.serialize(artifactId, headers, person);

            try {
                deserializer.deserialize(artifactId, headers, bytes);
                Assertions.fail();
            } catch (Exception ignored) {
            }
        }

    }

    @Test
    public void testJsonSchemaSerdeHeaders() throws Exception {
        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        Integer globalId = createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema));

        this.waitForGlobalId(globalId);

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, person);

            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID));
            Header headerGlobalId = headers.lastHeader(SerdeHeaders.HEADER_VALUE_GLOBAL_ID);
            long id = ByteBuffer.wrap(headerGlobalId.value()).getLong();
            assertEquals(globalId.intValue(), Long.valueOf(id).intValue());

            Assertions.assertNotNull(headers.lastHeader(SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE));
            Header headerMsgType = headers.lastHeader(SerdeHeaders.HEADER_VALUE_MESSAGE_TYPE);
            assertEquals(person.getClass().getName(), IoUtil.toString(headerMsgType.value()));

            person = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals("Ales", person.getFirstName());
            Assertions.assertEquals("Justin", person.getLastName());
            Assertions.assertEquals(23, person.getAge());
        }

    }

    @Test
    public void testJsonSchemaSerdeMagicByte() throws Exception {
        InputStream jsonSchema = getClass().getResourceAsStream("/io/apicurio/registry/util/json-schema-with-java-type.json");
        Assertions.assertNotNull(jsonSchema);

        String groupId = TestUtils.generateGroupId();
        String artifactId = generateArtifactId();

        Integer globalId = createArtifact(groupId, artifactId, ArtifactType.JSON, IoUtil.toString(jsonSchema));

        this.waitForGlobalId(globalId);

        Person person = new Person("Ales", "Justin", 23);

        try (JsonSchemaKafkaSerializer<Person> serializer = new JsonSchemaKafkaSerializer<>(restClient, true);
             Deserializer<Person> deserializer = new JsonSchemaKafkaDeserializer<>(restClient, true)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class.getName());
            serializer.configure(config, false);

            deserializer.configure(Collections.emptyMap(), false);

            byte[] bytes = serializer.serialize(artifactId, person);

            TestUtils.waitForSchema(schemaGlobalId -> {
                assertEquals(globalId.intValue(), schemaGlobalId.intValue());
                return true;
            }, bytes);

            person = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals("Ales", person.getFirstName());
            Assertions.assertEquals("Justin", person.getLastName());
            Assertions.assertEquals(23, person.getAge());
        }

    }
}
