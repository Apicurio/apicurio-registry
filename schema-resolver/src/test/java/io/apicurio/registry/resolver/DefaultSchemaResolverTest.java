package io.apicurio.registry.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.rest.client.RegistryClient;


public class DefaultSchemaResolverTest {
    @Test
    void testCanResolveArtifactByContentHash() {
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        String contentHash = "content hash value";
        String schemaContent = "schema content";
        RegistryClient client = new MockRegistryClient(schemaContent);
        resolver.setClient(client);
        Map<String, String> configs = new HashMap<>();
        SchemaParser<String, String> schemaParser = new MockSchemaParser();
        resolver.configure(configs, schemaParser);            

        ArtifactReference reference = ArtifactReference.builder().contentHash(contentHash).build();
        SchemaLookupResult<String> result = resolver.resolveSchemaByArtifactReference(reference);

        assertEquals(contentHash, result.getContentHash());
        assertEquals(schemaContent, new String(result.getParsedSchema().getRawSchema(), StandardCharsets.UTF_8));
    }

    @Test
    void testCachesArtifactsResolvedByContentHash() {
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        String contentHash = "another content hash value";
        String schemaContent = "more schema content";
        MockRegistryClient client = new MockRegistryClient(schemaContent);
        resolver.setClient(client);
        Map<String, String> configs = new HashMap<>();
        SchemaParser<String, String> schemaParser = new MockSchemaParser();
        resolver.configure(configs, schemaParser);            

        ArtifactReference reference = ArtifactReference.builder().contentHash(contentHash).build();
        SchemaLookupResult<String> result1 = resolver.resolveSchemaByArtifactReference(reference);
        SchemaLookupResult<String> result2 = resolver.resolveSchemaByArtifactReference(reference);

        assertEquals(contentHash, result1.getContentHash());
        assertEquals(schemaContent, new String(result1.getParsedSchema().getRawSchema(), StandardCharsets.UTF_8));
        assertEquals(contentHash, result2.getContentHash());
        assertEquals(schemaContent, new String(result2.getParsedSchema().getRawSchema(), StandardCharsets.UTF_8));
        assertEquals(1, client.timesGetContentByHashCalled);
    }

}
