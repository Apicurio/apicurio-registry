package io.apicurio.registry.noprofile.serde;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.api.sample.TableNotification;
import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.rest.client.models.CreateArtifact;
import io.apicurio.registry.rest.client.models.CreateRule;
import io.apicurio.registry.rest.client.models.CreateVersion;
import io.apicurio.registry.rest.client.models.RuleType;
import io.apicurio.registry.rest.client.models.VersionContent;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaDeserializer;
import io.apicurio.registry.serde.protobuf.ProtobufKafkaSerializer;
import io.apicurio.registry.serde.strategy.SimpleTopicIdStrategy;
import io.apicurio.registry.serde.strategy.TopicIdStrategy;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.test.PersonProtos;
import io.apicurio.registry.test.UserEventProtos;
import io.apicurio.registry.types.ContentTypes;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.confluent.kafka.serializers.subject.RecordNameStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
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

    private String getSchemaRegistryUrl() {
        return "http://localhost:" + testPort + "/apis/ccompat/v7";
    }

    private SchemaRegistryClient buildSchemaRegistryClient() {
        final List<SchemaProvider> schemaProviders = Arrays.asList(new JsonSchemaProvider(),
                new AvroSchemaProvider(), new ProtobufSchemaProvider());
        return new CachedSchemaRegistryClient(getSchemaRegistryUrl(), 3, schemaProviders, Map.of());
    }

    @ParameterizedTest(name = "testSerdeMix [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testSerdeMix(ClientFacadeSupplier clientFacadeSupplier) throws Exception {
        SchemaRegistryClient schemaRegistryClient = buildSchemaRegistryClient();
        RegistryClientFacade clientFacade = clientFacadeSupplier.getFacade(this);

        // Confluent serializer -> Apicurio deserializer
        try (Serializer<TestCmmn.UUID> serializer = new KafkaProtobufSerializer<>(schemaRegistryClient);
             Deserializer<TestCmmn.UUID> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

            serializer.configure(Map.of(
                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG,  getSchemaRegistryUrl(),
                    AbstractKafkaSchemaSerDeConfig.VALUE_SUBJECT_NAME_STRATEGY, RecordNameStrategy.class,
                    AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, Boolean.TRUE,
                    AbstractKafkaSchemaSerDeConfig.LATEST_COMPATIBILITY_STRICT, false,
                    KafkaProtobufSerializerConfig.SKIP_KNOWN_TYPES_CONFIG, true
            ), false);

            deserializer.configure(Map.of(
                    SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, TestCmmn.UUID.class.getName()
            ), false);

            TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

            String topic = generateArtifactId();

            byte[] bytes = serializer.serialize(topic, record);

            waitForSchema(contentId -> {
                try {
                    if (isolatedClientV3.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0) {
                        VersionMetaData artifactMetadata = isolatedClientV3.groups().byGroupId("default").artifacts()
                                .byArtifactId(record.getDescriptorForType().getFullName()).versions().byVersionExpression("branch=latest").get();
                        assertEquals(contentId.longValue(), artifactMetadata.getContentId());
                        return true;
                    }
                }
                catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }, bytes);

            TestCmmn.UUID deserializedUuid = deserializer.deserialize(topic, bytes);
            assertEquals(record.getMsb(), deserializedUuid.getMsb());
            assertEquals(record.getLsb(), deserializedUuid.getLsb());
        }

        // Apicurio serializer -> Confluent deserializer
        try (Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<>(clientFacade);
             Deserializer<TestCmmn.UUID> deserializer = new KafkaProtobufDeserializer(schemaRegistryClient)) {

            serializer.configure(Map.of(
                    SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class,
                    SerdeConfig.AUTO_REGISTER_ARTIFACT, "true",
                    SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default",
                    SerdeConfig.SEND_TYPE_REF, "false",
                    SerdeConfig.SEND_INDEXES, "true"
            ), false);

            deserializer.configure(Map.of(
                    AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, getSchemaRegistryUrl(),
                    KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, TestCmmn.UUID.class
            ), false);

            TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

            String topic = generateArtifactId();

            byte[] bytes = serializer.serialize(topic, record);

            waitForSchema(contentId -> {
                try {
                    if (isolatedClientV3.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0) {
                        VersionMetaData artifactMetadata = isolatedClientV3.groups().byGroupId("default").artifacts()
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

            TestCmmn.UUID deserializedUuid = deserializer.deserialize(topic, bytes);
            assertEquals(record.getMsb(), deserializedUuid.getMsb());
            assertEquals(record.getLsb(), deserializedUuid.getLsb());
        }

        // Apicurio serializer -> Apicurio deserializer (confluent interop enabled)
        try (Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<>(clientFacade);
             Deserializer<TestCmmn.UUID> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

            serializer.configure(Map.of(
                    SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class,
                    SerdeConfig.AUTO_REGISTER_ARTIFACT, "true",
                    SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default",
                    SerdeConfig.SEND_TYPE_REF, "false",
                    SerdeConfig.SEND_INDEXES, "true"
            ), false);

            deserializer.configure(Map.of(
                    SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, TestCmmn.UUID.class.getName(),
                    SerdeConfig.READ_TYPE_REF, "false",
                    SerdeConfig.READ_INDEXES, "true"
            ), false);

            TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

            String topic = generateArtifactId();

            byte[] bytes = serializer.serialize(topic, record);

            waitForSchema(contentId -> {
                try {
                    if (isolatedClientV3.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0) {
                        VersionMetaData artifactMetadata = isolatedClientV3.groups().byGroupId("default").artifacts()
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

            TestCmmn.UUID deserializedUuid = deserializer.deserialize(topic, bytes);
            assertEquals(record.getMsb(), deserializedUuid.getMsb());
            assertEquals(record.getLsb(), deserializedUuid.getLsb());
        }
    }

    /**
     * Test case for GitHub issue #6672: Schema registration issue with pre-registered Protobuf schemas.
     * This test reproduces the scenario where:
     * 1. A Protobuf schema is pre-registered with artifact ID "-value"
     * 2. Auto-registration is disabled for production
     */
    @ParameterizedTest(name = "testPreRegisteredSchemaReuse [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testPreRegisteredSchemaReuse(ClientFacadeSupplier clientFacadeSupplier) throws Exception {
        RegistryClientFacade clientFacade = clientFacadeSupplier.getFacade(this);
        String topic = generateArtifactId();
        String artifactId = topic + "-value"; // Following the pattern from GitHub discussion

        // Step 1: Pre-register the schema manually
        String protoSchema;
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("schema/user_event.proto")) {
            Assertions.assertNotNull(inputStream, "Could not find user_event.proto resource");
            protoSchema = new String(inputStream.readAllBytes());
        }

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(artifactId);
        createArtifact.setArtifactType("PROTOBUF");

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(protoSchema);
        content.setContentType(ContentTypes.APPLICATION_PROTOBUF);
        firstVersion.setContent(content);
        firstVersion.setName("Initial version");
        firstVersion.setDescription("Pre-registered schema for testing");
        createArtifact.setFirstVersion(firstVersion);

        VersionMetaData preRegisteredVersion = isolatedClientV3.groups().byGroupId(groupId)
                .artifacts().post(createArtifact).getVersion();

        long preRegisteredContentId = preRegisteredVersion.getContentId();

        int numVersions = isolatedClientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get().getCount();

        // Step 2: Test production with auto-registration disabled - should use pre-registered schema
        try (Serializer<UserEventProtos.UserEvent> serializer = new ProtobufKafkaSerializer<>(clientFacade);
             Deserializer<UserEventProtos.UserEvent> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicIdStrategy.class);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "false"); // Disable auto-registration
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_ID, artifactId);
            config.put(SerdeConfig.FIND_LATEST_ARTIFACT, "true");
            config.put(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, UserEventProtos.UserEvent.class.getName());
            serializer.configure(config, false);
            deserializer.configure(config, false);

            UserEventProtos.UserEvent record = UserEventProtos.UserEvent.newBuilder()
                    .setUserId("user-123")
                    .setEventType("purchase")
                    .setTimestamp(System.currentTimeMillis())
                    .build();

            // This should succeed using the pre-registered schema
            byte[] bytes = serializer.serialize(topic, record);

            // Verify the same content ID is being used (schema was reused, not re-registered)
            ByteBuffer actualBuffer = ByteBuffer.wrap(bytes);
            actualBuffer.get(); // read magic byte
            int actualContentId = actualBuffer.getInt(); // read the content Id
            assertEquals(preRegisteredContentId, actualContentId);

            UserEventProtos.UserEvent deserializedRecord = deserializer.deserialize(topic, bytes);
            assertUserEventEquals(record, deserializedRecord);

            // Assert we still only have 1 version of the artifact
            int newNumVersions = isolatedClientV3.groups().byGroupId(groupId).artifacts().byArtifactId(artifactId).versions().get().getCount();
            assertEquals(numVersions, newNumVersions);
        }
    }

    private void assertUserEventEquals(UserEventProtos.UserEvent expected, UserEventProtos.UserEvent actual) {
        assertEquals(expected.getUserId(), actual.getUserId());
        assertEquals(expected.getEventType(), actual.getEventType());
        assertEquals(expected.getTimestamp(), actual.getTimestamp());
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

    /**
     * Test case for GitHub issue #6697: Protobuf schema validation fails when reserved fields are present.
     *
     * This test reproduces the scenario where:
     * 1. A Protobuf schema contains reserved fields
     * 2. The schema is registered in the registry (with reserved fields intact)
     * 3. A compiled Java class is used for serialization (reserved fields stripped by protoc)
     * 4. Validation is enabled on the serializer
     *
     * Without the fix, this would fail with:
     * "The data to send is not compatible with the schema. [ProtobufDifference(message=X reserved fields were removed, message Person)]"
     *
     * With the fix (passing false to findDifferences), the serializer skips the reserved fields check
     * since compiled protobuf classes never contain reserved field information.
     */
    @ParameterizedTest(name = "testProtobufWithReservedFields [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testProtobufWithReservedFields(ClientFacadeSupplier clientFacadeSupplier) throws Exception {
        RegistryClientFacade clientFacade = clientFacadeSupplier.getFacade(this);
        String topic = generateArtifactId();

        // Step 1: Pre-register the schema with reserved fields
        String protoSchemaWithReserved;
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("schema/person_with_reserved.proto")) {
            Assertions.assertNotNull(inputStream, "Could not find person_with_reserved.proto resource");
            protoSchemaWithReserved = new String(inputStream.readAllBytes());
        }

        CreateArtifact createArtifact = new CreateArtifact();
        createArtifact.setArtifactId(topic);
        createArtifact.setArtifactType("PROTOBUF");

        CreateVersion firstVersion = new CreateVersion();
        VersionContent content = new VersionContent();
        content.setContent(protoSchemaWithReserved);
        content.setContentType(ContentTypes.APPLICATION_PROTOBUF);
        firstVersion.setContent(content);
        firstVersion.setName("Schema with reserved fields");
        firstVersion.setDescription("Testing GitHub issue #6697");
        createArtifact.setFirstVersion(firstVersion);

        isolatedClientV3.groups().byGroupId(groupId).artifacts().post(createArtifact);

        // Step 2: Serialize with validation enabled using compiled Java class
        // The compiled class does NOT have reserved field information (stripped by protoc)
        try (Serializer<PersonProtos.Person> serializer = new ProtobufKafkaSerializer<>(clientFacade);
             Deserializer<PersonProtos.Person> deserializer = new ProtobufKafkaDeserializer(clientFacade)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, SimpleTopicIdStrategy.class);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "false"); // Use pre-registered schema
            config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
            config.put(SerdeConfig.VALIDATION_ENABLED, "true"); // IMPORTANT: validation enabled
            config.put(SerdeConfig.FIND_LATEST_ARTIFACT, "true");
            config.put(SerdeConfig.DESERIALIZER_SPECIFIC_VALUE_RETURN_CLASS, PersonProtos.Person.class.getName());
            serializer.configure(config, false);
            deserializer.configure(config, false);

            PersonProtos.Person person = PersonProtos.Person.newBuilder()
                    .setName("John Doe")
                    .setAge(30)
                    .setEmail("john.doe@example.com")
                    .build();

            // This should succeed with the fix (serializer passes false to findDifferences)
            // Without the fix, this would throw:
            // IllegalStateException: The data to send is not compatible with the schema.
            // [ProtobufDifference(message=X reserved fields were removed, message Person)]
            byte[] bytes = serializer.serialize(topic, person);

            // Verify deserialization works
            PersonProtos.Person deserializedPerson = deserializer.deserialize(topic, bytes);
            assertEquals(person.getName(), deserializedPerson.getName());
            assertEquals(person.getAge(), deserializedPerson.getAge());
            assertEquals(person.getEmail(), deserializedPerson.getEmail());
        }
    }

}
