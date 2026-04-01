package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.client.RegistryArtifactReference;
import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    @Test
    void testResolveReferencesDoesNotDuplicateRecursiveCalls() throws Exception {
        // Set up a schema with one reference that itself has a nested reference.
        // Without the fix, the nested reference would be resolved twice per level.
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade("{\"type\":\"string\"}");

        // nested-ref has no further references (leaf)
        // parent-ref -> nested-ref
        RegistryArtifactReference nestedRef = RegistryArtifactReference.builder()
                .name("nested-ref").groupId("default").artifactId("nested-artifact").version("1").build();
        mockFacade.addReferencesByGAV("default", "parent-artifact", "1", List.of(nestedRef));

        RegistryArtifactReference parentRef = RegistryArtifactReference.builder()
                .name("parent-ref").groupId("default").artifactId("parent-artifact").version("1").build();

        try (TestAbstractSchemaResolver<String, Object> resolver = createResolver(mockFacade)) {
            Map<String, ParsedSchema<String>> result = resolver.resolveReferences(List.of(parentRef));

            // Should contain both the parent and nested reference
            assertEquals(2, result.size());
            assertTrue(result.containsKey("parent-ref"));
            assertTrue(result.containsKey("nested-ref"));

            // Each GAV should be fetched exactly once:
            // parent-artifact (1 call) + nested-artifact (1 call) = 2 calls each
            assertEquals(2, mockFacade.getGetSchemaByGAVCallCount(),
                    "Each unique GAV should be fetched exactly once");
            assertEquals(2, mockFacade.getGetReferencesByGAVCallCount(),
                    "References for each unique GAV should be fetched exactly once");
        }
    }

    @Test
    void testResolveReferencesDeduplicatesSharedReferences() throws Exception {
        // Two top-level references both point to the same nested reference.
        // The shared nested reference should only be fetched once.
        MockRegistryClientFacade mockFacade = new MockRegistryClientFacade("{\"type\":\"string\"}");

        RegistryArtifactReference sharedRef = RegistryArtifactReference.builder()
                .name("shared-ref").groupId("default").artifactId("shared-artifact").version("1").build();

        // Both parent-a and parent-b reference the same shared-ref
        mockFacade.addReferencesByGAV("default", "parent-a", "1", List.of(sharedRef));
        mockFacade.addReferencesByGAV("default", "parent-b", "1", List.of(sharedRef));

        RegistryArtifactReference parentA = RegistryArtifactReference.builder()
                .name("parent-a-ref").groupId("default").artifactId("parent-a").version("1").build();
        RegistryArtifactReference parentB = RegistryArtifactReference.builder()
                .name("parent-b-ref").groupId("default").artifactId("parent-b").version("1").build();

        try (TestAbstractSchemaResolver<String, Object> resolver = createResolver(mockFacade)) {
            Map<String, ParsedSchema<String>> result = resolver.resolveReferences(
                    List.of(parentA, parentB));

            // Should contain parent-a-ref, parent-b-ref, and shared-ref
            assertEquals(3, result.size());
            assertTrue(result.containsKey("parent-a-ref"));
            assertTrue(result.containsKey("parent-b-ref"));
            assertTrue(result.containsKey("shared-ref"));

            // 3 unique GAVs: parent-a, parent-b, shared-artifact
            // shared-artifact should only be fetched once despite being referenced twice
            assertEquals(3, mockFacade.getGetSchemaByGAVCallCount(),
                    "Shared reference should only be fetched once");
            assertEquals(3, mockFacade.getGetReferencesByGAVCallCount(),
                    "References for shared GAV should only be fetched once");
        }
    }

    private TestAbstractSchemaResolver<String, Object> createResolver(MockRegistryClientFacade mockFacade) {
        Map<String, String> configs = Collections.singletonMap(SchemaResolverConfig.REGISTRY_URL,
                "http://localhost");
        SchemaParser<String, Object> parser = new SchemaParser<>() {
            @Override
            public String artifactType() {
                return "JSON";
            }

            @Override
            public String parseSchema(byte[] rawSchema,
                                       Map<String, ParsedSchema<String>> resolvedReferences) {
                return new String(rawSchema);
            }

            @Override
            public ParsedSchema<String> getSchemaFromData(Record<Object> data) {
                return null;
            }

            @Override
            public ParsedSchema<String> getSchemaFromData(Record<Object> data, boolean dereference) {
                return null;
            }
        };
        TestAbstractSchemaResolver<String, Object> resolver = new TestAbstractSchemaResolver<>();
        resolver.setClientFacade(mockFacade);
        resolver.configure(configs, parser);
        return resolver;
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
