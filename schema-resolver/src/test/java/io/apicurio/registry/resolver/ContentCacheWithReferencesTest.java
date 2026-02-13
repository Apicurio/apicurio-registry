package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests for content-based schema caching with references.
 *
 * This test class verifies the fix for a caching bug where the content cache key
 * lookup included references but the stored key did not. This caused cache misses
 * on every lookup, resulting in excessive API calls to the registry.
 *
 * Issue: When using auto-register with the same schema content multiple times,
 * the cache was not being hit because the lookup key (with references) didn't
 * match the stored key (without references).
 *
 * Fix: The content key extractor in AbstractSchemaResolver now includes references
 * from SchemaLookupResult, ensuring key consistency between lookup and storage.
 *
 * @see <a href="https://github.com/Apicurio/apicurio-registry/issues/7395">GitHub Issue #7395</a>
 */
public class ContentCacheWithReferencesTest {

    private static final String TEST_SCHEMA = "{\"type\": \"record\", \"name\": \"Test\", \"fields\": []}";

    /**
     * Test that when auto-register is enabled and the same schema is resolved
     * multiple times, the createSchema API is only called once.
     *
     * This is the core regression test for the caching bug. Before the fix,
     * each call to resolveSchema would result in a createSchema API call
     * because the cache lookup always missed.
     */
    @Test
    void testContentCachingWithAutoRegister() {
        // Setup
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(TEST_SCHEMA);
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(mockFacade);

        // Create a parsed schema that will be returned from getSchemaFromData
        ParsedSchemaImpl<String> parsedSchema = new ParsedSchemaImpl<>();
        parsedSchema.setParsedSchema(TEST_SCHEMA);
        parsedSchema.setRawSchema(TEST_SCHEMA.getBytes(StandardCharsets.UTF_8));

        MockSchemaParser schemaParser = new MockSchemaParser(parsedSchema);

        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        configs.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, false);
        resolver.configure(configs, schemaParser);

        // Create test records with artifact reference
        ArtifactReference reference = ArtifactReference.builder()
                .groupId("default")
                .artifactId("test-artifact")
                .build();

        MockRecord<String> record1 = new MockRecord<>("payload1", reference);
        MockRecord<String> record2 = new MockRecord<>("payload2", reference);
        MockRecord<String> record3 = new MockRecord<>("payload3", reference);

        // Resolve the schema multiple times with the same content
        SchemaLookupResult<String> result1 = resolver.resolveSchema(record1);
        SchemaLookupResult<String> result2 = resolver.resolveSchema(record2);
        SchemaLookupResult<String> result3 = resolver.resolveSchema(record3);

        // Verify that the createSchema API was only called once
        // Before the fix, this would be 3 (once per resolveSchema call)
        assertEquals(1, mockFacade.getCreateSchemaCallCount(),
                "createSchema should only be called once - subsequent calls should use the cache");

