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

import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.IoUtil;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.concurrent.CompletionStage;

@QuarkusTest
public class SerdeMixTest extends AbstractResourceTestBase {

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:8081/ccompat", 3);
    }

    @Test
    public void testVersions() throws Exception {
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

        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            CompletionStage<ArtifactMetaData> cs = service.updateArtifact(subject, ArtifactType.AVRO, new ByteArrayInputStream(IoUtil.toBytes(schema.toString())));
            ArtifactMetaData amd = ConcurrentUtil.result(cs);

            retry(() -> {
                service.getArtifactMetaDataByGlobalId(amd.getGlobalId());
                return null;
            });

            List<Integer> versions1 = client.getAllVersions(subject);
            Assertions.assertEquals(2, versions1.size());
            Assertions.assertTrue(versions1.contains(1));
            Assertions.assertTrue(versions1.contains(2));

            List<Long> versions2 = service.listArtifactVersions(subject);
            Assertions.assertEquals(2, versions2.size());
            Assertions.assertTrue(versions2.contains(1L));
            Assertions.assertTrue(versions2.contains(2L));

            client.deleteSchemaVersion(subject, "1");

            retry(() -> {
                try {
                    service.getArtifactVersionMetaData(1, subject);
                    Assertions.fail();
                } catch (Exception ignored) {
                }
                return null;
            });

            versions1 = client.getAllVersions(subject);
            Assertions.assertEquals(1, versions1.size());
            Assertions.assertFalse(versions1.contains(1));
            Assertions.assertTrue(versions1.contains(2));

            versions2 = service.listArtifactVersions(subject);
            Assertions.assertEquals(1, versions2.size());
            Assertions.assertFalse(versions2.contains(1L));
            Assertions.assertTrue(versions2.contains(2L));
        }
    }

    @Test
    public void testSerdeMix() throws Exception {
        SchemaRegistryClient client = buildClient();

        String subject = generateArtifactId();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        client.register(subject + "-value", schema);

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            AvroKafkaDeserializer<GenericData.Record> deserializer1 = new AvroKafkaDeserializer<GenericData.Record>(service).asConfluent();
            try (KafkaAvroSerializer serializer1 = new KafkaAvroSerializer(client)) {
                byte[] bytes = serializer1.serialize(subject, record);

                waitForSchema(service, bytes, bb -> (long) bb.getInt());

                GenericData.Record ir = deserializer1.deserialize(subject, bytes);
                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }

            AvroKafkaSerializer<GenericData.Record> serializer2 = new AvroKafkaSerializer<GenericData.Record>(service).asConfluent();
            try (KafkaAvroDeserializer deserializer2 = new KafkaAvroDeserializer(client)) {
                byte[] bytes = serializer2.serialize(subject, record);
                GenericData.Record ir = (GenericData.Record) deserializer2.deserialize(subject, bytes);
                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }
        }
    }

}
