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

package io.apicurio.registry.noprofile.ccompat.rest.v7;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.ccompat.rest.error.ErrorCode;
import io.apicurio.registry.rest.Headers;
import io.apicurio.registry.rest.client.exception.ArtifactNotFoundException;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.support.HealthUtils;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.connect.avro.AvroConverter;
import io.confluent.kafka.schemaregistry.CompatibilityLevel;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaReference;
import io.confluent.kafka.schemaregistry.client.rest.entities.SchemaString;
import io.confluent.kafka.schemaregistry.client.rest.entities.SubjectVersion;
import io.confluent.kafka.schemaregistry.client.rest.entities.requests.RegisterSchemaRequest;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaDeserializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializer;
import io.confluent.kafka.serializers.json.KafkaJsonSchemaSerializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Typed;
import org.apache.avro.Schema;
import org.apache.avro.SchemaParseException;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static io.confluent.kafka.schemaregistry.CompatibilityLevel.*;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static org.junit.jupiter.api.Assertions.*;


@QuarkusTest
@Typed(ConfluentClientV7Test.class)
@SuppressWarnings({"unchecked", "rawtypes"})
public class ConfluentClientV7Test extends AbstractResourceTestBase {

    public SchemaRegistryClient buildClient() {
        final List<SchemaProvider> schemaProviders = Arrays
                .asList(new JsonSchemaProvider(), new AvroSchemaProvider(), new ProtobufSchemaProvider());
        return new CachedSchemaRegistryClient(new RestService("http://localhost:" + testPort + "/apis/ccompat/v7"), 3, schemaProviders, null, Map.of(Headers.GROUP_ID, "confluentV7-test-group"));
    }

    @AfterEach
    protected void afterEach() {
        try {
            clientV2.deleteArtifactsInGroup(null);
        } catch (ArtifactNotFoundException ignored) {
        }
    }

    @Test
    public void testSerdeProtobufSchema() {
        TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

        final SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        final Map<String, Object> config = new HashMap<>();
        config.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        config.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:" + testPort + "/apis/ccompat/v7");
        config.put(KafkaProtobufDeserializerConfig.SPECIFIC_PROTOBUF_VALUE_TYPE, TestCmmn.UUID.class.getName());

        try (KafkaProtobufSerializer<TestCmmn.UUID> serializer = new KafkaProtobufSerializer<>(client);
             KafkaProtobufDeserializer<TestCmmn.UUID> deserializer = new KafkaProtobufDeserializer<>(client)) {

            serializer.configure(config, false);
            deserializer.configure(config, false);

            byte[] bytes = serializer.serialize(subject, record);
            TestCmmn.UUID deserialized = deserializer.deserialize(subject, bytes);
            Assertions.assertEquals(record, deserialized);
        }
    }

    @Test
    public void testSmoke() throws Exception {
        SchemaRegistryClient client = buildClient();
        Collection<String> subjects = client.getAllSubjects();
        Assertions.assertNotNull(subjects);
    }

    @Test
    public void testSimpleOps() throws Exception {
        SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        ParsedSchema schema1 = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        int id1 = client.register(subject, schema1);

        // Reset the client cache so that the next line actually does what we want.
        client.reset();

        TestUtils.retry(() -> client.getSchemaById(id1));

        ParsedSchema schema2 = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"f2\",\"type\":\"string\"}]}");
        int id2 = client.register(subject, schema2);

        TestUtils.retry(() -> client.getSchemaById(id2));

        ParsedSchema schema = client.getSchemaById(id1);
        Assertions.assertNotNull(schema);

        client.reset();

        Assertions.assertTrue(client.testCompatibility(subject, schema2));

        // global id can be mapped async
        retry(() -> {
            ParsedSchema schema3 = client.getSchemaById(id2);
            Assertions.assertNotNull(schema3);
            return schema3;
        });

        Collection<String> subjects = client.getAllSubjects();
        Assertions.assertTrue(subjects.contains(subject));

        List<Integer> versions = client.getAllVersions(subject);
        Assertions.assertTrue(versions.contains(1));
        Assertions.assertTrue(versions.contains(2));

        int v2 = client.getVersion(subject, schema2);
        Assertions.assertEquals(2, v2);

