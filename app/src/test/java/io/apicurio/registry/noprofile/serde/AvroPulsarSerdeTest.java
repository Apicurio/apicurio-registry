package io.apicurio.registry.noprofile.serde;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.avro.AvroDeserializer;
import io.apicurio.registry.serde.avro.AvroPulsarSerde;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.AvroSerializer;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class AvroPulsarSerdeTest extends AbstractResourceTestBase {
    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        restClient = new RegistryClient(adapter);
    }

    @Test
    public void testAvro() throws Exception {
        testAvroAutoRegisterIdInBody(RecordIdStrategy.class, () -> {
            return restClient.groups().byGroupId("test_group_avro").artifacts().byArtifactId("myrecord3")
                    .versions().byVersionExpression("branch=latest").get();
        });
    }

    private void testAvroAutoRegisterIdInBody(
            Class<? extends ArtifactReferenceResolverStrategy<?, ?>> strategy,
            Supplier<VersionMetaData> artifactFinder) throws Exception {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"myrecord3\",\"namespace\":\"test_group_avro\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroSerializer<GenericData.Record> serializer = new AvroSerializer<>(restClient);
            AvroDeserializer<GenericData.Record> deserializer = new AvroDeserializer<>(restClient)) {

            AvroPulsarSerde<GenericData.Record> avroPulsarSerde = new AvroPulsarSerde<>(serializer,
                    deserializer, "myrecord3");

            Map<String, Object> config = new HashMap<>();
            config.put(SerdeConfig.ARTIFACT_RESOLVER_STRATEGY, strategy);
            config.put(SerdeConfig.AUTO_REGISTER_ARTIFACT, "true");
            serializer.configure(new AvroSerdeConfig(config), false);

            config = new HashMap<>();
            deserializer.configure(new AvroSerdeConfig(config), false);

            GenericData.Record record = new GenericData.Record(schema);
            record.put("bar", "somebar");

            byte[] bytes = avroPulsarSerde.serialize(record);

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

            GenericData.Record ir = avroPulsarSerde.deserialize(bytes);

            Assertions.assertEquals(record, ir);
            Assertions.assertEquals("somebar", ir.get("bar").toString());
        }
    }
}
