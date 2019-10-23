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

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;
import io.apicurio.registry.client.RegistryClient;
import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaDeserializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindBySchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.IdStrategy;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistrySerdeTest extends AbstractResourceTestBase {

    @Test
    public void testFindBySchema() throws Exception {
        String artifactId = UUID.randomUUID().toString();
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (RegistryService service = RegistryClient.cached("http://localhost:8081")) {
            CompletionStage<ArtifactMetaData> csa = service.createArtifact(ArtifactType.AVRO, artifactId, new ByteArrayInputStream(schema.toString().getBytes()));
            ArtifactMetaData amd = ConcurrentUtil.result(csa);
            IdStrategy<Schema> idStrategy = new FindBySchemaIdStrategy<>();
            Assertions.assertEquals(amd.getGlobalId(), idStrategy.findId(service, artifactId, ArtifactType.AVRO, schema));
            Assertions.assertNotNull(service.getArtifactMetaData(amd.getGlobalId()));
        }
    }

    @Test
    public void testAvro() throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (RegistryService service = RegistryClient.cached("http://localhost:8081")) {
            try (Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(service).setIdStrategy(new AutoRegisterIdStrategy<>());
                 Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(service)) {

                GenericData.Record record = new GenericData.Record(schema);
                record.put("bar", "somebar");

                byte[] bytes = serializer.serialize("foo", record);
                GenericData.Record ir = deserializer.deserialize("foo", bytes);

                Assertions.assertEquals("somebar", ir.get("bar").toString());
            }
        }
    }

    @Test
    @Disabled("proto is not really schema registry friendly ...")
    public void testProto() throws Exception {
        try (RegistryService service = RegistryClient.create("http://localhost:8081")) {
            try (Serializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<TestCmmn.UUID>(service).setIdStrategy(new AutoRegisterIdStrategy<>());
                 Deserializer<DynamicMessage> deserializer = new ProtobufKafkaDeserializer(service)) {

                TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

                byte[] bytes = serializer.serialize("foo", record);
                DynamicMessage dm = deserializer.deserialize("foo", bytes);
                Descriptors.Descriptor descriptor = TestCmmn.UUID.getDescriptor();

                Descriptors.FieldDescriptor lsb = descriptor.findFieldByName("lsb");
                Assertions.assertNotNull(lsb);
                Assertions.assertEquals(2, dm.getField(lsb));

                Descriptors.FieldDescriptor msb = descriptor.findFieldByName("msb");
                Assertions.assertNotNull(msb);
                Assertions.assertEquals(1, dm.getField(msb));
            }

        }
    }
}