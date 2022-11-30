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

import io.apicurio.registry.noprofile.ccompat.rest.v6.ConfluentClientTest;
import io.apicurio.registry.support.TestCmmn;
import io.confluent.kafka.schemaregistry.SchemaProvider;
import io.confluent.kafka.schemaregistry.avro.AvroSchemaProvider;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.rest.RestService;
import io.confluent.kafka.schemaregistry.json.JsonSchemaProvider;
import io.confluent.kafka.schemaregistry.protobuf.ProtobufSchemaProvider;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufDeserializerConfig;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializer;
import io.confluent.kafka.serializers.protobuf.KafkaProtobufSerializerConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import javax.enterprise.inject.Typed;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@QuarkusTest
@Typed(ConfluentClientV7Test.class)
public class ConfluentClientV7Test extends ConfluentClientTest {

    @Override
    public SchemaRegistryClient buildClient() {
        final List<SchemaProvider> schemaProviders = Arrays
                .asList(new JsonSchemaProvider(), new AvroSchemaProvider(), new ProtobufSchemaProvider());
        return new CachedSchemaRegistryClient(new RestService("http://localhost:" + testPort + "/apis/ccompat/v7"), 3, schemaProviders, null, null);
    }

    @Test
    @Override
    public void testSerdeProtobufSchema() throws Exception {
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
}
