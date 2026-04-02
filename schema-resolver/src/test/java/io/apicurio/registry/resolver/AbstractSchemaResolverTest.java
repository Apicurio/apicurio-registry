package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.client.RegistryClientFacade;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractSchemaResolverTest {
    @Test
    void testConfigureInitializesSchemaCache() throws Exception {
        Map<String, String> configs = Collections.singletonMap(SchemaResolverConfig.REGISTRY_URL,
                "http://localhost");

        try (TestAbstractSchemaResolver<Object, Object> resolver = new TestAbstractSchemaResolver<>()) {
            resolver.configure(configs, null);

            assertDoesNotThrow(() -> {
                resolver.schemaCache.checkInitialized();
            });
        }
    }

    @Test
    void testSupportsFailureTolerantSchemaCache() throws Exception {
        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost");
        configs.put(SchemaResolverConfig.FAULT_TOLERANT_REFRESH, true);

        try (TestAbstractSchemaResolver<Object, Object> resolver = new TestAbstractSchemaResolver<>()) {
            resolver.configure(configs, null);

            assertTrue(resolver.schemaCache.isFaultTolerantRefresh());
        }
    }

    @Test
    void testDefaultsToFailureTolerantSchemaCacheDisabled() throws Exception {
        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost");

        try (TestAbstractSchemaResolver<Object, Object> resolver = new TestAbstractSchemaResolver<>()) {
            resolver.configure(configs, null);

            assertFalse(resolver.schemaCache.isFaultTolerantRefresh());
        }
    }

    @Test
    void testDefaultsToCacheLatestEnabled() throws Exception {
        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.REGISTRY_URL, "http://localhost");

        try (TestAbstractSchemaResolver<Object, Object> resolver = new TestAbstractSchemaResolver<>()) {
            resolver.configure(configs, null);

            assertTrue(resolver.schemaCache.isCacheLatest());
        }
    }

    /**
     * Verifies that when two independent calls to resolveReferences() both reference
     * the same GAV (Schema C), the registry client is only called once for that GAV.
     */
    @Test
    void testResolveReferencesCrossInvocationDeduplication() {
        String schemaC = "{\"type\": \"record\", \"name\": \"SchemaC\", \"fields\": []}";
        AtomicInteger getSchemaByGAVCount = new AtomicInteger(0);

        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(schemaC) {
            @Override
            public String getSchemaByGAV(String groupId, String artifactId, String version) {
                getSchemaByGAVCount.incrementAndGet();
                return super.getSchemaByGAV(groupId, artifactId, version);
            }
        };

        TestAbstractSchemaResolver<String, String> resolver = new TestAbstractSchemaResolver<>();
        resolver.setClientFacade(mockFacade);
        resolver.schemaParser = new MockSchemaParser();

        // Schema A references Schema C
        RegistryArtifactReference refCFromA = RegistryArtifactReference.builder()
                .name("schema-c.avsc")
                .groupId("default")
                .artifactId("schema-c")
                .version("1")
                .build();

        // Schema B also references Schema C (same GAV)
        RegistryArtifactReference refCFromB = RegistryArtifactReference.builder()
                .name("schema-c.avsc")
                .groupId("default")
                .artifactId("schema-c")
                .version("1")
                .build();

        // First resolution (Schema A's references)
        Map<String, ParsedSchema<String>> result1 = resolver.resolveReferences(List.of(refCFromA));
        assertNotNull(result1.get("schema-c.avsc"));
        assertEquals(1, getSchemaByGAVCount.get(), "First resolution should fetch from registry");

        // Second resolution (Schema B's references) — should hit the cache
        Map<String, ParsedSchema<String>> result2 = resolver.resolveReferences(List.of(refCFromB));
        assertNotNull(result2.get("schema-c.avsc"));
        assertEquals(1, getSchemaByGAVCount.get(),
                "Second resolution of the same GAV should use the reference cache, not fetch again");
    }

    /**
     * Verifies that the duplicate recursive call bug is fixed: when a reference has
     * nested references, resolveReferences() should not call itself twice for the
     * same nested reference list.
     */
    @Test
    void testResolveReferencesNoDuplicateRecursiveCall() {
        String schemaContent = "{\"type\": \"string\"}";
        AtomicInteger getSchemaByGAVCount = new AtomicInteger(0);

        RegistryArtifactReference nestedRef = RegistryArtifactReference.builder()
                .name("nested.avsc")
                .groupId("default")
                .artifactId("nested-schema")
                .version("1")
                .build();

        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(schemaContent) {
            @Override
            public String getSchemaByGAV(String groupId, String artifactId, String version) {
                getSchemaByGAVCount.incrementAndGet();
                return super.getSchemaByGAV(groupId, artifactId, version);
            }

            @Override
            public List<RegistryArtifactReference> getReferencesByGAV(String groupId, String artifactId,
                    String version) {
                if ("parent-schema".equals(artifactId)) {
                    return List.of(nestedRef);
                }
                return List.of();
            }
        };

        TestAbstractSchemaResolver<String, String> resolver = new TestAbstractSchemaResolver<>();
        resolver.setClientFacade(mockFacade);
        resolver.schemaParser = new MockSchemaParser();

        RegistryArtifactReference parentRef = RegistryArtifactReference.builder()
                .name("parent.avsc")
                .groupId("default")
                .artifactId("parent-schema")
                .version("1")
                .build();

        Map<String, ParsedSchema<String>> result = resolver.resolveReferences(List.of(parentRef));

        assertNotNull(result.get("parent.avsc"));
        assertNotNull(result.get("nested.avsc"));
        // 1 call for parent + 1 call for nested = 2 total (not 3, which would indicate a duplicate call)
        assertEquals(2, getSchemaByGAVCount.get(),
                "Should fetch parent and nested schema exactly once each, no duplicate recursive calls");
    }

    /**
     * Verifies that reset() clears the reference cache, so subsequent calls
     * re-fetch from the registry.
     */
    @Test
    void testResetClearsReferenceCache() {
        String schemaContent = "{\"type\": \"string\"}";
        AtomicInteger getSchemaByGAVCount = new AtomicInteger(0);

        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade(schemaContent) {
            @Override
            public String getSchemaByGAV(String groupId, String artifactId, String version) {
                getSchemaByGAVCount.incrementAndGet();
                return super.getSchemaByGAV(groupId, artifactId, version);
            }
        };

        TestAbstractSchemaResolver<String, String> resolver = new TestAbstractSchemaResolver<>();
        resolver.setClientFacade(mockFacade);
        resolver.schemaParser = new MockSchemaParser();

        RegistryArtifactReference ref = RegistryArtifactReference.builder()
                .name("schema.avsc")
                .groupId("default")
                .artifactId("my-schema")
                .version("1")
                .build();

        // First call — fetches from registry
        resolver.resolveReferences(List.of(ref));
        assertEquals(1, getSchemaByGAVCount.get());

        // Second call — uses cache
        resolver.resolveReferences(List.of(ref));
        assertEquals(1, getSchemaByGAVCount.get(), "Should use cache before reset");

        // Reset clears the cache
        resolver.reset();

        // Third call — should fetch again
        resolver.resolveReferences(List.of(ref));
        assertEquals(2, getSchemaByGAVCount.get(), "Should re-fetch from registry after reset");
    }

    class TestAbstractSchemaResolver<SCHEMA, DATA> extends AbstractSchemaResolver<SCHEMA, DATA> {

        @Override
        public SchemaLookupResult<SCHEMA> resolveSchema(Record<DATA> data) {
            throw new UnsupportedOperationException("Unimplemented method 'resolveSchema'");
        }

        @Override
        public SchemaLookupResult<SCHEMA> resolveSchemaByArtifactReference(ArtifactReference reference) {
            throw new UnsupportedOperationException(
                    "Unimplemented method 'resolveSchemaByArtifactReference'");
        }

    }
}
