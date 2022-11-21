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

package io.apicurio.registry.noprofile.ccompat.rest.v6;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.ccompat.dto.SchemaContent;
import io.apicurio.registry.rest.v2.beans.Rule;
import io.apicurio.registry.support.HealthUtils;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.types.RuleType;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.connect.avro.AvroConverter;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
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
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import static io.apicurio.registry.utils.tests.TestUtils.retry;


@QuarkusTest
@SuppressWarnings({"unchecked", "rawtypes"})
public class ConfluentClientTest extends AbstractResourceTestBase {

    public SchemaRegistryClient buildClient() {

        final List<SchemaProvider> schemaProviders = Arrays
                .asList(new JsonSchemaProvider(), new AvroSchemaProvider(), new ProtobufSchemaProvider());

        return new CachedSchemaRegistryClient(new RestService("http://localhost:" + testPort + "/apis/ccompat/v6"), 3, schemaProviders, null, null);
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

        // TODO -- match per schema!
        //int v1 = client.getVersion(subject, schema1);
        //Assertions.assertEquals(1, v1);

        int v2 = client.getVersion(subject, schema2);
        Assertions.assertEquals(2, v2);

        int d1 = client.deleteSchemaVersion(subject, "1");
        Assertions.assertEquals(1, d1);
        int d2 = client.deleteSchemaVersion(subject, "2");
        Assertions.assertEquals(2, d2);
        //int dl = client.deleteSchemaVersion(subject, "latest");
        //Assertions.assertEquals(2, dl);

        // TODO: discuss with Ales: both versions of the schema were deleted above.  should the subject be deleted when all versions are deleted?
//        versions = client.deleteSubject(subject);
        // TODO: why would this work?  deleting the subject would return the already-deleted versions?
//        Assertions.assertTrue(versions.contains(1));
//        Assertions.assertTrue(versions.contains(2));
    }

    // TODO -- cover all endpoints!

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
             KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(client);) {

            GenericData.Record record = new GenericData.Record(new Schema.Parser().parse(rawSchema));
            record.put("bar", "somebar");

            byte[] bytes = serializer.serialize(subject, record);
            GenericData.Record ir = (GenericData.Record) deserializer.deserialize(subject, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testSerdeJsonSchema() throws Exception {

        final SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        final SchemaContent schemaContent = new SchemaContent(
                "{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");

        final Properties config = new Properties();
        config.put(KafkaJsonSchemaSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        config.put(KafkaJsonSchemaSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:" + testPort + "/apis/ccompat/v6");

        try (KafkaJsonSchemaSerializer serializer = new KafkaJsonSchemaSerializer(client, new HashMap(config));
             KafkaJsonSchemaDeserializer deserializer = new KafkaJsonSchemaDeserializer(client, config, SchemaContent.class)) {

            byte[] bytes = serializer.serialize(subject, schemaContent);
            Object deserialized = deserializer.deserialize(subject, bytes);
            Assertions.assertEquals(schemaContent, deserialized);
        }
    }

    @Test
    public void testSerdeProtobufSchema() throws Exception {

        TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

        final SchemaRegistryClient client = buildClient();
        final String subject = generateArtifactId();

        final Map<String, Object> config = new HashMap<>();
        config.put(KafkaProtobufSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        config.put(KafkaProtobufSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:" + testPort + "/apis/ccompat/v6");
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
     * Test for issue: https://github.com/Apicurio/apicurio-registry/issues/536
     *
     * @throws Exception
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
}
