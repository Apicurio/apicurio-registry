package io.apicurio.registry.resolver;

import com.microsoft.kiota.RequestAdapter;
import io.apicurio.registry.resolver.client.RegistryClientFacadeImpl;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import io.apicurio.registry.rest.client.RegistryClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DefaultSchemaResolverTest {

    /**
     * Verifies that resolveSchema does not throw NPE when Record.metadata() returns null.
     * This is a regression test for https://github.com/Apicurio/apicurio-registry/issues/7471
     */
    @Test
    void testResolveSchemaWithNullMetadata() {
        String schemaContent = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}";
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(schemaContent);
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(mockFacade);

        ParsedSchemaImpl<String> parsedSchema = new ParsedSchemaImpl<>();
        parsedSchema.setParsedSchema(schemaContent);
        parsedSchema.setRawSchema(schemaContent.getBytes(StandardCharsets.UTF_8));

        MockSchemaParser schemaParser = new MockSchemaParser(parsedSchema);

        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        configs.put(SchemaResolverConfig.EXPLICIT_ARTIFACT_GROUP_ID, "default");
        configs.put(SchemaResolverConfig.EXPLICIT_ARTIFACT_ID, "test-artifact");
        resolver.configure(configs, schemaParser);

        // Use a strategy that doesn't access metadata, since the default
        // DynamicArtifactReferenceResolverStrategy also calls data.metadata()
        resolver.setArtifactResolverStrategy(new ArtifactReferenceResolverStrategy<>() {
            @Override
            public ArtifactReference artifactReference(Record<String> data, ParsedSchema<String> ps) {
                return ArtifactReference.builder()
                        .groupId("default")
                        .artifactId("test-artifact")
                        .build();
            }

            @Override
            public boolean loadSchema() {
                return false;
            }
        });

        // Create a record with null metadata — this previously caused NPE
        Record<String> record = new Record<>() {
            @Override
            public io.apicurio.registry.resolver.data.Metadata metadata() {
                return null;
            }

            @Override
            public String payload() {
                return "test-payload";
            }
        };

        SchemaLookupResult<String> result = resolver.resolveSchema(record);
        assertNotNull(result);
        assertEquals("test-artifact", result.getArtifactId());
    }

    @Test
    void testCanResolveArtifactByContentHash() {
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        String contentHash = "content hash value";
        String schemaContent = "schema content";
        RequestAdapter mockRequestAdapter = new MockRequestAdapter(schemaContent);
        RegistryClient client = new RegistryClient(mockRequestAdapter);
        resolver.setClientFacade(new RegistryClientFacadeImpl(client));
        Map<String, String> configs = new HashMap<>();
        SchemaParser<String, String> schemaParser = new MockSchemaParser();
        resolver.configure(configs, schemaParser);

        ArtifactReference reference = ArtifactReference.builder().contentHash(contentHash).build();
        SchemaLookupResult<String> result = resolver.resolveSchemaByArtifactReference(reference);

        assertEquals(contentHash, result.getContentHash());
        assertEquals(schemaContent,
                new String(result.getParsedSchema().getRawSchema(), StandardCharsets.UTF_8));
    }

    @Test
    void testCachesArtifactsResolvedByContentHash() {
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        String contentHash = "another content hash value";
        String schemaContent = "more schema content";
        MockRequestAdapter mockAdapter = new MockRequestAdapter(schemaContent);
        RegistryClient client = new RegistryClient(mockAdapter);
        resolver.setClientFacade(new RegistryClientFacadeImpl(client));
        Map<String, String> configs = new HashMap<>();
        SchemaParser<String, String> schemaParser = new MockSchemaParser();
        resolver.configure(configs, schemaParser);

        ArtifactReference reference = ArtifactReference.builder().contentHash(contentHash).build();
        SchemaLookupResult<String> result1 = resolver.resolveSchemaByArtifactReference(reference);
        SchemaLookupResult<String> result2 = resolver.resolveSchemaByArtifactReference(reference);

        assertEquals(contentHash, result1.getContentHash());
        assertEquals(schemaContent,
                new String(result1.getParsedSchema().getRawSchema(), StandardCharsets.UTF_8));
        assertEquals(contentHash, result2.getContentHash());
        assertEquals(schemaContent,
                new String(result2.getParsedSchema().getRawSchema(), StandardCharsets.UTF_8));
        assertEquals(1, mockAdapter.timesGetContentByHashCalled);
    }

}
