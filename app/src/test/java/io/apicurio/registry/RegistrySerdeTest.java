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
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.junit.jupiter.api.Assertions;

import com.google.protobuf.Descriptors;
import com.google.protobuf.DynamicMessage;

import io.apicurio.registry.client.RegistryService;
import io.apicurio.registry.rest.beans.ArtifactMetaData;
import io.apicurio.registry.support.TestCmmn;
import io.apicurio.registry.support.Tester;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.utils.ConcurrentUtil;
import io.apicurio.registry.utils.serde.AbstractKafkaSerDe;
import io.apicurio.registry.utils.serde.AbstractKafkaSerializer;
import io.apicurio.registry.utils.serde.AvroKafkaDeserializer;
import io.apicurio.registry.utils.serde.AvroKafkaSerializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaDeserializer;
import io.apicurio.registry.utils.serde.ProtobufKafkaSerializer;
import io.apicurio.registry.utils.serde.avro.AvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.utils.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.utils.serde.strategy.AutoRegisterIdStrategy;
import io.apicurio.registry.utils.serde.strategy.CachedSchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindBySchemaIdStrategy;
import io.apicurio.registry.utils.serde.strategy.FindLatestIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GetOrCreateIdStrategy;
import io.apicurio.registry.utils.serde.strategy.GlobalIdStrategy;
import io.apicurio.registry.utils.serde.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.utils.tests.RegistryServiceTest;
import io.quarkus.test.junit.QuarkusTest;

/**
 * @author Ales Justin
 */
@QuarkusTest
public class RegistrySerdeTest extends AbstractResourceTestBase {

    @RegistryServiceTest
    public void testFindBySchema(Supplier<RegistryService> supplier) throws Exception {
        String artifactId = generateArtifactId();
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        CompletionStage<ArtifactMetaData> csa = supplier.get().createArtifact(ArtifactType.AVRO, artifactId, null, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));
        ArtifactMetaData amd = ConcurrentUtil.result(csa);

        retry(() -> supplier.get().getArtifactMetaDataByGlobalId(amd.getGlobalId()));

        Assertions.assertNotNull(supplier.get().getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        GlobalIdStrategy<Schema> idStrategy = new FindBySchemaIdStrategy<>();
        Assertions.assertEquals(amd.getGlobalId(), idStrategy.findId(supplier.get(), artifactId, ArtifactType.AVRO, schema));
    }

