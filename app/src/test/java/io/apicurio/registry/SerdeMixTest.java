/*
 * Copyright 2020 Red Hat
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

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
public class SerdeMixTest extends AbstractResourceTestBase {

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:8081/api/ccompat", 3);
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @Test
    public void testVersions() throws Exception {
        SchemaRegistryClient confClient = buildClient();

        String subject = generateArtifactId();

        ParsedSchema schema = new AvroSchema("{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        int id = confClient.register(subject, schema);
        confClient.reset();

        this.waitForArtifact(subject);

        this.waitForGlobalId(id);
        ParsedSchema schema2 = confClient.getSchemaById(id);
        Assertions.assertNotNull(schema2);

        ArtifactMetaData amd1 = client.updateArtifact(subject, ArtifactType.AVRO, new ByteArrayInputStream(IoUtil.toBytes(schema.toString())));
        this.waitForGlobalId(amd1.getGlobalId());

        client.getArtifactMetaDataByGlobalId(amd1.getGlobalId());

        ArtifactMetaData amd2 = client.updateArtifact(subject, ArtifactType.AVRO, new ByteArrayInputStream(IoUtil.toBytes(schema.toString())));
        this.waitForGlobalId(amd2.getGlobalId());

        client.getArtifactMetaDataByGlobalId(amd2.getGlobalId());

        List<Integer> versions1 = confClient.getAllVersions(subject);
        Assertions.assertEquals(3, versions1.size());
        Assertions.assertTrue(versions1.contains(1));
        Assertions.assertTrue(versions1.contains(2));
        Assertions.assertTrue(versions1.contains(3));

        List<Long> versions2 = client.listArtifactVersions(subject);
        Assertions.assertEquals(3, versions2.size());
        Assertions.assertTrue(versions2.contains(1L));
        Assertions.assertTrue(versions2.contains(2L));
        Assertions.assertTrue(versions2.contains(3L));

        confClient.deleteSchemaVersion(subject, "1");

        TestUtils.retry(() -> {
            try {
                client.getArtifactVersionMetaData(subject, 1);
                Assertions.fail("Expected client.getArtifactVersionMetaData() to fail");
            } catch (Exception ignored) {
            }
            return null;
        });

        versions1 = confClient.getAllVersions(subject);
        Assertions.assertEquals(2, versions1.size());
        Assertions.assertFalse(versions1.contains(1));
        Assertions.assertTrue(versions1.contains(2));
        Assertions.assertTrue(versions1.contains(3));

        versions2 = client.listArtifactVersions(subject);
        Assertions.assertEquals(2, versions2.size());
        Assertions.assertFalse(versions2.contains(1L));
        Assertions.assertTrue(versions2.contains(2L));
        Assertions.assertTrue(versions2.contains(3L));
    }

    @Disabled("Doesn't work with H2 test env after code change for Spanner")
    @SuppressWarnings("resource")
    @Test
    public void testSerdeMix() throws Exception {
        SchemaRegistryClient schemaClient = buildClient();

        String subject = generateArtifactId();

        String rawSchema = "{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ParsedSchema schema = new AvroSchema(rawSchema);
        schemaClient.register(subject + "-value", schema);

        GenericData.Record record = new GenericData.Record(new Schema.Parser().parse(rawSchema));
        record.put("bar", "somebar");

        AvroKafkaDeserializer<GenericData.Record> deserializer1 = new AvroKafkaDeserializer<GenericData.Record>(client);
        deserializer1.asLegacyId();
        try (KafkaAvroSerializer serializer1 = new KafkaAvroSerializer(schemaClient)) {
            byte[] bytes = serializer1.serialize(subject, record);

            TestUtils.waitForSchema(client, bytes, bb -> (long) bb.getInt());

            GenericData.Record ir = deserializer1.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }

        AvroKafkaSerializer<GenericData.Record> serializer2 = new AvroKafkaSerializer<GenericData.Record>(client);
        serializer2.asLegacyId();
        try (KafkaAvroDeserializer deserializer2 = new KafkaAvroDeserializer(schemaClient)) {
            byte[] bytes = serializer2.serialize(subject, record);
            GenericData.Record ir = (GenericData.Record) deserializer2.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

}
