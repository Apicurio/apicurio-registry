package io.apicurio.registry.noprofile.serde;

import ch.mobi.lead.leadfall.Fall;
import ch.mobi.lead.leadfall.FdtCodeArt;
import ch.mobi.lead.leadfall.LeadFallErstellen;
import ch.mobi.lead.leadfall.Verantwortlichkeit;
import com.kubetrade.schema.trade.AvroSchemaA;
import com.kubetrade.schema.trade.AvroSchemaB;
import com.kubetrade.schema.trade.AvroSchemaC;
import com.kubetrade.schema.trade.AvroSchemaD;
import com.kubetrade.schema.trade.AvroSchemaE;
import com.kubetrade.schema.trade.AvroSchemaF;
import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.avro.AvroKafkaDeserializer;
import io.apicurio.registry.serde.avro.AvroKafkaSerializer;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.DefaultAvroDatumProvider;
import io.apicurio.registry.serde.avro.ReflectAllowNullAvroDatumProvider;
import io.apicurio.registry.serde.avro.ReflectAvroDatumProvider;
import io.apicurio.registry.serde.avro.strategy.QualifiedRecordIdStrategy;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy;
import io.apicurio.registry.serde.config.IdOption;
import io.apicurio.registry.serde.config.KafkaSerdeConfig;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.serde.headers.KafkaSerdeHeaders;
import io.apicurio.registry.support.Tester;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient;
import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serializer;
import org.json.JSONObject;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static io.apicurio.registry.utils.tests.TestUtils.waitForSchemaLongId;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class AvroSerdeTest extends AbstractResourceTestBase {
    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        var adapter = new VertXRequestAdapter(vertx);
        adapter.setBaseUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        restClient = new RegistryClient(adapter);
    }

    @Test
    public void testConfiguration() throws Exception {
        String recordName = "myrecord3";
        Schema schema = new Schema.Parser().parse("{\"type\":\"record\",\"name\":\"" + recordName
                + "\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");

        String groupId = TestUtils.generateGroupId();
        String topic = generateArtifactId();

        /* final Integer globalId = */
        createArtifact(groupId, topic + "-" + recordName, ArtifactType.AVRO, schema.toString(),
                ContentTypes.APPLICATION_JSON);

        Map<String, Object> config = new HashMap<>();
        config.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_GROUP_ID, groupId);
        config.put(SerdeConfig.EXPLICIT_ARTIFACT_VERSION, "1");
        config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
        config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
        Serializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>();
        serializer.configure(config, true);

        Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<GenericData.Record>();

        TestUtils.retry(() -> {

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");
            byte[] bytes = serializer.serialize(topic, record);

            Map<String, Object> deserializerConfig = new HashMap<>();
            deserializerConfig.put(SerdeConfig.REGISTRY_URL, TestUtils.getRegistryV3ApiUrl(testPort));
            deserializer.configure(deserializerConfig, true);

            GenericData.Record deserializedRecord = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals(record, deserializedRecord);
            Assertions.assertEquals("somebar", record.get("bar").toString());

            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
            config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
            serializer.configure(config, true);
            bytes = serializer.serialize(topic, record);

            deserializer.configure(deserializerConfig, true);
            record = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals("somebar", record.get("bar").toString());

            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, TopicRecordIdStrategy.class.getName());
            config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, DefaultAvroDatumProvider.class.getName());
            serializer.configure(config, true);
            bytes = serializer.serialize(topic, record);
            deserializer.configure(deserializerConfig, true);
            record = deserializer.deserialize(topic, bytes);
            Assertions.assertEquals("somebar", record.get("bar").toString());

        });

        serializer.close();
        deserializer.close();
    }

    @Test
    public void testAvro() throws Exception {
        testAvroAutoRegisterIdInBody(RecordIdStrategy.class, () -> {
            return restClient.groups().byGroupId("test_group_avro").artifacts().byArtifactId("myrecord3")
                    .versions().byVersionExpression("branch=latest").get();
        });
    }

    @Test
    public void testAvroQualifiedRecordIdStrategy() throws Exception {
        testAvroAutoRegisterIdInBody(QualifiedRecordIdStrategy.class, () -> {
            return restClient.groups().byGroupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifacts()
                    .byArtifactId("test_group_avro.myrecord3").versions().byVersionExpression("branch=latest")
                    .get();
        });
    }

    private void testAvroAutoRegisterIdInBody(
            Class<? extends ArtifactReferenceResolverStrategy<?, ?>> strategy,
            Supplier<VersionMetaData> artifactFinder) throws Exception {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"myrecord3\",\"namespace\":\"test_group_avro\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (
            AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(
                    restClient);
            Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, strategy);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String topic = generateArtifactId();

            byte[] bytes = serializer.serialize(topic, record);

            // some impl details ...
            waitForSchema(contentId -> {
                try {
                    if (restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0) {
                        VersionMetaData artifactMetadata = artifactFinder.get();
                        assertEquals(contentId.longValue(), artifactMetadata.getContentId());
                        return true;
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                return false;
            }, bytes);

            GenericData.Record ir = deserializer.deserialize(topic, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testAvroJSON() throws Exception {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (
            AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(
                    restClient);
            Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, record);

            // Test msg is stored as json, take 1st 5 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 5, bytes.length)));
            Assertions.assertEquals("somebar", msgAsJson.getString("bar"));

            // some impl details ...
            waitForSchema(contentId -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            GenericData.Record ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void avroJsonWithReferences() throws Exception {
        try (AvroKafkaSerializer<AvroSchemaB> serializer = new AvroKafkaSerializer<AvroSchemaB>(restClient);
            Deserializer<AvroSchemaB> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.DEREFERENCE_SCHEMA, "false");
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.putIfAbsent(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            deserializer.configure(config, false);

            AvroSchemaB avroSchemaB = new AvroSchemaB();
            AvroSchemaA avroSchemaA = AvroSchemaA.GEMINI;
            AvroSchemaA avroSchemaA2 = AvroSchemaA.GEMINI;
            AvroSchemaC avroSchemaC = new AvroSchemaC();
            AvroSchemaD avroSchemaD = new AvroSchemaD();
            AvroSchemaE avroSchemaE = new AvroSchemaE();
            AvroSchemaF avroSchemaF = new AvroSchemaF();

            avroSchemaF.setPayload("Fschema");
            avroSchemaF.setSymbol("Fsymbol");

            avroSchemaE.setPayload("ESchema");
            avroSchemaE.setSymbol("ESymbol");

            avroSchemaD.setSchemaE(avroSchemaE);
            avroSchemaD.setSymbol("Dsymbol");

            avroSchemaC.setSymbol("CSymbol");
            avroSchemaC.setPayload("CSchema");
            avroSchemaC.setSchemaD(avroSchemaD);

            avroSchemaB.setSchemaC(avroSchemaC);
            avroSchemaB.setSchemaA(avroSchemaA);
            avroSchemaB.setSchemaA2(avroSchemaA2);
            avroSchemaB.setKey(UUID.randomUUID().toString());
            avroSchemaB.setUnionTest(avroSchemaF);
            avroSchemaB.setArrayTest(List.of(avroSchemaF));
            avroSchemaB.setMapTest(Map.of("mapKey", avroSchemaF));

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, avroSchemaB);

            // Test msg is stored as json, take 1st 5 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 5, bytes.length)));
            Assertions.assertEquals("CSymbol", msgAsJson.getJSONObject("schemaC").getString("symbol"));

            // some impl details ...
            waitForSchema(contentId -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            AvroSchemaB ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(avroSchemaB, ir);
            Assertions.assertEquals(AvroSchemaA.GEMINI, ir.getSchemaA());
        }
    }

    /**
     * Same test as above but using the dereference configuration to register the schema dereferenced.
     *
     * @throws Exception
     */
    @Test
    public void avroJsonWithReferencesDereferenced() throws Exception {
        try (AvroKafkaSerializer<AvroSchemaB> serializer = new AvroKafkaSerializer<AvroSchemaB>(restClient);
            Deserializer<AvroSchemaB> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.putIfAbsent(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            deserializer.configure(config, false);

            AvroSchemaB avroSchemaB = new AvroSchemaB();
            AvroSchemaA avroSchemaA = AvroSchemaA.GEMINI;
            AvroSchemaA avroSchemaA2 = AvroSchemaA.GEMINI;
            AvroSchemaC avroSchemaC = new AvroSchemaC();
            AvroSchemaD avroSchemaD = new AvroSchemaD();
            AvroSchemaE avroSchemaE = new AvroSchemaE();
            AvroSchemaF avroSchemaF = new AvroSchemaF();

            avroSchemaF.setPayload("Fschema");
            avroSchemaF.setSymbol("Fsymbol");

            avroSchemaE.setPayload("ESchema");
            avroSchemaE.setSymbol("ESymbol");

            avroSchemaD.setSchemaE(avroSchemaE);
            avroSchemaD.setSymbol("Dsymbol");

            avroSchemaC.setSymbol("CSymbol");
            avroSchemaC.setPayload("CSchema");
            avroSchemaC.setSchemaD(avroSchemaD);

            avroSchemaB.setSchemaC(avroSchemaC);
            avroSchemaB.setSchemaA(avroSchemaA);
            avroSchemaB.setSchemaA2(avroSchemaA2);
            avroSchemaB.setKey(UUID.randomUUID().toString());

            avroSchemaB.setUnionTest(avroSchemaF);
            avroSchemaB.setArrayTest(List.of(avroSchemaF));
            avroSchemaB.setMapTest(Map.of("mapKey", avroSchemaF));

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, avroSchemaB);

            // Test msg is stored as json, take 1st 5 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 5, bytes.length)));
            Assertions.assertEquals("CSymbol", msgAsJson.getJSONObject("schemaC").getString("symbol"));

            // some impl details ...
            waitForSchema(contentId -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            AvroSchemaB ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(avroSchemaB, ir);
            Assertions.assertEquals(AvroSchemaA.GEMINI, ir.getSchemaA());
        }
    }

    /**
     * Same test as avroJsonWithReferences but using the dereference configuration for the deserializer only.
     *
     * @throws Exception
     */
    @Test
    public void avroJsonWithReferencesDeserializerDereferenced() throws Exception {
        try (AvroKafkaSerializer<AvroSchemaB> serializer = new AvroKafkaSerializer<AvroSchemaB>(restClient);
            Deserializer<AvroSchemaB> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.putIfAbsent(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            config.putIfAbsent(SerdeConfig.DEREFERENCE_SCHEMA, "true");
            deserializer.configure(config, false);

            AvroSchemaB avroSchemaB = new AvroSchemaB();
            AvroSchemaA avroSchemaA = AvroSchemaA.GEMINI;
            AvroSchemaA avroSchemaA2 = AvroSchemaA.GEMINI;
            AvroSchemaC avroSchemaC = new AvroSchemaC();
            AvroSchemaD avroSchemaD = new AvroSchemaD();
            AvroSchemaE avroSchemaE = new AvroSchemaE();
            AvroSchemaF avroSchemaF = new AvroSchemaF();

            avroSchemaF.setPayload("Fschema");
            avroSchemaF.setSymbol("Fsymbol");

            avroSchemaE.setPayload("ESchema");
            avroSchemaE.setSymbol("ESymbol");

            avroSchemaD.setSchemaE(avroSchemaE);
            avroSchemaD.setSymbol("Dsymbol");

            avroSchemaC.setSymbol("CSymbol");
            avroSchemaC.setPayload("CSchema");
            avroSchemaC.setSchemaD(avroSchemaD);

            avroSchemaB.setSchemaC(avroSchemaC);
            avroSchemaB.setSchemaA(avroSchemaA);
            avroSchemaB.setSchemaA2(avroSchemaA2);
            avroSchemaB.setKey(UUID.randomUUID().toString());

            avroSchemaB.setUnionTest(avroSchemaF);
            avroSchemaB.setArrayTest(List.of(avroSchemaF));
            avroSchemaB.setMapTest(Map.of("mapKey", avroSchemaF));

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, avroSchemaB);

            // Test msg is stored as json, take 1st 5 bytes off (magic byte and long)
            JSONObject msgAsJson = new JSONObject(new String(Arrays.copyOfRange(bytes, 5, bytes.length)));
            Assertions.assertEquals("CSymbol", msgAsJson.getJSONObject("schemaC").getString("symbol"));

            waitForSchema(contentId -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            AvroSchemaB ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(avroSchemaB, ir);
            Assertions.assertEquals(AvroSchemaA.GEMINI, ir.getSchemaA());

            // Create new serializer, the schema already exists in Registry
            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "false");
            serializer.configure(config, false);

            bytes = serializer.serialize(artifactId, avroSchemaB);

            // No need to wait, the schema has been previously registered in Registry
            ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(avroSchemaB, ir);
            Assertions.assertEquals(AvroSchemaA.GEMINI, ir.getSchemaA());
        }
    }

    @Test
    public void issue4463Test() throws Exception {
        try (
            AvroKafkaSerializer<LeadFallErstellen> serializer = new AvroKafkaSerializer<LeadFallErstellen>(
                    restClient);
            Deserializer<LeadFallErstellen> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.put(SerdeConfig.DEREFERENCE_SCHEMA, "true");
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_ENCODING, AvroSerdeConfig.AVRO_ENCODING_JSON);
            config.putIfAbsent(AvroSerdeConfig.AVRO_DATUM_PROVIDER, ReflectAvroDatumProvider.class.getName());
            deserializer.configure(config, false);

            LeadFallErstellen leadFallErstellen = LeadFallErstellen.newBuilder()
                    .setFall(Fall.newBuilder().setVerantwortlichkeitForFall(Verantwortlichkeit.newBuilder()
                            .setBenoetigteStellen(
                                    List.of(FdtCodeArt.newBuilder().setArt(20).setCode(24).build()))
                            .build()).build())
                    .build();

            String artifactId = generateArtifactId();

            byte[] bytes = serializer.serialize(artifactId, leadFallErstellen);

            waitForSchema(id -> {
                try {
                    return restClient.ids().contentIds().byContentId(id.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            LeadFallErstellen ir = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(leadFallErstellen, ir);
        }
    }

    @Test
    public void testAvroUsingHeaders() throws Exception {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (
            AvroKafkaSerializer<GenericData.Record> serializer = new AvroKafkaSerializer<GenericData.Record>(
                    restClient);
            Deserializer<GenericData.Record> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(config, false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            String artifactId = generateArtifactId();
            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, record);

            Assertions.assertNotNull(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_CONTENT_ID));
            headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_CONTENT_ID);

            GenericData.Record ir = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }

    @Test
    public void testReferenceRaw() throws Exception {
        Schema.Parser parser = new Schema.Parser();
        Schema eventTypeSchema = parser.parse("{\n" + "    \"type\": \"enum\",\n"
                + "    \"namespace\": \"test\",\n" + "    \"name\": \"EventType\",\n"
                + "    \"symbols\": [\"CREATED\", \"DELETED\", \"UNDEFINED\", \"UPDATED\"]\n" + "  }\n");

        try (
            AvroKafkaSerializer<GenericData.EnumSymbol> serializer = new AvroKafkaSerializer<GenericData.EnumSymbol>(
                    restClient);
            Deserializer<GenericData.EnumSymbol> deserializer = new AvroKafkaDeserializer<>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(SerdeConfig.DEREFERENCE_SCHEMA, "true");
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, RecordIdStrategy.class.getName());
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(KafkaSerdeConfig.ENABLE_HEADERS, "true");
            deserializer.configure(config, false);

            GenericData.EnumSymbol record = new GenericData.EnumSymbol(eventTypeSchema, "UNDEFINED");

            String artifactId = generateArtifactId();
            Headers headers = new RecordHeaders();
            byte[] bytes = serializer.serialize(artifactId, headers, record);

            Assertions.assertNotNull(headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_CONTENT_ID));
            Header contentId = headers.lastHeader(KafkaSerdeHeaders.HEADER_VALUE_CONTENT_ID);
            long contentIdKey = ByteBuffer.wrap(contentId.value()).getLong();

            waitForSchemaLongId(id -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentIdKey).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes, byteBuffer -> contentIdKey);

            GenericData.EnumSymbol ir = deserializer.deserialize(artifactId, headers, bytes);

            Assertions.assertEquals(record, ir);
        }
    }

    @ParameterizedTest
    @ValueSource(classes = { io.apicurio.registry.serde.strategy.TopicIdStrategy.class,
            io.apicurio.registry.serde.avro.strategy.QualifiedRecordIdStrategy.class,
            io.apicurio.registry.serde.avro.strategy.RecordIdStrategy.class,
            io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy.class })
    public void testAvroReflect(Class<?> artifactResolverStrategyClass) throws Exception {
        testAvroReflect(artifactResolverStrategyClass, ReflectAvroDatumProvider.class,
                () -> new Tester("Apicurio", Tester.TesterState.ONLINE));
    }

    @ParameterizedTest
    @ValueSource(classes = { io.apicurio.registry.serde.strategy.TopicIdStrategy.class,
            io.apicurio.registry.serde.avro.strategy.QualifiedRecordIdStrategy.class,
            io.apicurio.registry.serde.avro.strategy.RecordIdStrategy.class,
            io.apicurio.registry.serde.avro.strategy.TopicRecordIdStrategy.class })
    public void testAvroReflectAllowNull(Class<?> artifactResolverStrategyClass) throws Exception {
        testAvroReflect(artifactResolverStrategyClass, ReflectAllowNullAvroDatumProvider.class,
                () -> new Tester("Apicurio", null));
    }

    private void testAvroReflect(Class<?> artifactResolverStrategyClass, Class<?> datumProvider,
            Supplier<Tester> testerFactory) throws Exception {
        try (AvroKafkaSerializer<Tester> serializer = new AvroKafkaSerializer<Tester>(restClient);
            AvroKafkaDeserializer<Tester> deserializer = new AvroKafkaDeserializer<Tester>(restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, datumProvider.getName());
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, artifactResolverStrategyClass.getName());
            serializer.configure(config, false);

            config = new HashMap<>();
            config.put(AvroSerdeConfig.AVRO_DATUM_PROVIDER, datumProvider.getName());
            deserializer.configure(config, false);

            String artifactId = generateArtifactId();

            Tester tester = testerFactory.get();
            byte[] bytes = serializer.serialize(artifactId, tester);

            waitForSchema(contentId -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes);

            Tester deserializedTester = deserializer.deserialize(artifactId, bytes);

            Assertions.assertEquals(tester, deserializedTester);
            Assertions.assertEquals("Apicurio", deserializedTester.getName());
        }
    }

    private SchemaRegistryClient buildClient() {
        return new CachedSchemaRegistryClient("http://localhost:" + testPort + "/apis/ccompat/v7", 3);
    }

    @Test
    public void testSerdeMix() throws Exception {
        SchemaRegistryClient schemaClient = buildClient();

        String subject = generateArtifactId();

        String rawSchema = "{\"type\":\"record\",\"name\":\"myrecord5\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}";
        ParsedSchema schema = new AvroSchema(rawSchema);
        schemaClient.register(subject + "-value", schema);

        GenericData.Record record = new GenericData.Record(new Schema.Parser().parse(rawSchema));
        record.put("bar", "somebar");

        try (KafkaAvroSerializer serializer1 = new KafkaAvroSerializer(schemaClient);
            AvroKafkaDeserializer<GenericData.Record> deserializer1 = new AvroKafkaDeserializer<GenericData.Record>(
                    restClient)) {
            byte[] bytes = serializer1.serialize(subject, record);

            TestUtils.retry(() -> TestUtils.waitForSchema(contentId -> {
                try {
                    return restClient.ids().contentIds().byContentId(contentId.longValue()).get()
                            .readAllBytes().length > 0;
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }, bytes, ByteBuffer::getInt));

            deserializer1.as4ByteId();
            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.USE_ID, IdOption.contentId.name());
            deserializer1.configure(config, false);
            GenericData.Record ir = deserializer1.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }

        try (KafkaAvroDeserializer deserializer2 = new KafkaAvroDeserializer(schemaClient);
            AvroKafkaSerializer<GenericData.Record> serializer2 = new AvroKafkaSerializer<GenericData.Record>(
                    restClient)) {

            Map<String, String> config = new HashMap<>();
            config.put(SerdeConfig.USE_ID, IdOption.contentId.name());

            serializer2.as4ByteId();
            serializer2.configure(config, false);
            byte[] bytes = serializer2.serialize(subject, record);

            GenericData.Record ir = (GenericData.Record) deserializer2.deserialize(subject, bytes);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }
}