    @RegistryServiceTest
    public void testGetOrCreate(Supplier<RegistryService> supplier) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        String artifactId = generateArtifactId();
        CompletionStage<ArtifactMetaData> csa = supplier.get().createArtifact(ArtifactType.AVRO, artifactId, null, new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8)));
        ArtifactMetaData amd = ConcurrentUtil.result(csa);

        retry(() -> supplier.get().getArtifactMetaDataByGlobalId(amd.getGlobalId()));

        Assertions.assertNotNull(supplier.get().getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        GlobalIdStrategy<Schema> idStrategy = new GetOrCreateIdStrategy<>();
        Assertions.assertEquals(amd.getGlobalId(), idStrategy.findId(supplier.get(), artifactId, ArtifactType.AVRO, schema));

        artifactId = generateArtifactId(); // new
        long id = idStrategy.findId(supplier.get(), artifactId, ArtifactType.AVRO, schema);

        retry(() -> supplier.get().getArtifactMetaDataByGlobalId(id));

        Assertions.assertEquals(id, idStrategy.findId(supplier.get(), artifactId, ArtifactType.AVRO, schema));
    }

    @RegistryServiceTest
    public void testCachedSchema(Supplier<RegistryService> supplier) throws Exception {
        RegistryService service = supplier.get();

        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord5x\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        String artifactId = generateArtifactId();

        GlobalIdStrategy<Schema> idStrategy = new CachedSchemaIdStrategy<>();
        long id = idStrategy.findId(service, artifactId, ArtifactType.AVRO, schema);
        service.reset();

        retry(() -> service.getArtifactMetaDataByGlobalId(id));

        Assertions.assertEquals(id, idStrategy.findId(service, artifactId, ArtifactType.AVRO, schema));
    }

    @SuppressWarnings("unchecked")
    @RegistryServiceTest
    public void testConfiguration(Supplier<RegistryService> supplier) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        String artifactId = generateArtifactId();

        CompletionStage<ArtifactMetaData> csa = supplier.get().createArtifact(
            ArtifactType.AVRO,
            artifactId + "-myrecord3",
            null, 
            new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
        );
        ArtifactMetaData amd = ConcurrentUtil.result(csa);
        // reset any cache
        supplier.get().reset();
        // wait for global id store to populate (in case of Kafka / Streams)
        ArtifactMetaData amdById = retry(() -> supplier.get().getArtifactMetaDataByGlobalId(amd.getGlobalId()));
        Assertions.assertNotNull(amdById);

        GenericData.Record record = new GenericData.Record(schema);
        record.put("bar", "somebar");

        Map<String, Object> config = new HashMap<>();
        config.put(AbstractKafkaSerDe.REGISTRY_URL_CONFIG_PARAM, "http://localhost:8081/api");
        config.put(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, new TopicRecordIdStrategy());
        config.put(AbstractKafkaSerializer.REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM, new FindLatestIdStrategy<>());
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, new DefaultAvroDatumProvider<>());
        Serializer<GenericData.Record> serializer = (Serializer<GenericData.Record>) getClass().getClassLoader()
                                                                                               .loadClass(AvroKafkaSerializer.class.getName())
                                                                                               .newInstance();
        serializer.configure(config, true);
        byte[] bytes = serializer.serialize(artifactId, record);

        Deserializer<GenericData.Record> deserializer = (Deserializer<GenericData.Record>) getClass().getClassLoader()
                                                                                                     .loadClass(AvroKafkaDeserializer.class.getName())
                                                                                                     .newInstance();
        deserializer.configure(config, true);

        record = deserializer.deserialize(artifactId, bytes);
        Assertions.assertEquals("somebar", record.get("bar").toString());

        config.put(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, TopicRecordIdStrategy.class);
        config.put(AbstractKafkaSerializer.REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM, FindLatestIdStrategy.class);
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, DefaultAvroDatumProvider.class);
        serializer.configure(config, true);
        bytes = serializer.serialize(artifactId, record);
        deserializer.configure(config, true);
        record = deserializer.deserialize(artifactId, bytes);
        Assertions.assertEquals("somebar", record.get("bar").toString());

        config.put(AbstractKafkaSerializer.REGISTRY_ARTIFACT_ID_STRATEGY_CONFIG_PARAM, TopicRecordIdStrategy.class.getName());
        config.put(AbstractKafkaSerializer.REGISTRY_GLOBAL_ID_STRATEGY_CONFIG_PARAM, FindLatestIdStrategy.class.getName());
        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, DefaultAvroDatumProvider.class.getName());
        serializer.configure(config, true);
        bytes = serializer.serialize(artifactId, record);
        deserializer.configure(config, true);
        record = deserializer.deserialize(artifactId, bytes);
        Assertions.assertEquals("somebar", record.get("bar").toString());

        serializer.close();
        deserializer.close();
    }

    @RegistryServiceTest
    public void testAvro(Supplier<RegistryService> supplier) throws Exception {
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(supplier.get());
             Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(supplier.get())) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            
            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String subject = generateArtifactId();

            byte[] bytes = serializer.serialize(subject, record);

            // some impl details ...
            waitForSchema(supplier.get(), bytes);

            GenericData.Record ir = deserializer.deserialize(subject, bytes);

            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @RegistryServiceTest
    public void testAvroReflect(Supplier<RegistryService> supplier) throws Exception {
        try (AvroKafkaSerializer<Tester> serializer = new AvroKafkaSerializer<Tester>(supplier.get());
             AvroKafkaDeserializer<Tester> deserializer = new AvroKafkaDeserializer<Tester>(supplier.get())) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
            serializer.setAvroDatumProvider(new ReflectAvroDatumProvider<>());
            deserializer.setAvroDatumProvider(new ReflectAvroDatumProvider<>());
            
            String artifactId = generateArtifactId();

            Tester tester = new Tester("Apicurio");
            byte[] bytes = serializer.serialize(artifactId, tester);

            waitForSchema(supplier.get(), bytes);

            tester = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals("Apicurio", tester.getName());
        }
    }

    @RegistryServiceTest
    public void testProto(Supplier<RegistryService> supplier) throws Exception {
        try (ProtobufKafkaSerializer<TestCmmn.UUID> serializer = new ProtobufKafkaSerializer<TestCmmn.UUID>(supplier.get());
             Deserializer<DynamicMessage> deserializer = new ProtobufKafkaDeserializer(supplier.get())) {

            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());

            TestCmmn.UUID record = TestCmmn.UUID.newBuilder().setLsb(2).setMsb(1).build();

            String subject = generateArtifactId();

            byte[] bytes = serializer.serialize(subject, record);

            waitForSchema(supplier.get(), bytes);

            DynamicMessage dm = deserializer.deserialize(subject, bytes);
            Descriptors.Descriptor descriptor = dm.getDescriptorForType();

            Descriptors.FieldDescriptor lsb = descriptor.findFieldByName("lsb");
            Assertions.assertNotNull(lsb);
            Assertions.assertEquals(2L, dm.getField(lsb));

            Descriptors.FieldDescriptor msb = descriptor.findFieldByName("msb");
            Assertions.assertNotNull(msb);
            Assertions.assertEquals(1L, dm.getField(msb));
        }
    }
}
