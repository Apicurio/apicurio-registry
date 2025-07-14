package io.apicurio.registry.noprofile.serde;

import io.apicurio.registry.AbstractClientFacadeTestBase;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.models.VersionMetaData;
import io.apicurio.registry.serde.avro.AvroDeserializer;
import io.apicurio.registry.serde.avro.AvroPulsarSerde;
import io.apicurio.registry.serde.avro.AvroSerdeConfig;
import io.apicurio.registry.serde.avro.AvroSerializer;
import io.apicurio.registry.serde.avro.strategy.RecordIdStrategy;
import io.apicurio.registry.serde.config.SerdeConfig;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static io.apicurio.registry.utils.tests.TestUtils.waitForSchema;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
public class AvroPulsarSerdeTest extends AbstractClientFacadeTestBase {

    @ParameterizedTest(name = "testAvro [{0}]")
    @MethodSource("isolatedClientFacadeProvider")
    public void testAvro(AbstractClientFacadeTestBase.ClientFacadeSupplier clientFacadeSupplier) throws Exception {
        testAvroAutoRegisterIdInBody(clientFacadeSupplier.getFacade(this), RecordIdStrategy.class, () -> {
            return isolatedClientV3.groups().byGroupId("test_group_avro").artifacts().byArtifactId("myrecord3")
                    .versions().byVersionExpression("branch=latest").get();
        });
    }

    private void testAvroAutoRegisterIdInBody(RegistryClientFacade clientFacade,
                                              Class<? extends ArtifactReferenceResolverStrategy<?, ?>> strategy,
                                              Supplier<VersionMetaData> artifactFinder) throws Exception {
        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"myrecord3\",\"namespace\":\"test_group_avro\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        try (AvroSerializer<GenericData.Record> serializer = new AvroSerializer<>(clientFacade);
            AvroDeserializer<GenericData.Record> deserializer = new AvroDeserializer<>(clientFacade)) {

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
                    if (isolatedClientV3.ids().contentIds().byContentId(contentId.longValue()).get()
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
