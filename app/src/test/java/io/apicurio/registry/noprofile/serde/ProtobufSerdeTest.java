package io.apicurio.registry.noprofile.serde;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.api.sample.TableNotification;
import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.support.TestCmmn;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class ProtobufSerdeTest extends AbstractClientFacadeTestBase {

    private String groupId = "protobuf-serde-test";

    @BeforeEach
    public void createIsolatedClient() {
        CreateRule rule = new CreateRule();
        rule.setConfig("SYNTAX_ONLY");
        rule.setRuleType(RuleType.VALIDITY);
        isolatedClientV3.admin().rules().post(rule);
    }

    @ParameterizedTest(name = "testProto [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testProto(ClientFacadeSupplier clientFacadeSupplier) throws Exception {
        RegistryClientFacade clientFacade = clientFacadeSupplier.getFacade(this);

        try (Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<>(clientFacade);
                Deserializer<DynamicMessage> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

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

            waitForSchema(contentId -> {
                try {
                    if (isolatedClientV3.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0) {
                        VersionMetaData artifactMetadata = isolatedClientV3.groups().byGroupId(groupId).artifacts()
                                .byArtifactId(topic).versions().byVersionExpression("branch=latest").get();
                        assertEquals(contentId.longValue(), artifactMetadata.getContentId());
                        return true;
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }, bytes);

            DynamicMessage dm = deserializer.deserialize(topic, bytes);
            assertProtobufEquals(record, dm);
        }
    }

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @ParameterizedTest(name = "testProtobufSchemaWithReferences [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testProtobufSchemaWithReferences(ClientFacadeSupplier clientFacadeSupplier) {
        RegistryClientFacade clientFacade = clientFacadeSupplier.getFacade(this);

        try (Serializer<TableNotification> serializer = new ProtobufKafkaSerializer<>(clientFacade);
                Deserializer<TableNotification> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

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

    @SuppressWarnings({ "rawtypes", "unchecked" })
    @ParameterizedTest(name = "testProtobufSchemaWithReferencesDereferenced [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testProtobufSchemaWithReferencesDereferenced(ClientFacadeSupplier clientFacadeSupplier) {
        RegistryClientFacade clientFacade = clientFacadeSupplier.getFacade(this);

        try (Serializer<TableNotification> serializer = new ProtobufKafkaSerializer<>(clientFacade);
                Deserializer<TableNotification> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.FALLBACK_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.DEREFERENCE_SCHEMA, "true");
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
