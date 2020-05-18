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

import static io.apicurio.registry.utils.tests.TestUtils.retry;
import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Assertions;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
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

    @RegistryServiceTest
    public void testVersions(Supplier<RegistryService> supplier) throws Exception {
        SchemaRegistryClient client = buildClient();

        String subject = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        int id = client.register(subject, schema);
        client.reset();

        // global id can be mapped async
        retry(() -> {
            Schema schema2 = client.getById(id);
            Assertions.assertNotNull(schema2);
            return schema2;
        });

        CompletionStage<ArtifactMetaData> cs = supplier.get().updateArtifact(subject, ArtifactType.AVRO, new ByteArrayInputStream(IoUtil.toBytes(schema.toString())));
        ArtifactMetaData amd1 = ConcurrentUtil.result(cs);

        retry(() -> {
            supplier.get().getArtifactMetaDataByGlobalId(amd1.getGlobalId());
            return null;
        });

        cs = supplier.get().updateArtifact(subject, ArtifactType.AVRO, new ByteArrayInputStream(IoUtil.toBytes(schema.toString())));
        ArtifactMetaData amd2 = ConcurrentUtil.result(cs);

        retry(() -> {
            supplier.get().getArtifactMetaDataByGlobalId(amd2.getGlobalId());
            return null;
        });

        List<Integer> versions1 = client.getAllVersions(subject);
        Assertions.assertEquals(3, versions1.size());
        Assertions.assertTrue(versions1.contains(1));
        Assertions.assertTrue(versions1.contains(2));
        Assertions.assertTrue(versions1.contains(3));

        List<Long> versions2 = supplier.get().listArtifactVersions(subject);
        Assertions.assertEquals(3, versions2.size());
        Assertions.assertTrue(versions2.contains(1L));
        Assertions.assertTrue(versions2.contains(2L));
        Assertions.assertTrue(versions2.contains(3L));

        client.deleteSchemaVersion(subject, "1");

        retry(() -> {
            try {
                supplier.get().getArtifactVersionMetaData(1, subject);
                Assertions.fail();
            } catch (Exception ignored) {
            }
            return null;
        });

        versions1 = client.getAllVersions(subject);
        Assertions.assertEquals(2, versions1.size());
        Assertions.assertFalse(versions1.contains(1));
        Assertions.assertTrue(versions1.contains(2));
        Assertions.assertTrue(versions1.contains(3));

        versions2 = supplier.get().listArtifactVersions(subject);
        Assertions.assertEquals(2, versions2.size());
        Assertions.assertFalse(versions2.contains(1L));
        Assertions.assertTrue(versions2.contains(2L));
        Assertions.assertTrue(versions2.contains(3L));
    }

    @SuppressWarnings("resource")
    @RegistryServiceTest
    public void testSerdeMix(Supplier<RegistryService> supplier) throws Exception {
        SchemaRegistryClient client = buildClient();

        String subject = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        client.register(subject + "-value", schema);

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        AvroKafkaDeserializer<GenericData.Record> deserializer1 = new AvroKafkaDeserializer<GenericData.Record>(supplier.get());
        deserializer1.asLegacyId();
        try (KafkaAvroSerializer serializer1 = new KafkaAvroSerializer(client)) {
            byte[] bytes = serializer1.serialize(subject, record);

            waitForSchema(supplier.get(), bytes, bb -> (long) bb.getInt());

            GenericData.Record ir = deserializer1.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }

        AvroKafkaSerializer<GenericData.Record> serializer2 = new AvroKafkaSerializer<GenericData.Record>(supplier.get());
        serializer2.asLegacyId();
        try (KafkaAvroDeserializer deserializer2 = new KafkaAvroDeserializer(client)) {
            byte[] bytes = serializer2.serialize(subject, record);
            GenericData.Record ir = (GenericData.Record) deserializer2.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

}