        // Verify all results are valid
        assertEquals("test-artifact", result1.getArtifactId());
        assertEquals("test-artifact", result2.getArtifactId());
        assertEquals("test-artifact", result3.getArtifactId());
    }

    /**
     * Test that bulk operations (simulating Debezium CDC snapshot with many records)
     * only result in a single API call for schema registration.
     *
     * This simulates the real-world scenario where Debezium processes 1000+ records
     * during initial snapshot. Before the fix, this would result in 1000+ API calls.
     */
    @Test
    void testBulkOperationsCaching() {
        // Setup
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(TEST_SCHEMA);
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(mockFacade);

        ParsedSchemaImpl<String> parsedSchema = new ParsedSchemaImpl<>();
        parsedSchema.setParsedSchema(TEST_SCHEMA);
        parsedSchema.setRawSchema(TEST_SCHEMA.getBytes(StandardCharsets.UTF_8));

        MockSchemaParser schemaParser = new MockSchemaParser(parsedSchema);

        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        configs.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, false);
        resolver.configure(configs, schemaParser);

        ArtifactReference reference = ArtifactReference.builder()
                .groupId("cdc-group")
                .artifactId("cdc-table-value")
                .build();

        // Simulate bulk CDC operations (like Debezium initial snapshot)
        int numberOfRecords = 100;
        for (int i = 0; i < numberOfRecords; i++) {
            MockRecord<String> record = new MockRecord<>("payload-" + i, reference);
            resolver.resolveSchema(record);
        }

        // Verify that the createSchema API was only called once
        // Before the fix, this would be 100 (once per record)
        assertEquals(1, mockFacade.getCreateSchemaCallCount(),
                "createSchema should only be called once for " + numberOfRecords +
                " records with the same schema - cache should handle subsequent lookups");
    }

    /**
     * Test that different schema content results in separate cache entries.
     * This ensures the fix doesn't break the fundamental caching behavior
     * where different schemas should be cached separately.
     */
    @Test
    void testDifferentSchemaContentNotCached() {
        // Setup
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(TEST_SCHEMA);
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(mockFacade);

        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        configs.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, false);

        // First schema
        String schema1Content = "{\"type\": \"record\", \"name\": \"Schema1\", \"fields\": []}";
        ParsedSchemaImpl<String> parsedSchema1 = new ParsedSchemaImpl<>();
        parsedSchema1.setParsedSchema(schema1Content);
        parsedSchema1.setRawSchema(schema1Content.getBytes(StandardCharsets.UTF_8));
        MockSchemaParser schemaParser1 = new MockSchemaParser(parsedSchema1);
        resolver.configure(configs, schemaParser1);

        ArtifactReference ref1 = ArtifactReference.builder()
                .groupId("default")
                .artifactId("artifact-1")
                .build();

        MockRecord<String> record1 = new MockRecord<>("payload1", ref1);
        resolver.resolveSchema(record1);

        assertEquals(1, mockFacade.getCreateSchemaCallCount(),
                "First schema should trigger one createSchema call");

        // Reset and configure with different schema
        resolver.reset();
        String schema2Content = "{\"type\": \"record\", \"name\": \"Schema2\", \"fields\": []}";
        ParsedSchemaImpl<String> parsedSchema2 = new ParsedSchemaImpl<>();
        parsedSchema2.setParsedSchema(schema2Content);
        parsedSchema2.setRawSchema(schema2Content.getBytes(StandardCharsets.UTF_8));
        MockSchemaParser schemaParser2 = new MockSchemaParser(parsedSchema2);
        resolver.configure(configs, schemaParser2);

        ArtifactReference ref2 = ArtifactReference.builder()
                .groupId("default")
                .artifactId("artifact-2")
                .build();

        MockRecord<String> record2 = new MockRecord<>("payload2", ref2);
        resolver.resolveSchema(record2);

        assertEquals(2, mockFacade.getCreateSchemaCallCount(),
                "Different schema content should trigger another createSchema call");
    }

    /**
     * Test that cache is properly cleared when reset() is called.
     */
    @Test
    void testCacheResetBehavior() {
        // Setup
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(TEST_SCHEMA);
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(mockFacade);

        ParsedSchemaImpl<String> parsedSchema = new ParsedSchemaImpl<>();
        parsedSchema.setParsedSchema(TEST_SCHEMA);
        parsedSchema.setRawSchema(TEST_SCHEMA.getBytes(StandardCharsets.UTF_8));

        MockSchemaParser schemaParser = new MockSchemaParser(parsedSchema);

        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        configs.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, false);
        resolver.configure(configs, schemaParser);

        ArtifactReference reference = ArtifactReference.builder()
                .groupId("default")
                .artifactId("test-artifact")
                .build();

        // First resolution
        MockRecord<String> record1 = new MockRecord<>("payload1", reference);
        resolver.resolveSchema(record1);
        assertEquals(1, mockFacade.getCreateSchemaCallCount());

        // Second resolution - should use cache
        MockRecord<String> record2 = new MockRecord<>("payload2", reference);
        resolver.resolveSchema(record2);
        assertEquals(1, mockFacade.getCreateSchemaCallCount(),
                "Second call should use cache");

        // Reset the resolver
        resolver.reset();

        // Third resolution after reset - should call API again
        MockRecord<String> record3 = new MockRecord<>("payload3", reference);
        resolver.resolveSchema(record3);
        assertEquals(2, mockFacade.getCreateSchemaCallCount(),
                "After reset, createSchema should be called again");
    }
}
