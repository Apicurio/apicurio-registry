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

@QuarkusTest
public class SerdeMixTest extends AbstractResourceTestBase {

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:8081/confluent", 3);
    }

    @Test
    public void testSerdeMix() throws Exception {
        SchemaRegistryClient client = buildClient();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        client.register("SerdeMixTest-foo-value", schema);

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            AvroKafkaDeserializer<GenericData.Record> deserializer1 = new AvroKafkaDeserializer<GenericData.Record>(service).asConfluent();
            try (KafkaAvroSerializer serializer1 = new KafkaAvroSerializer(client)) {
                byte[] bytes = serializer1.serialize("SerdeMixTest-foo", record);

                waitForSchema(service, bytes, bb -> (long) bb.getInt());

                GenericData.Record ir = deserializer1.deserialize("SerdeMixTest-foo", bytes);
                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }

            AvroKafkaSerializer<GenericData.Record> serializer2 = new AvroKafkaSerializer<GenericData.Record>(service).asConfluent();
            try (KafkaAvroDeserializer deserializer2 = new KafkaAvroDeserializer(client)) {
                byte[] bytes = serializer2.serialize("SerdeMixTest-foo", record);
                GenericData.Record ir = (GenericData.Record) deserializer2.deserialize("SerdeMixTest-foo", bytes);
                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }
        }
    }

}
