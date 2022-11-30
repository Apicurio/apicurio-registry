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

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.HashMap;
import java.util.Map;

import io.apicurio.registry.serde.SerdeConfig;
import io.api.sample.TableNotification;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.RegistryClientFactory;
import io.apicurio.registry.rest.v2.beans.ArtifactMetaData;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.utils.tests.TestUtils;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Fabian Martinez
 */
@QuarkusTest
public class ProtobufSerdeTest extends AbstractResourceTestBase {

    private RegistryClient restClient;
    // Isolating this test in it's own groupId:
    // io.apicurio.registry.rest.client.exception.ArtifactAlreadyExistsException: An artifact with ID 'google/protobuf/timestamp.proto' in group 'default' already exists.
    private String groupId = "protobuf-serde-test";

    @BeforeEach
    public void createIsolatedClient() {
        restClient = RegistryClientFactory.create(TestUtils.getRegistryV2ApiUrl(testPort));
    }

    //FIXME
    //test not working because of getArtifactVersionMetaDataByContent does not find the schema for somereason
//    @Test
//    public void testConfiguration() throws Exception {
//
//        TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();
//        byte[] schema = toSchemaProto(record.getDescriptorForType().getFile()).toByteArray();
////        String schema = IoUtil.toString(toSchemaProto(record));
//
//        String groupId = TestUtils.generateGroupId();
//        String topic = generateArtifactId();
//
//        createArtifact(groupId, topic, ArtifactType.PROTOBUF_FD, IoUtil.toString(schema));
//
//        System.out.println("artifaaact " + clientV2.listArtifactsInGroup(groupId).getArtifacts().get(0).getId());
//
//        Map<String, Object> config = new HashMap<>();
//        config.put(SerdeConfigKeys.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl());
//        config.put(SerdeConfigKeys.ARTIFACT_GROUP_ID, groupId);
//        config.put(SerdeConfigKeys.ARTIFACT_ID_STRATEGY, new SimpleTopicIdStrategy<>());
//        Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<>();
//        serializer.configure(config, true);
//
//        byte[] bytes = serializer.serialize(topic, record);
//
//        Map<String, Object> deserializerConfig = new HashMap<>();
//        deserializerConfig.put(SerdeConfigKeys.REGISTRY_URL, TestUtils.getRegistryV2ApiUrl());
//        Deserializer<DynamicMessage> deserializer = new ProtobufKafkaDeserializer();
//        deserializer.configure(deserializerConfig, true);
//
//        DynamicMessage deserializedRecord = deserializer.deserialize(topic, bytes);
//        assertProtobufEquals(record, deserializedRecord);
//
//        config.put(SerdeConfigKeys.ARTIFACT_ID_STRATEGY, SimpleTopicIdStrategy.class);
//        serializer.configure(config, true);
//        bytes = serializer.serialize(topic, record);
//
//        deserializer.configure(deserializerConfig, true);
//        deserializedRecord = deserializer.deserialize(topic, bytes);
//        assertProtobufEquals(record, deserializedRecord);
//
//        config.put(SerdeConfigKeys.ARTIFACT_ID_STRATEGY, SimpleTopicIdStrategy.class.getName());
//        serializer.configure(config, true);
//        bytes = serializer.serialize(topic, record);
//        deserializer.configure(deserializerConfig, true);
//        deserializedRecord = deserializer.deserialize(topic, bytes);
//        assertProtobufEquals(record, deserializedRecord);
//
//        serializer.close();
//        deserializer.close();
//    }
//
//    private Serde.Schema toSchemaProto(Descriptors.FileDescriptor file) {
//        Serde.Schema.Builder b = Serde.Schema.newBuilder();
//        b.setFile(file.toProto());
//        for (Descriptors.FileDescriptor d : file.getDependencies()) {
//            b.addImport(toSchemaProto(d));
//        }
//        return b.build();
//    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
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

    @SuppressWarnings({ "rawtypes", "unchecked" })
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

            byte[] data = serializer.serialize("test",  TableNotification.newBuilder().build());
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
