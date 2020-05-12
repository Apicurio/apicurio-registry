/*
 * Copyright 2019 Red Hat
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

package io.apicurio.registry;

import io.apicurio.registry.support.HealthUtils;
import io.confluent.connect.avro.AvroConverter;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.exceptions.RestClientException;
import io.confluent.kafka.serializers.AbstractKafkaAvroSerDeConfig;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.connect.data.SchemaAndValue;
import org.apache.kafka.connect.data.Struct;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static io.apicurio.registry.utils.tests.TestUtils.retry;

import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

@QuarkusTest
public class ConfluentClientTest extends AbstractResourceTestBase {

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:8081/api/ccompat", 3);
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

        Schema schema1 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord1\",\"fields\":[{\"name\":\"f1\",\"type\":\"string\"}]}");
        int id1 = client.register(subject, schema1);

        Schema schema2 = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord2\",\"fields\":[{\"name\":\"f2\",\"type\":\"string\"}]}");
        int id2 = client.register(subject, schema2);

        Schema schema = client.getById(id1);
        Assertions.assertNotNull(schema);

        client.reset();

        Assertions.assertTrue(client.testCompatibility(subject, schema2));

        // global id can be mapped async
        retry(() -> {
            Schema schema3 = client.getById(id2);
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
    public void testSerde() throws Exception {
        SchemaRegistryClient client = buildClient();

        String subject = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        int id = client.register(subject + "-value", schema);
        client.reset();

        // global id can be mapped async
        retry(() -> {
            Schema schema2 = client.getById(id);
            Assertions.assertNotNull(schema2);
            return schema2;
        });

        try (KafkaAvroSerializer serializer = new KafkaAvroSerializer(client);
             KafkaAvroDeserializer deserializer = new KafkaAvroDeserializer(client);) {

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            byte[] bytes = serializer.serialize(subject, record);
            GenericData.Record ir = (GenericData.Record) deserializer.deserialize(subject, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
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
            HealthUtils.assertIsReady();
            HealthUtils.assertIsLive();
        });

        String subject = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        int id = client.register(subject, schema);
        client.reset();

        // global id can be mapped async
        retry(() -> {
            Schema schema2 = client.getById(id);
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
                    Schema schema = new Schema.Parser().parse(String.format("{\"type\":\"record\",\"name\":\"%s\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}],\"connect.name\":\"%s\"}", name, name));
                    int id = client.register(subject + "-value", schema);
                    client.reset();
                    // can be async ...
                    Schema retry = retry(() -> client.getById(id));
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
                    Schema retry = retry(() -> {
                        ByteBuffer buffer = ByteBuffer.wrap(bytes);
                        buffer.get(); // magic-byte
                        int id = buffer.getInt();
                        return client.getById(id);
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
        config.put(AbstractKafkaAvroSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, "dummy");
        config.put(AbstractKafkaAvroSerDeConfig.AUTO_REGISTER_SCHEMAS, autoRegister);
        converter.configure(config, false);

        byte[] bytes = converter.fromConnectData(subject, cs, struct);

        post.accept(client, bytes);

        SchemaAndValue sav = converter.toConnectData(subject, bytes);
        Struct ir = (Struct) sav.value();
        Assertions.assertEquals("somebar", ir.get("bar").toString());
    }
}
