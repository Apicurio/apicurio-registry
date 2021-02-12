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

package io.apicurio.tests.converters;

import io.apicurio.tests.ApicurioV2BaseIT;

/**
 * @author Ales Justin
 */
//@Tag(SERDES)
//@Tag(ACCEPTANCE)
public class RegistryConverterIT extends ApicurioV2BaseIT {

    //TODO implement tests for new converters? if any? otherwise implement tests for old converters, but using new api

//    @Test
//    public void testConfiguration() throws Exception {
//        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"myrecord4\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
//
//        String artifactId = generateArtifactId();
//
//        ArtifactMetaData amd = registryClient.createArtifact(artifactId + "-myrecord4", ArtifactType.AVRO,
//            new ByteArrayInputStream(schema.toString().getBytes(StandardCharsets.UTF_8))
//        );
//        // wait for global id store to populate (in case of Kafka / Streams)
//        ArtifactMetaData amdById = retry(() -> registryClient.getArtifactMetaDataByGlobalId(amd.getGlobalId()));
//        Assertions.assertNotNull(amdById);
//
//        GenericData.Record record = new GenericData.Record(schema);
//        record.put("bar", "somebar");
//
//        Map<String, Object> config = new HashMap<>();
//        config.put(SerdeConfig.REGISTRY_URL, RestAssured.baseURI);
//        config.put(SchemalessConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class.getName());
//        config.put(SchemalessConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class.getName());
//        config.put(SerdeConfig.ARTIFACT_ID_STRATEGY, new TopicRecordIdStrategy());
//        config.put(AvroDatumProvider.REGISTRY_AVRO_DATUM_PROVIDER_CONFIG_PARAM, new DefaultAvroDatumProvider<>());
//        SchemalessConverter<GenericData.Record> converter = new SchemalessConverter<>();
//
//        byte[] bytes;
//        try {
//            converter.configure(config, true);
//            bytes = converter.fromConnectData(artifactId, null, record);
//            record = (GenericData.Record) converter.toConnectData(artifactId, bytes).value();
//            Assertions.assertEquals("somebar", record.get("bar").toString());
//        } finally {
//            converter.close();
//        }
//
//        config.put(SchemalessConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, AvroKafkaSerializer.class);
//        config.put(SchemalessConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, AvroKafkaDeserializer.class);
//
//        converter = new SchemalessConverter<>();
//        try {
//            converter.configure(config, true);
//            bytes = converter.fromConnectData(artifactId, null, record);
//            record = (GenericData.Record) converter.toConnectData(artifactId, bytes).value();
//            Assertions.assertEquals("somebar", record.get("bar").toString());
//        } finally {
//            converter.close();
//        }
//
//        config.put(SchemalessConverter.REGISTRY_CONVERTER_SERIALIZER_PARAM, new AvroKafkaSerializer<>());
//        config.put(SchemalessConverter.REGISTRY_CONVERTER_DESERIALIZER_PARAM, new AvroKafkaDeserializer<>());
//
//        converter = new SchemalessConverter<>();
//        try {
//            converter.configure(config, true);
//            bytes = converter.fromConnectData(artifactId, null, record);
//            record = (GenericData.Record) converter.toConnectData(artifactId, bytes).value();
//            Assertions.assertEquals("somebar", record.get("bar").toString());
//        } finally {
//            converter.close();
//        }
//    }
//
//    @Test
//    public void testAvro() throws Exception {
//        try (AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(createRegistryClient());
//             AvroKafkaDeserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(createRegistryClient())) {
//
//            serializer.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
//            AvroData avroData = new AvroData(new AvroDataConfig(Collections.emptyMap()));
//            try (AvroConverter<Record> converter = new AvroConverter<>(serializer, deserializer, avroData)) {
//
//                org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
//                                                                       .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
//                                                                       .build();
//                Struct struct = new Struct(sc);
//                struct.put("bar", "somebar");
//
//                String subject = generateArtifactId();
//
//                byte[] bytes = converter.fromConnectData(subject, sc, struct);
//
//                // some impl details ...
//                waitForSchema(globalId -> registryClient.getArtifactMetaDataByGlobalId(globalId) != null, bytes);
//
//                Struct ir = (Struct) converter.toConnectData(subject, bytes).value();
//                Assertions.assertEquals("somebar", ir.get("bar").toString());
//            }
//        }
//    }
//
//    @Test
//    public void testPrettyJson() throws Exception {
//        testJson(
//            createRegistryClient(),
//            new PrettyFormatStrategy(),
//            input -> {
//                try {
//                    ObjectMapper mapper = new ObjectMapper();
//                    JsonNode root = mapper.readTree(input);
//                    return root.get("schemaId").asLong();
//                } catch (IOException e) {
//                    throw new UncheckedIOException(e);
//                }
//            }
//        );
//    }
//
//    @Test
//    public void testCompactJson() throws Exception {
//        testJson(
//            createRegistryClient(),
//            new CompactFormatStrategy(),
//            input -> {
//                ByteBuffer buffer = AbstractKafkaSerDe.getByteBuffer(input);
//                return buffer.getLong();
//            }
//        );
//    }
//
//    private void testJson(RegistryRestClient restClient, FormatStrategy formatStrategy, Function<byte[], Long> fn) throws Exception {
//        try (ExtJsonConverter converter = new ExtJsonConverter(restClient)) {
//            converter.setGlobalIdStrategy(new AutoRegisterIdStrategy<>());
//            converter.setFormatStrategy(formatStrategy);
//            converter.configure(Collections.emptyMap(), false);
//
//            org.apache.kafka.connect.data.Schema sc = SchemaBuilder.struct()
//                                                                   .field("bar", org.apache.kafka.connect.data.Schema.STRING_SCHEMA)
//                                                                   .build();
//            Struct struct = new Struct(sc);
//            struct.put("bar", "somebar");
//
//            byte[] bytes = converter.fromConnectData("extjson", sc, struct);
//
//            // some impl details ...
//            waitForSchemaCustom(globalId -> restClient.getArtifactMetaDataByGlobalId(globalId) != null, bytes, fn);
//
//            //noinspection rawtypes
//            Map ir = (Map) converter.toConnectData("extjson", bytes).value();
//            Assertions.assertEquals("somebar", ir.get("bar").toString());
//        }
//    }
}
