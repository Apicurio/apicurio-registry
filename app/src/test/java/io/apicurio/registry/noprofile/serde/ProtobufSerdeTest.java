package io.apicurio.registry.noprofile.serde;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.api.sample.TableNotification;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class ProtobufSerdeTest extends AbstractResourceTestBase {

    private RegistryClient restClient;
    // Isolating this test in it's own groupId:
    // io.apicurio.registry.rest.client.exception.ArtifactAlreadyExistsException: An artifact with ID 'google/protobuf/timestamp.proto' in group 'default' already exists.
    private String groupId = "protobuf-serde-test";

    @BeforeEach
    public void createIsolatedClient() {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        restClient = new RegistryClient(adapter);
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
                try {
                    if (restClient.ids().globalIds().byGlobalId(globalId).get().readAllBytes().length > 0) {
                        VersionMetaData artifactMetadata = restClient.groups().byGroupId(groupId).artifacts().byArtifactId(topic).versions().byVersionExpression("branch=latest").meta().get();
                        assertEquals(globalId, artifactMetadata.getGlobalId());
                        return true;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
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
