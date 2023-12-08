package io.apicurio.registry.resolver;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractSchemaResolverTest {
    @Test
    void testConfigureInitializesSchemaCache() throws Exception {
        Map<String, String> configs = Collections.singletonMap(SchemaResolverConfig.REGISTRY_URL, "http://localhost");

        try (TestAbstractSchemaResolver<Object, Object> resolver = new TestAbstractSchemaResolver<>()) {
            resolver.configure(configs, null);

            assertDoesNotThrow(() -> {resolver.schemaCache.checkInitialized();});
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

    class TestAbstractSchemaResolver<SCHEMA, DATA> extends AbstractSchemaResolver<SCHEMA, DATA> {

        @Override
        public SchemaLookupResult<SCHEMA> resolveSchema(Record<DATA> data) {
            throw new UnsupportedOperationException("Unimplemented method 'resolveSchema'");
        }

        @Override
        public SchemaLookupResult<SCHEMA> resolveSchemaByArtifactReference(ArtifactReference reference) {
            throw new UnsupportedOperationException("Unimplemented method 'resolveSchemaByArtifactReference'");
        }
        
    }
}
