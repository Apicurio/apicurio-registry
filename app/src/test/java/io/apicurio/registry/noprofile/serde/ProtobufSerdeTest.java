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

package io.apicurio.registry.noprofile.serde;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.api.sample.TableNotification;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.resolver.SchemaResolverConfig;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class ProtobufSerdeTest extends AbstractResourceTestBase {

    private RegistryClient restClient;
    private String groupId = "protobuf-serde-test";

    @BeforeEach
    public void createIsolatedClient() {
        restClient = RegistryClientFactory.create(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testProto() throws Exception {
        try (Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<>(restClient);
             Deserializer<DynamicMessage> deserializer = new ProtobufKafkaDeserializer(restClient)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.FALLBACK_ARTIFACT_GROUP_ID, groupId);
            serializer.configure(config, false);
            deserializer.configure(config, false);

            TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

            String topic = generateArtifactId();

            byte[] bytes = serializer.serialize(topic, record);

            waitForSchema(globalId -> {
                if (restClient.getContentByGlobalId(globalId) != null) {
                    ArtifactMetaData artifactMetadata = restClient.getArtifactMetaData(groupId, topic);
                    assertEquals(globalId, artifactMetadata.getGlobalId());
                    return true;
                }
                return false;
            }, bytes);

            DynamicMessage dm = deserializer.deserialize(topic, bytes);
            assertProtobufEquals(record, dm);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testProtobufSchemaWithReferences() {
        try (Serializer<TableNotification> serializer = new ProtobufKafkaSerializer<>(restClient);
             Deserializer<TableNotification> deserializer = new ProtobufKafkaDeserializer(restClient)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.FALLBACK_ARTIFACT_GROUP_ID, groupId);
            serializer.configure(config, false);
            deserializer.configure(config, false);

            byte[] data = serializer.serialize("test", TableNotification.newBuilder().build());
            deserializer.deserialize("test", data);

        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    public void testProtobufSchemaWithReferencesDereferenced() {
        try (Serializer<TableNotification> serializer = new ProtobufKafkaSerializer<>(restClient);
             Deserializer<TableNotification> deserializer = new ProtobufKafkaDeserializer(restClient)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.FALLBACK_ARTIFACT_GROUP_ID, groupId);
            config.put(SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA, "true");
            serializer.configure(config, false);
            deserializer.configure(config, false);

            byte[] data = serializer.serialize("test", TableNotification.newBuilder().build());
            deserializer.deserialize("test", data);
        }
    }

    private void assertProtobufEquals(TestCmmn.UUID record, DynamicMessage dm) {
        Descriptors.Descriptor descriptor = dm.getDescriptorForType();

        Descriptors.FieldDescriptor lsb = descriptor.findFieldByName("lsb");
        Assertions.assertNotNull(lsb);
        Assertions.assertEquals(record.getLsb(), dm.getField(lsb));

        Descriptors.FieldDescriptor msb = descriptor.findFieldByName("msb");
        Assertions.assertNotNull(msb);
        Assertions.assertEquals(record.getMsb(), dm.getField(msb));
    }

}
