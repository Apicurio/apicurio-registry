package io.apicurio.registry.noprofile.resolver;

import io.apicurio.registry.AbstractResourceTestBase;
import io.apicurio.registry.client.auth.VertXAuthFactory;
import io.apicurio.registry.model.GroupId;
import io.apicurio.registry.resolver.*;
import io.apicurio.registry.resolver.data.Metadata;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;
import io.apicurio.registry.types.ArtifactType;
import io.apicurio.registry.types.ContentTypes;
import io.apicurio.registry.utils.tests.TestUtils;
import io.kiota.http.vertx.VertXRequestAdapter;
import io.quarkus.test.junit.QuarkusTest;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericRecord;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

@QuarkusTest
public class SchemaResolverTest extends AbstractResourceTestBase {

    private RegistryClient restClient;

    @BeforeEach
    public void createIsolatedClient() {
        var adapter = new VertXRequestAdapter(VertXAuthFactory.defaultVertx);
        adapter.setBaseUrl(TestUtils.getRegistryV3ApiUrl(testPort));
        restClient = new RegistryClient(adapter);
    }

    @Test
    public void testDynamicStrategy() throws Exception {

        SchemaResolver<Schema, GenericRecord> resolver = new DefaultSchemaResolver<>();
        resolver.setClient(restClient);
        Map<String, Object> config = new HashMap<>();
        config.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, true);
        resolver.configure(config, new SchemaParser<Schema, GenericRecord>() {

            @Override
            public Schema parseSchema(byte[] rawSchema,
                    Map<String, ParsedSchema<Schema>> resolvedReferences) {
                return null;
            }

            @Override
            public ParsedSchema<Schema> getSchemaFromData(Record<GenericRecord> data) {
                return null;
            }

            @Override
            public ParsedSchema<Schema> getSchemaFromData(Record<GenericRecord> data, boolean dereference) {
                return null;
            }

            /**
             * @see io.apicurio.registry.resolver.SchemaParser#supportsExtractSchemaFromData()
             */
            @Override
            public boolean supportsExtractSchemaFromData() {
                return false;
            }

            @Override
            public String artifactType() {
                return ArtifactType.AVRO;
            }
        });

        Schema schema = new Schema.Parser().parse(
                "{\"type\":\"record\",\"name\":\"myrecord3\",\"fields\":[{\"name\":\"bar\",\"type\":\"string\"}]}");
        String artifactId = TestUtils.generateArtifactId();
        createArtifact(artifactId, ArtifactType.AVRO, schema.toString(), ContentTypes.APPLICATION_JSON);

        GenericRecord avroRecord = new GenericData.Record(schema);
        avroRecord.put("bar", "somebar");
        Record<GenericRecord> record = new CustomResolverRecord(avroRecord, ArtifactReference.builder()
                .groupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString()).artifactId(artifactId).build());
        var lookup = resolver.resolveSchema(record);

        assertNull(lookup.getGroupId());
        assertEquals(artifactId, lookup.getArtifactId());
        assertEquals(schema.toString(), new String(lookup.getParsedSchema().getRawSchema()));
        assertNull(lookup.getParsedSchema().getParsedSchema());

        var runtimeException = Assertions.assertThrows(RuntimeException.class,
                () -> resolver.resolveSchema(new CustomResolverRecord(avroRecord,
                        ArtifactReference.builder().groupId(GroupId.DEFAULT.getRawGroupIdWithDefaultString())
                                .artifactId("foo").build())));
        io.apicurio.registry.rest.client.models.ProblemDetails error = (io.apicurio.registry.rest.client.models.ProblemDetails) runtimeException
                .getCause();
        assertEquals("VersionNotFoundException", error.getName());
        assertEquals(404, error.getStatus());

        resolver.close();
    }

    private static class CustomResolverRecord implements Record<GenericRecord> {

        private GenericRecord payload;
        private ArtifactReference reference;

        public CustomResolverRecord(GenericRecord payload, ArtifactReference reference) {
            this.payload = payload;
            this.reference = reference;
        }

        /**
         * @see io.apicurio.registry.resolver.data.Record#metadata()
         */
        @Override
        public Metadata metadata() {
            return new Metadata() {

                @Override
                public ArtifactReference artifactReference() {
                    return reference;
                }
            };
        }

        /**
         * @see io.apicurio.registry.resolver.data.Record#payload()
         */
        @Override
        public GenericRecord payload() {
            return payload;
        }

    }

}