        int d1 = client.deleteSchemaVersion(subject, "1");
        Assertions.assertEquals(1, d1);
        int d2 = client.deleteSchemaVersion(subject, "2");
        Assertions.assertEquals(2, d2);
    }

    @Test
    public void testSerdeAvro() throws Exception {
        SchemaRegistryClient client = buildClient();

        String subject = generateArtifactId();

        String rawSchema = "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ParsedSchema schema = new AvroSchema(rawSchema);
        int id = client.register(subject + "-value", schema);
        client.reset();

        // global id can be mapped async
        retry(() -> {
            ParsedSchema schema2 = client.getSchemaById(id);
            Assertions.assertNotNull(schema2);
            return schema2;
        });

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(client);
             KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(client)) {

            GenericData.Record record = new GenericData.Record(new Schema.Parser().parse(rawSchema));
            record.put("bar", "somebar");

            byte[] bytes = serializer.serialize(subject, record);
            GenericData.Record ir = (GenericData.Record) deserializer.deserialize(subject, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testSerdeJsonSchema() {

        final SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        final SchemaContent schemaContent = new SchemaContent(
                "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");

        final Properties config = new Properties();
        config.put(KafkaJsonSchemaSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        config.put(KafkaJsonSchemaSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:" + testPort + "/apis/ccompat/v7");

        try (KafkaJsonSchemaSerializer serializer = new KafkaJsonSchemaSerializer(client, new HashMap(config));
             KafkaJsonSchemaDeserializer deserializer = new KafkaJsonSchemaDeserializer(client, config, SchemaContent.class)) {

            byte[] bytes = serializer.serialize(subject, schemaContent);
            Object deserialized = deserializer.deserialize(subject, bytes);
            Assertions.assertEquals(schemaContent, deserialized);
        }
    }

    @Test
    public void testDelete() throws Exception {
        SchemaRegistryClient client = buildClient();

        String nonExisting = generateArtifactId();
        try {
            client.deleteSubject(nonExisting);
            Assertions.fail();
        } catch (RestClientException e) {
            Assertions.assertEquals(404, e.getStatus());
        }

        retry(() -> {
            HealthUtils.assertIsReady(testPort);
            HealthUtils.assertIsLive(testPort);
        });

        String subject = generateArtifactId();

        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        int id = client.register(subject, schema);
        client.reset();

        // global id can be mapped async
        retry(() -> {
            ParsedSchema schema2 = client.getSchemaById(id);
            Assertions.assertNotNull(schema2);
            return schema2;
        });

        Collection<String> subjects = client.getAllSubjects();
        Assertions.assertTrue(subjects.contains(subject));

        client.deleteSubject(subject);

        retry(() -> {
            // delete can be async
            Collection<String> all = client.getAllSubjects();
            Assertions.assertFalse(all.contains(subject));
            return null;
        });
    }

    /**
     * Test for issue: <a href="https://github.com/Apicurio/apicurio-registry/issues/536">...</a>
     */
    @Test
    public void testGlobalRule() throws Exception {
        SchemaRegistryClient client = buildClient();

        Rule rule = new Rule();
        rule.setType(RuleType.COMPATIBILITY);
        rule.setConfig("BACKWARD");
        adminClientV2.createGlobalRule(rule);

        String subject = generateArtifactId();
        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        int id = client.register(subject, schema);
        client.reset();

        // global id can be mapped async
        retry(() -> {
            ParsedSchema schema2 = client.getSchemaById(id);
            Assertions.assertNotNull(schema2);
            return schema2;
        });

        // try to register an incompatible schema
        Assertions.assertThrows(RestClientException.class, () -> {
            ParsedSchema schema2 = new AvroSchema("{\"type\":\"string\"}");
            client.register(subject, schema2);
            client.reset();
        });
    }


    @Test
    public void testConverter_PreRegisterSchema() {
        String subject = generateArtifactId();
        String name = "myr" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        testConverter(
                subject,
                name,
                false,
                (client) -> {
                    try {
                        ParsedSchema schema = new AvroSchema(String.format("{\"type\":\"record\",\"name\":\"%s\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}],\"connect.name\":\"%s\"}", name, name));
                        int id = client.register(subject + "-value", schema);
                        client.reset();
                        // can be async ...
                        ParsedSchema retry = retry(() -> client.getSchemaById(id));
                        Assertions.assertNotNull(retry);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                },
                (c, b) -> {
                }
        );
    }

    @Test
    public void testConverter_AutoRegisterSchema() {
        String name = "myr" + ThreadLocalRandom.current().nextInt(0, Integer.MAX_VALUE);
        testConverter(
                generateArtifactId(),
                name,
                true,
                (c) -> {
                },
                (client, bytes) -> {
                    try {
                        client.reset();
                        ParsedSchema retry = retry(() -> {
                            ByteBuffer buffer = ByteBuffer.wrap(bytes);
                            buffer.get(); // magic-byte
                            int id = buffer.getInt();
                            return client.getSchemaById(id);
                        });
                        Assertions.assertNotNull(retry);
                    } catch (Exception e) {
                        throw new IllegalStateException(e);
                    }
                }
        );
    }

    private void testConverter(String subject, String name, boolean autoRegister, Consumer<SchemaRegistryClient> pre, BiConsumer<SchemaRegistryClient, byte[]> post) {
        SchemaRegistryClient client = buildClient();

        pre.accept(client);

        org.apache.kafka.connect.data.Schema cs =
                org.apache.kafka.connect.data.SchemaBuilder.struct()
                        .name(name).field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA);
        Struct struct = new Struct(cs);
        struct.put("bar", "somebar");

        AvroConverter converter = new AvroConverter(client);
        Map<String, Object> config = new HashMap<>();
        config.put("schema.registry.url", "dummy");
        config.put("auto.register.schemas", autoRegister);
        converter.configure(config, false);

        byte[] bytes = converter.fromConnectData(subject, cs, struct);

        post.accept(client, bytes);

        SchemaAndValue sav = converter.toConnectData(subject, bytes);
        Struct ir = (Struct) sav.value();
        Assertions.assertEquals("somebar", ir.get("bar").toString());
    }

    @Test
    public void testSimpleAvroSchema() throws Exception {
        SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        ParsedSchema schema1 = new AvroSchema("\"string\"");
        int id1 = client.register(subject, schema1);

        // Reset the client cache so that the next line actually does what we want.
        client.reset();

        TestUtils.retry(() -> client.getSchemaById(id1));

        ParsedSchema schema2 = new AvroSchema("{\"type\":\"string\"}");
        int id2 = client.register(subject, schema2);

        TestUtils.retry(() -> client.getSchemaById(id2));
    }

    @Test
    public void testCreateRuleBeforeSchema() throws Exception {
        SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        client.updateCompatibility(subject, "FULL");

        ParsedSchema schema1 = new AvroSchema("\"string\"");
        int id1 = client.register(subject, schema1);

        // Reset the client cache so that the next line actually does what we want.
        client.reset();

        TestUtils.retry(() -> client.getSchemaById(id1));

        ParsedSchema schema2 = new AvroSchema("{\"type\":\"string\"}");
        int id2 = client.register(subject, schema2);

        TestUtils.retry(() -> client.getSchemaById(id2));
    }

    @Test
    public void testBasicAvro() throws Exception {
        String subject1 = "testBasic1";
        String subject2 = "testBasic2";
        int schemasInSubject1 = 10;
        List<Integer> allVersionsInSubject1 = new ArrayList<>();
        List<String> allSchemasInSubject1 = ConfluentTestUtils.getRandomCanonicalAvroString(schemasInSubject1);
        int schemasInSubject2 = 5;
        List<Integer> allVersionsInSubject2 = new ArrayList<>();
        List<String> allSchemasInSubject2 = ConfluentTestUtils.getRandomCanonicalAvroString(schemasInSubject2);
        List<String> allSubjects = new ArrayList<>();

        List<Integer> schemaIds = new ArrayList<>();

        // test getAllVersions with no existing data
        try {
            confluentClient.getAllVersions(subject1);
            fail("Getting all versions from non-existing subject1 should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject");
        }

        // test getAllSubjects with no existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects(), "Getting all subjects should return empty");

        // test registering and verifying new schemas in subject1
        for (int i = 0; i < schemasInSubject1; i++) {
            String schema = allSchemasInSubject1.get(i);
            int expectedVersion = i + 1;
            int schemaId = ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject1);
            schemaIds.add(schemaId);
            allVersionsInSubject1.add(expectedVersion);
        }

        allSubjects.add(subject1);

        // test re-registering existing schemas
        for (int i = 0; i < schemasInSubject1; i++) {
            String schemaString = allSchemasInSubject1.get(i);
            int foundId = confluentClient.registerSchema(schemaString, subject1, true);
            assertEquals((int) schemaIds.get(i), foundId, "Re-registering an existing schema should return the existing version");
        }

        // test registering schemas in subject2
        for (int i = 0; i < schemasInSubject2; i++) {
            String schema = allSchemasInSubject2.get(i);
            int expectedVersion = i + 1;
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject2);
            allVersionsInSubject2.add(expectedVersion);
        }
        allSubjects.add(subject2);

        // test getAllVersions with existing data
        assertEquals(allVersionsInSubject1, confluentClient.getAllVersions(subject1), "Getting all versions from subject1 should match all registered versions");
        assertEquals(allVersionsInSubject2, confluentClient.getAllVersions(subject2), "Getting all versions from subject2 should match all registered versions");

        // test getAllSubjects with existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects(), "Getting all subjects should match all registered subjects");
    }

    @Test
    public void testRegisterSameSchemaOnDifferentSubject() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        int id1 = confluentClient.registerSchema(schema, "subject1", true);
        int id2 = confluentClient.registerSchema(schema, "subject2", true);
        assertEquals(id1, id2, "Registering the same schema under different subjects should return the same id");
    }

    @Test
    public void testRegisterInvalidSchemaBadType() throws Exception {
        String subject = "testRegisterInvalidSchemaBadType";

        //Invalid Field Type 'str'
        String badSchemaString = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"str\",\"name\":\"field1\"}]}";

        try {
            new org.apache.avro.Schema.Parser().parse(badSchemaString);
            fail("Parsing invalid schema string should fail with SchemaParseException");
        } catch (SchemaParseException ignored) {
        }

        try {
            confluentClient.registerSchema(badSchemaString, subject, true);
            fail("Registering schema with invalid field type should fail with " + ErrorCode.INVALID_SCHEMA.value() + " (invalid schema)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode(), "Invalid schema");
        }
    }

    @Test
    public void testRegisterInvalidSchemaBadReference() throws Exception {
        String subject = "testRegisterInvalidSchemaBadReference";

        //Invalid Reference
        SchemaReference invalidReference = new SchemaReference("invalid.schema", "badSubject", 1);
        String schemaString = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"field1\"}]}";

        try {
            confluentClient.registerSchema(schemaString, "AVRO", Collections.singletonList(invalidReference), subject, true);
            fail("Registering schema with invalid reference should fail with " + ErrorCode.INVALID_SCHEMA.value() + " (invalid schema)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode(), "Invalid schema");
        }
    }

    @Test
    public void testCompatibleSchemaLookupBySubject() throws Exception {
        String subject = "testSubject";
        int numRegisteredSchemas = 0;
        int numSchemas = 10;

        List<String> allSchemas = ConfluentTestUtils.getRandomCanonicalAvroString(numSchemas);

        confluentClient.registerSchema(allSchemas.get(0), subject);
        numRegisteredSchemas++;

        // test compatibility of this schema against the latest version under the subject
        String schema1 = allSchemas.get(0);
        boolean isCompatible = confluentClient.testCompatibility(schema1, subject, "latest").isEmpty();
        assertTrue(isCompatible, "First schema registered should be compatible");

        for (int i = 0; i < numSchemas; i++) {
            // Test that compatibility check doesn't change the number of versions
            String schema = allSchemas.get(i);
            assertTrue(confluentClient.testCompatibility(schema, subject, "latest").isEmpty());
            ConfluentTestUtils.checkNumberOfVersions(confluentClient, numRegisteredSchemas, subject);
        }
    }

    @Test
    public void testIncompatibleSchemaLookupBySubject() throws Exception {
        String subject = "testSubject";

        // Make two incompatible schemas - field 'f' has different types
        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"int\",\"name\":" + "\"f" + "\"}]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        // ensure registering incompatible schemas will raise an error
        confluentClient.updateCompatibility(CompatibilityLevel.FULL.name, subject);

        // test that compatibility check for incompatible schema returns false and the appropriate
        // error response from Avro
        confluentClient.registerSchema(schema1, subject);
        int versionOfRegisteredSchema = confluentClient.lookUpSubjectVersion(schema1, subject).getVersion();
        boolean isCompatible = confluentClient.testCompatibility(schema2, subject, String.valueOf(versionOfRegisteredSchema)).isEmpty();
        assertFalse(isCompatible, "Schema should be incompatible with specified version");
    }

    @Test
    public void testIncompatibleSchemaBySubject() throws Exception {
        String subject = "testSubject";

        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"},{\"type\":\"string\",\"name\":\"f2\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"}]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        String schema3String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":\"f1\"},{\"type\":\"string\",\"name\":\"f3\"}]}";
        String schema3 = new AvroSchema(schema3String).canonicalString();

        confluentClient.registerSchema(schema1, subject);
        confluentClient.registerSchema(schema2, subject);

        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD_TRANSITIVE.name, subject);

        //schema3 is compatible with schema2, but not compatible with schema1
        boolean isCompatible = confluentClient.testCompatibility(schema3, subject, "latest").isEmpty();
        assertTrue(isCompatible, "Schema is compatible with the latest version");
        isCompatible = confluentClient.testCompatibility(schema3, subject, null).isEmpty();
        assertFalse(isCompatible, "Schema should be incompatible with FORWARD_TRANSITIVE setting");
        try {
            confluentClient.registerSchema(schema3String, subject);
            fail("Schema register should fail since schema is incompatible");
        } catch (RestClientException e) {
            assertEquals(HTTP_CONFLICT, e.getErrorCode(), "Schema register should fail since schema is incompatible");
            assertFalse(e.getMessage().isEmpty());
        }
    }

    @Test
    public void testSchemaRegistrationUnderDiffSubjects() throws Exception {
        String subject1 = "testSchemaRegistrationUnderDiffSubjects1";
        String subject2 = "testSchemaRegistrationUnderDiffSubjects2";

        // Make two incompatible schemas - field 'f' has different types
        String schemaString1 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schemaString1).canonicalString();

        String schemaString2 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"int\",\"name\":" + "\"foo" + "\"}]}";
        String schema2 = new AvroSchema(schemaString2).canonicalString();

        confluentClient.registerSchema(schema1, subject1);
        int versionOfRegisteredSchema1Subject1 = confluentClient.lookUpSubjectVersion(schema1, subject1).getVersion();
        assertEquals(1, versionOfRegisteredSchema1Subject1, "1st schema under subject1 should have version 1");

        int idOfRegisteredSchema2Subject1 = confluentClient.registerSchema(schema2, subject1);
        int versionOfRegisteredSchema2Subject1 = confluentClient.lookUpSubjectVersion(schema2, subject1).getVersion();
        assertEquals(2, versionOfRegisteredSchema2Subject1, "2nd schema under subject1 should have version 2");

        int idOfRegisteredSchema2Subject2 = confluentClient.registerSchema(schema2, subject2);
        assertEquals(idOfRegisteredSchema2Subject1, idOfRegisteredSchema2Subject2, "Since schema is globally registered but not under subject2, id should not change");
    }

    @Test
    public void testConfigDefaults() throws Exception {
        assertEquals(NONE.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Default compatibility level should be none for this test instance");

        // change it to forward
        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, null);

        assertEquals(FORWARD.name, confluentClient.getConfig(null).getCompatibilityLevel(), "New compatibility level should be forward for this test instance");
    }

    @Test
    public void testNonExistentSubjectConfigChange() throws Exception {
        String subject = "testSubject";
        try {
            confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, subject);
        } catch (RestClientException e) {
            fail("Changing config for an invalid subject should succeed");
        }
        assertEquals(FORWARD.name, confluentClient.getConfig(subject).getCompatibilityLevel(), "New compatibility level for this subject should be forward");
    }

    @Test
    public void testSubjectConfigChange() throws Exception {
        String subject = "testSubjectConfigChange";
        assertEquals(NONE.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Default compatibility level should be none for this test instance");

        // change subject compatibility to forward
        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, subject);

        assertEquals(NONE.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Global compatibility level should remain none for this test instance");

        assertEquals(FORWARD.name, confluentClient.getConfig(subject).getCompatibilityLevel(), "New compatibility level for this subject should be forward");
    }

    @Test
    public void testGlobalConfigChange() throws Exception {
        assertEquals(NONE.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Default compatibility level should be none for this test instance");

        // change subject compatibility to forward
        confluentClient.updateCompatibility(CompatibilityLevel.FORWARD.name, null);
        assertEquals(FORWARD.name, confluentClient.getConfig(null).getCompatibilityLevel(), "New Global compatibility level should be forward");

        // change subject compatibility to backward
        confluentClient.updateCompatibility(BACKWARD.name, null);
        assertEquals(BACKWARD.name, confluentClient.getConfig(null).getCompatibilityLevel(), "New Global compatibility level should be backward");
    }

    @Test
    public void testGetSchemaNonExistingId() throws Exception {
        try {
            confluentClient.getId(100);
            fail("Schema lookup by missing id should fail with " + ErrorCode.SCHEMA_NOT_FOUND.value() + " (schema not found)");
        } catch (RestClientException rce) {
            // this is expected.
            assertEquals(ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing id");
        }
    }

    @Test
    public void testGetSchemaTypes() throws Exception {
        assertEquals(new HashSet<>(Arrays.asList("AVRO", "JSON", "PROTOBUF")), new HashSet<>(confluentClient.getSchemaTypes()));
    }

    @Test
    public void testListVersionsNonExistingSubject() throws Exception {
        try {
            confluentClient.getAllVersions("Invalid");
            fail("Getting all versions of missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException rce) {
            // this is expected.
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject");
        }
    }

    @Test
    public void testGetVersionNonExistentSubject() throws Exception {
        // test getVersion on a non-existing subject
        try {
            confluentClient.getVersion("non-existing-subject", 1);
            fail("Getting version of missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException e) {
            // this is expected.
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), e.getErrorCode(), "Unregistered subject shouldn't be found in getVersion()");
        }
    }

    @Test
    public void testGetNonExistingVersion() throws Exception {
        // test getVersion on a non-existing version
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.getVersion(subject, 200);
            fail("Getting unregistered version should fail with " + ErrorCode.VERSION_NOT_FOUND.value() + " (version not found)");
        } catch (RestClientException e) {
            // this is expected.
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), e.getErrorCode(), "Unregistered version shouldn't be found");
        }
    }

    @Test
    public void testGetInvalidVersion() throws Exception {
        // test getVersion on a non-existing version
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.getVersion(subject, 0);
            fail("Getting invalid version should fail with " + ErrorCode.INVALID_VERSION + " (invalid version)");
        } catch (RestClientException e) {
            // this is expected.
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), e.getErrorCode(), "Invalid version shouldn't be found");
        }
    }

    @Test
    public void testGetVersion() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals(schemas.get(0), confluentClient.getVersion(subject, 1).getSchema(), "Version 1 schema should match");

        assertEquals(schemas.get(1), confluentClient.getVersion(subject, 2).getSchema(), "Version 2 schema should match");
        assertEquals(schemas.get(1), confluentClient.getLatestVersion(subject).getSchema(), "Latest schema should be the same as version 2");
    }

    @Test
    public void testGetLatestVersionSchemaOnly() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals(schemas.get(1), confluentClient.getLatestVersionSchemaOnly(subject), "Latest schema should be the same as version 2");
    }

    @Test
    public void testGetVersionSchemaOnly() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(1);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);

        assertEquals(schemas.get(0), confluentClient.getVersionSchemaOnly(subject, 1), "Retrieved schema should be the same as version 1");
    }

    @Test
    public void testSchemaReferences() throws Exception {
        List<String> schemas = ConfluentTestUtils.getAvroSchemaWithReferences();
        String subject = "testSchemaReferences";
        String referrer = "testSchemaReferencesReferer";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get(1));
        SchemaReference ref = new SchemaReference("otherns.Subrecord", subject, 1);
        request.setReferences(Collections.singletonList(ref));
        int registeredId = confluentClient.registerSchema(request, referrer, false);

        SchemaString schemaString = confluentClient.getId(registeredId);
        // the newly registered schema should be immediately readable on the leader
        assertEquals(schemas.get(1), schemaString.getSchemaString(), "Registered schema should be found");

        assertEquals(Collections.singletonList(ref), schemaString.getReferences(), "Schema references should be found");

        List<Integer> refs = confluentClient.getReferencedBy(subject, 1);
        assertEquals(registeredId, refs.get(0).intValue());

        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, String.valueOf(1));
            fail("Deleting reference should fail with " + ErrorCode.REFERENCE_EXISTS.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.REFERENCE_EXISTS.value(), rce.getErrorCode(), "Reference found");
        }

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, referrer, "1"));

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1"));
    }

    @Test
    public void testSchemaReferencesMultipleLevels() throws Exception {
        String root = "[\"myavro.BudgetDecreased\",\"myavro.BudgetUpdated\"]";

        String ref1 = "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"BudgetDecreased\",\n" + "  \"namespace\" : \"myavro\",\n" + "  \"fields\" : [ {\n" + "    \"name\" : \"buyerId\",\n" + "    \"type\" : \"long\"\n" + "  }, {\n" + "    \"name\" : \"currency\",\n" + "    \"type\" : {\n" + "      \"type\" : \"myavro.currencies.Currency\"" + "    }\n" + "  }, {\n" + "    \"name\" : \"amount\",\n" + "    \"type\" : \"double\"\n" + "  } ]\n" + "}";

        String ref2 = "{\n" + "  \"type\" : \"record\",\n" + "  \"name\" : \"BudgetUpdated\",\n" + "  \"namespace\" : \"myavro\",\n" + "  \"fields\" : [ {\n" + "    \"name\" : \"buyerId\",\n" + "    \"type\" : \"long\"\n" + "  }, {\n" + "    \"name\" : \"currency\",\n" + "    \"type\" : {\n" + "      \"type\" : \"myavro.currencies.Currency\"" + "    }\n" + "  }, {\n" + "    \"name\" : \"updatedValue\",\n" + "    \"type\" : \"double\"\n" + "  } ]\n" + "}";

        String sharedRef = "{\n" + "      \"type\" : \"enum\",\n" + "      \"name\" : \"Currency\",\n" + "      \"namespace\" : \"myavro.currencies\",\n" + "      \"symbols\" : [ \"EUR\", \"USD\" ]\n" + "    }\n";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, new AvroSchema(sharedRef).canonicalString(), "shared");

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(ref1);
        SchemaReference ref = new SchemaReference("myavro.currencies.Currency", "shared", 1);
        request.setReferences(Collections.singletonList(ref));
        confluentClient.registerSchema(request, "ref1", false);

        request = new RegisterSchemaRequest();
        request.setSchema(ref2);
        ref = new SchemaReference("myavro.currencies.Currency", "shared", 1);
        request.setReferences(Collections.singletonList(ref));
        confluentClient.registerSchema(request, "ref2", false);

        request = new RegisterSchemaRequest();
        request.setSchema(root);
        SchemaReference r1 = new SchemaReference("myavro.BudgetDecreased", "ref1", 1);
        SchemaReference r2 = new SchemaReference("myavro.BudgetUpdated", "ref2", 1);
        request.setReferences(Arrays.asList(r1, r2));
        int registeredSchema = confluentClient.registerSchema(request, "root", false);

        SchemaString schemaString = confluentClient.getId(registeredSchema);
        // the newly registered schema should be immediately readable on the leader
        assertEquals(root, schemaString.getSchemaString(), "Registered schema should be found");

        assertEquals(Arrays.asList(r1, r2), schemaString.getReferences(), "Schema references should be found");
    }

    @Test
    public void testSchemaMissingReferences() {
        List<String> schemas = ConfluentTestUtils.getAvroSchemaWithReferences();

        RegisterSchemaRequest request = new RegisterSchemaRequest();
        request.setSchema(schemas.get(1));
        request.setReferences(Collections.emptyList());

        assertThrows(RestClientException.class, () -> confluentClient.registerSchema(request, "referrer", false));
    }

    @Test
    public void testSchemaNormalization() throws Exception {
        String subject1 = "testSchemaNormalization";

        String reference1 = "{\"type\":\"record\"," + "\"name\":\"Subrecord1\"," + "\"namespace\":\"otherns\"," + "\"fields\":" + "[{\"name\":\"field1\",\"type\":\"string\"}]}";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, reference1, "ref1");
        String reference2 = "{\"type\":\"record\"," + "\"name\":\"Subrecord2\"," + "\"namespace\":\"otherns\"," + "\"fields\":" + "[{\"name\":\"field2\",\"type\":\"string\"}]}";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, reference2, "ref2");

        SchemaReference ref1 = new SchemaReference("otherns.Subrecord1", "ref1", 1);
        SchemaReference ref2 = new SchemaReference("otherns.Subrecord2", "ref2", 1);

        // Two versions of same schema, the second one with extra spaces and line breaks
        String schemaString1 = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":{\"type\":\"int\"},\"name\":\"field0" + "\"}," + "{\"name\":\"field1\",\"type\":\"otherns.Subrecord1\"}," + "{\"name\":\"field2\",\"type\":\"otherns.Subrecord2\"}" + "]," + "\"extraMetadata\": {\"a\": 1, \"b\": 2}" + "}";
        String schemaString2 = "{\"type\":\"record\",\n" + "\"name\":\"myrecord\",\n" + "\"fields\":" + "[{\"type\":{\"type\":\"int\"},\"name\":\"field0" + "\"},\n" + "{\"name\":\"field1\",\"type\":\"otherns.Subrecord1\"}," + "{\"name\":\"field2\",\"type\":\"otherns.Subrecord2\"}" + "]," + "\"extraMetadata\": {\"a\": 1, \"b\": 2}" + "}";

        RegisterSchemaRequest registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(schemaString1);
        registerRequest.setReferences(Arrays.asList(ref1, ref2));
        confluentClient.registerSchema(registerRequest, subject1, true);

        int versionOfRegisteredSchema1Subject1 = confluentClient.lookUpSubjectVersion(registerRequest, subject1, false, false).getVersion();


        // send schema with all references resolved
        RegisterSchemaRequest lookUpRequest = new RegisterSchemaRequest();
        org.apache.avro.Schema.Parser parser = new org.apache.avro.Schema.Parser();
        parser.parse(reference1);
        parser.parse(reference2);
        AvroSchema resolvedSchema = new AvroSchema(parser.parse(schemaString2));
        lookUpRequest.setSchema(resolvedSchema.canonicalString());
        versionOfRegisteredSchema1Subject1 = confluentClient.lookUpSubjectVersion(lookUpRequest, subject1, true, false).getVersion();
        assertEquals(1, versionOfRegisteredSchema1Subject1, "1st schema under subject1 should have version 1");


        String recordInvalidDefaultSchema = "{\"namespace\": \"namespace\",\n" + " \"type\": \"record\",\n" + " \"name\": \"test\",\n" + " \"fields\": [\n" + "     {\"name\": \"string_default\", \"type\": \"string\", \"default\": null}\n" + "]\n" + "}";
        registerRequest = new RegisterSchemaRequest();
        registerRequest.setSchema(recordInvalidDefaultSchema);
        try {
            confluentClient.registerSchema(registerRequest, subject1, true);
            fail("Registering bad schema should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode(), "Invalid schema");
        }
    }

    @Test
    public void testBad() throws Exception {
        String subject1 = "testBad";
        List<String> allSubjects = new ArrayList<>();

        // test getAllSubjects with no existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects(), "Getting all subjects should return empty");

        try {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, ConfluentTestUtils.getBadSchema(), subject1);
            fail("Registering bad schema should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode(), "Invalid schema");
        }

        try {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0), List.of(new SchemaReference("bad", "bad", 100)), subject1);
            fail("Registering bad reference should fail with " + ErrorCode.INVALID_SCHEMA.value());
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.INVALID_SCHEMA.value(), rce.getErrorCode(), "Invalid schema");
        }

        // test getAllSubjects with existing data
        assertEquals(allSubjects, confluentClient.getAllSubjects(), "Getting all subjects should match all registered subjects");
    }

    @Test
    public void testLookUpSchemaUnderNonExistentSubject() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        try {
            confluentClient.lookUpSubjectVersion(schema, "non-existent-subject");
            fail("Looking up schema under missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Subject not found");
        }
    }

    @Test
    public void testLookUpNonExistentSchemaUnderSubject() throws Exception {
        String subject = "test";
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        confluentClient.updateCompatibility(CompatibilityLevel.NONE.name, subject);

        try {
            confluentClient.lookUpSubjectVersion(schemas.get(1), subject);
            fail("Looking up missing schema under subject should fail with " + ErrorCode.SCHEMA_NOT_FOUND.value() + " (schema not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode(), "Schema not found");
        }
    }

    @Test
    public void testGetVersionsAssociatedWithSchemaId() throws Exception {
        String subject1 = "testGetVersionsAssociatedWithSchemaId1";
        String subject2 = "testGetVersionsAssociatedWithSchemaId2";

        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);

        int registeredSchemaId = confluentClient.registerSchema(schema, subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject2);

        List<SubjectVersion> associatedSubjects = confluentClient.getAllVersionsById(registeredSchemaId);
        assertEquals(associatedSubjects.size(), 2);
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject1, 1)));
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject2, 1)));

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject2, "1"), "Deleting Schema Version Success");

        associatedSubjects = confluentClient.getAllVersionsById(registeredSchemaId);
        assertEquals(associatedSubjects.size(), 1);
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject1, 1)));

        associatedSubjects = confluentClient.getAllVersionsById(RestService.DEFAULT_REQUEST_PROPERTIES, registeredSchemaId, null, true);
        assertEquals(associatedSubjects.size(), 2);
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject1, 1)));
        assertTrue(associatedSubjects.contains(new SubjectVersion(subject2, 1)));
    }

    @Test
    public void testCompatibilityNonExistentVersion() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "testCompatibilityNonExistentVersion";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.testCompatibility(schema, subject, "100");
            fail("Testing compatibility for missing version should fail with " + ErrorCode.VERSION_NOT_FOUND.value() + " (version not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Version not found");
        }
    }

    @Test
    public void testCompatibilityInvalidVersion() throws Exception {
        String schema = ConfluentTestUtils.getRandomCanonicalAvroString(1).get(0);
        String subject = "test";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schema, subject);
        try {
            confluentClient.testCompatibility(schema, subject, "earliest");
            fail("Testing compatibility for invalid version should fail with " + ErrorCode.VERSION_NOT_FOUND.value() + " (version not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Version not found");
        }
    }

    @Test
    public void testGetConfigNonExistentSubject() throws Exception {
        try {
            confluentClient.getConfig("non-existent-subject");
            fail("Getting the configuration of a missing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " error code (subject not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Subject not found");
        }
    }

    @Test
    public void testCanonicalization() throws Exception {
        // schema string with extra white space
        String schema = "{   \"type\":   \"string\"}";
        String subject = "test";

        int id = confluentClient.registerSchema(schema, subject);

        assertEquals(id, confluentClient.registerSchema(schema, subject), "Registering the same schema should get back the same id");

        assertEquals(id, confluentClient.lookUpSubjectVersion(schema, subject).getId().intValue(), "Lookup the same schema should get back the same id");
    }

    @Test
    public void testDeleteSchemaVersionBasic() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSchemaVersionBasic";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals((Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2"), "Deleting Schema Version Success");

        assertEquals(Collections.singletonList(1), confluentClient.getAllVersions(subject));

        try {
            confluentClient.getVersion(subject, 2);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Version not found");
        }
        try {
            RegisterSchemaRequest request = new RegisterSchemaRequest();
            request.setSchema(schemas.get(1));
            confluentClient.lookUpSubjectVersion(RestService.DEFAULT_REQUEST_PROPERTIES, request, subject, false, false);
            fail(String.format("Lookup Subject Version %s for subject %s should fail with %s", "2", subject, ErrorCode.SCHEMA_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode(), "Schema not found");
        }

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"), "Deleting Schema Version Success");
        try {
            List<Integer> versions = confluentClient.getAllVersions(subject);
            fail("Getting all versions from non-existing subject1 should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found). Got " + versions);
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject");
        }

        //re-register twice and versions should be same
        for (int i = 0; i < 2; i++) {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
            assertEquals(Collections.singletonList(3), confluentClient.getAllVersions(subject));
        }

    }

    @Test
    public void testDeleteSchemaVersionPermanent() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        //permanent delete without soft delete first
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2", true);
            fail("Permanent deleting first time should throw schemaVersionNotSoftDeletedException");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SCHEMA_VERSION_NOT_SOFT_DELETED.value(), rce.getErrorCode(), "Schema version must be soft deleted first");
        }

        //soft delete
        assertEquals((Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2"), "Deleting Schema Version Success");

        assertEquals(Collections.singletonList(1), confluentClient.getAllVersions(subject));
        assertEquals(Arrays.asList(1, 2), confluentClient.getAllVersions(RestService.DEFAULT_REQUEST_PROPERTIES, subject, true));
        //soft delete again
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2");
            fail("Soft deleting second time should throw schemaVersionSoftDeletedException");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SCHEMA_VERSION_SOFT_DELETED.value(), rce.getErrorCode(), "Schema version already soft deleted");
        }

        try {
            confluentClient.getVersion(subject, 2);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Version not found");
        }

        io.confluent.kafka.schemaregistry.client.rest.entities.Schema schema = confluentClient.getVersion(subject, 2, true);
        assertEquals((Integer) 2, schema.getVersion(), "Lookup Version Match");

        try {
            RegisterSchemaRequest request = new RegisterSchemaRequest();
            request.setSchema(schemas.get(1));
            confluentClient.lookUpSubjectVersion(RestService.DEFAULT_REQUEST_PROPERTIES, request, subject, false, false);
            fail(String.format("Lookup Subject Version %s for subject %s should fail with %s", "2", subject, ErrorCode.SCHEMA_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode(), "Schema not found");
        }
        // permanent delete
        assertEquals((Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2", true), "Deleting Schema Version Success");
        // GET after permanent delete should give exception
        try {
            confluentClient.getVersion(subject, 2, true);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Version not found");
        }
        //permanent delete again
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2", true);
            fail(String.format("Getting Version %s for subject %s should fail with %s", "2", subject, ErrorCode.VERSION_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Version not found");
        }

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"), "Deleting Schema Version Success");

        try {
            List<Integer> versions = confluentClient.getAllVersions(subject);
            fail("Getting all versions from non-existing subject1 should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found). Got " + versions);
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject");
        }

        //re-register twice and versions should be same
        //after permanent delete of 2, the new version coming up will be 2
        for (int i = 0; i < 2; i++) {
            ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
            assertEquals(Collections.singletonList(2), confluentClient.getAllVersions(subject));
        }

    }

    @Test
    public void testDeleteSchemaVersionInvalidSubject() throws Exception {
        try {
            String subject = "testDeleteSchemaVersionInvalidSubject";
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1");
            fail("Deleting a non existent subject version should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " error code (subject not found)");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Subject not found");
        }
    }

    @Test
    public void testDeleteLatestVersion() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(3);
        String subject = "testDeleteLatestVersion";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals((Integer) 2, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"), "Deleting Schema Version Success");

        io.confluent.kafka.schemaregistry.client.rest.entities.Schema schema = confluentClient.getLatestVersion(subject);
        assertEquals(schemas.get(0), schema.getSchema(), "Latest Version Schema");

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest"), "Deleting Schema Version Success");
        try {
            confluentClient.getLatestVersion(subject);
            fail("Getting latest versions from non-existing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found).");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject");
        }

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(2), subject);
        assertEquals(schemas.get(2), confluentClient.getLatestVersion(subject).getSchema(), "Latest version available after subject re-registration");
    }

    @Test
    public void testGetLatestVersionNonExistentSubject() throws Exception {
        String subject = "non_existent_subject";

        try {
            confluentClient.getLatestVersion(subject);
            fail("Getting latest versions from non-existing subject should fail with " + ErrorCode.SUBJECT_NOT_FOUND.value() + " (subject not found).");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject");
        }
    }

    @Test
    public void testGetLatestVersionDeleteOlder() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "test";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);

        assertEquals(schemas.get(1), confluentClient.getLatestVersion(subject).getSchema(), "Latest Version Schema");

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1"), "Deleting Schema Older Version Success");
        assertEquals(schemas.get(1), confluentClient.getLatestVersion(subject).getSchema(), "Latest Version Schema Still Same");
    }

    @Test
    public void testDeleteInvalidVersion() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(1);
        String subject = "test";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        try {
            confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.VERSION_NOT_FOUND.value(), rce.getErrorCode(), "Should get a 404 status for non-existing subject version");
        }

    }

    @Test
    public void testDeleteWithLookup() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteWithLookup";

        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1"), "Deleting Schema Version Success");
        try {
            confluentClient.lookUpSubjectVersion(schemas.get(0), subject, false);
            fail(String.format("Lookup Subject Version %s for subject %s should fail with %s", "2", subject, ErrorCode.SCHEMA_NOT_FOUND.value()));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SCHEMA_NOT_FOUND.value(), rce.getErrorCode(), "Schema not found");
        }
        //verify deleted schema
        io.confluent.kafka.schemaregistry.client.rest.entities.Schema schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals((Integer) 1, schema.getVersion(), "Lookup Version Match");

        //re-register schema again and verify we get latest version
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals((Integer) 3, schema.getVersion(), "Lookup Version Match");
        schema = confluentClient.lookUpSubjectVersion(schemas.get(1), subject, false);
        assertEquals((Integer) 2, schema.getVersion(), "Lookup Version Match");
    }

    @Test
    public void testIncompatibleSchemaLookupBySubjectAfterDelete() throws Exception {
        String subject = "testIncompatibleSchemaLookupBySubjectAfterDelete";

        // Make two incompatible schemas - field 'g' has different types
        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String wrongSchema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"string\",\"name\":" + "\"g\" , \"default\":\"d\"}" + "]}";
        String wrongSchema2 = new AvroSchema(wrongSchema2String).canonicalString();

        String correctSchema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"int\",\"name\":" + "\"g\" , \"default\":0}" + "]}";
        String correctSchema2 = new AvroSchema(correctSchema2String).canonicalString();
        // ensure registering incompatible schemas will raise an error
        confluentClient.updateCompatibility(CompatibilityLevel.BACKWARD.name, subject);

        // test that compatibility check for incompatible schema returns false and the appropriate
        // error response from Avro
        confluentClient.registerSchema(schema1, subject, true);

        boolean isCompatible = confluentClient.testCompatibility(wrongSchema2, subject, "latest").isEmpty();
        assertTrue(isCompatible, "Schema should be compatible with specified version");

        confluentClient.registerSchema(wrongSchema2, subject, true);

        isCompatible = confluentClient.testCompatibility(correctSchema2, subject, "latest").isEmpty();
        assertFalse(isCompatible, "Schema should be incompatible with specified version");
        try {
            confluentClient.registerSchema(correctSchema2, subject);
            fail("Schema should be Incompatible");
        } catch (RestClientException rce) {
            assertEquals(HTTP_CONFLICT, rce.getErrorCode(), "Incompatible Schema");
        }

        confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "latest");
        isCompatible = confluentClient.testCompatibility(correctSchema2, subject, "latest").isEmpty();
        assertTrue(isCompatible, "Schema should be compatible with specified version");

        confluentClient.registerSchema(correctSchema2, subject, true);

        assertEquals((Integer) 3, confluentClient.lookUpSubjectVersion(correctSchema2String, subject, true, false).getVersion(), "Version is same");

    }

    @Test
    public void testSubjectCompatibilityAfterDeletingAllVersions() throws Exception {
        String subject = "testSubjectCompatibilityAfterDeletingAllVersions";

        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"string\",\"name\":" + "\"g\" , \"default\":\"d\"}" + "]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        confluentClient.updateCompatibility(CompatibilityLevel.FULL.name, null);
        confluentClient.updateCompatibility(CompatibilityLevel.BACKWARD.name, subject);

        confluentClient.registerSchema(schema1, subject, true);
        confluentClient.registerSchema(schema2, subject);

        confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "1");
        assertEquals(BACKWARD.name, confluentClient.getConfig(subject).getCompatibilityLevel(), "Compatibility Level Exists");
        assertEquals(FULL.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Top Compatibility Level Exists");
        confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject, "2");
        try {
            confluentClient.getConfig(subject);
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_COMPATIBILITY_NOT_CONFIGURED.value(), rce.getErrorCode(), "Compatibility Level doesn't exist");
        }
        assertEquals(FULL.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Top Compatibility Level Exists");

    }

    @Test
    public void testListSubjects() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject1 = "test1";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject1);
        String subject2 = "test2";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject2);

        List<String> expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        expectedResponse.add(subject2);
        assertEquals(expectedResponse, confluentClient.getAllSubjects(), "Current Subjects");
        List<Integer> deletedResponse = new ArrayList<>();
        deletedResponse.add(1);
        assertEquals(deletedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject2), "Versions Deleted Match");

        expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        assertEquals(expectedResponse, confluentClient.getAllSubjects(), "Current Subjects");

        expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        expectedResponse.add(subject2);
        assertEquals(expectedResponse, confluentClient.getAllSubjects(true), "Current Subjects");

        assertEquals(deletedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject2, true), "Versions Deleted Match");

        expectedResponse = new ArrayList<>();
        expectedResponse.add(subject1);
        assertEquals(expectedResponse, confluentClient.getAllSubjects(), "Current Subjects");
    }

    @Test
    public void testListSoftDeletedSubjectsAndSchemas() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(3);
        String subject1 = "test1";
        String subject2 = "test2";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject1);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(2), subject2);

        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject1, "1"));
        assertEquals((Integer) 1, confluentClient.deleteSchemaVersion(RestService.DEFAULT_REQUEST_PROPERTIES, subject2, "1"));

        assertEquals(Collections.singletonList(2), confluentClient.getAllVersions(subject1), "List All Versions Match");
        assertEquals(Arrays.asList(1, 2), confluentClient.getAllVersions(RestService.DEFAULT_REQUEST_PROPERTIES, subject1, true), "List All Versions Include deleted Match");

        assertEquals(Collections.singletonList(subject1), confluentClient.getAllSubjects(), "List All Subjects Match");
        assertEquals(Arrays.asList(subject1, subject2), confluentClient.getAllSubjects(true), "List All Subjects Include deleted Match");
    }

    @Test
    public void testDeleteSubjectBasic() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSubjectBasic";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        List<Integer> expectedResponse = new ArrayList<>();
        expectedResponse.add(1);
        expectedResponse.add(2);
        assertEquals(expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject), "Versions Deleted Match");
        try {
            confluentClient.getLatestVersion(subject);
            fail(String.format("Subject %s should not be found", subject));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Subject Not Found");
        }

    }

    @Test
    public void testDeleteSubjectException() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSubjectException";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        List<Integer> expectedResponse = new ArrayList<>();
        expectedResponse.add(1);
        expectedResponse.add(2);
        assertEquals(expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject), "Versions Deleted Match");

        io.confluent.kafka.schemaregistry.client.rest.entities.Schema schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals(1, (long) schema.getVersion());
        schema = confluentClient.lookUpSubjectVersion(schemas.get(1), subject, true);
        assertEquals(2, (long) schema.getVersion());

        try {
            confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject);
            fail(String.format("Subject %s should not be found", subject));
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_SOFT_DELETED.value(), rce.getErrorCode(), "Subject exists in soft deleted format.");
        }
    }


    @Test
    public void testDeleteSubjectPermanent() throws Exception {
        List<String> schemas = ConfluentTestUtils.getRandomCanonicalAvroString(2);
        String subject = "testDeleteSubjectPermanent";
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(0), subject);
        ConfluentTestUtils.registerAndVerifySchema(confluentClient, schemas.get(1), subject);
        List<Integer> expectedResponse = new ArrayList<>();
        expectedResponse.add(1);
        expectedResponse.add(2);

        try {
            confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject, true);
            fail("Delete permanent should not succeed");
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_NOT_SOFT_DELETED.value(), rce.getErrorCode(), "Subject '%s' was not deleted first before permanent delete");
        }

        assertEquals(expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject), "Versions Deleted Match");

        io.confluent.kafka.schemaregistry.client.rest.entities.Schema schema = confluentClient.lookUpSubjectVersion(schemas.get(0), subject, true);
        assertEquals(1, (long) schema.getVersion());
        schema = confluentClient.lookUpSubjectVersion(schemas.get(1), subject, true);
        assertEquals(2, (long) schema.getVersion());

        assertEquals(expectedResponse, confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject, true), "Versions Deleted Match");
        for (Integer i : expectedResponse) {
            try {
                confluentClient.lookUpSubjectVersion(schemas.get(0), subject, false);
                fail(String.format("Subject %s should not be found", subject));
            } catch (RestClientException rce) {
                assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Subject Not Found");
            }

            try {
                confluentClient.lookUpSubjectVersion(schemas.get(i - 1), subject, true);
                fail(String.format("Subject %s should not be found", subject));
            } catch (RestClientException rce) {
                assertEquals(ErrorCode.SUBJECT_NOT_FOUND.value(), rce.getErrorCode(), "Subject Not Found");
            }
        }
    }

    @Test
    public void testSubjectCompatibilityAfterDeletingSubject() throws Exception {
        String subject = "testSubjectCompatibilityAfterDeletingSubject";

        String schema1String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}]}";
        String schema1 = new AvroSchema(schema1String).canonicalString();

        String schema2String = "{\"type\":\"record\"," + "\"name\":\"myrecord\"," + "\"fields\":" + "[{\"type\":\"string\",\"name\":" + "\"f" + "\"}," + "{\"type\":\"string\",\"name\":" + "\"g\" , \"default\":\"d\"}" + "]}";
        String schema2 = new AvroSchema(schema2String).canonicalString();

        confluentClient.updateCompatibility(CompatibilityLevel.FULL.name, null);
        confluentClient.updateCompatibility(CompatibilityLevel.BACKWARD.name, subject);

        confluentClient.registerSchema(schema1, subject, true);
        confluentClient.registerSchema(schema2, subject, true);

        confluentClient.deleteSubject(RestService.DEFAULT_REQUEST_PROPERTIES, subject);
        try {
            confluentClient.getConfig(subject);
        } catch (RestClientException rce) {
            assertEquals(ErrorCode.SUBJECT_COMPATIBILITY_NOT_CONFIGURED.value(), rce.getErrorCode(), "Compatibility Level doesn't exist");
        }
        assertEquals(FULL.name, confluentClient.getConfig(null).getCompatibilityLevel(), "Top Compatibility Level Exists");

    }
}