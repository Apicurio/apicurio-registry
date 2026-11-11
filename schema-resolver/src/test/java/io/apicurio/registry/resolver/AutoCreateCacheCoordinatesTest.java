package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for #11053: auto-registration results are cached by content, so identical schemas written
 * under different coordinates must still each be registered under their own coordinates.
 */
class AutoCreateCacheCoordinatesTest {

    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"Same\",\"fields\":[]}";

    private static DefaultSchemaResolver<String, String> resolver(MockRegistryClientFacade facade) {
        ParsedSchemaImpl<String> parsed = new ParsedSchemaImpl<>();
        parsed.setParsedSchema(SCHEMA);
        parsed.setRawSchema(SCHEMA.getBytes(StandardCharsets.UTF_8));
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(facade);
        Map<String, Object> configs = new HashMap<>();
        configs.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        resolver.configure(configs, new MockSchemaParser(parsed));
        return resolver;
    }

    private static MockRecord<String> record(String artifactId) {
        return new MockRecord<>("payload", ArtifactReference.builder().groupId("g").artifactId(artifactId)
                .build());
    }

    @Test
    void identicalSchemasUnderDifferentArtifactIdsAreEachRegistered() {
        MockRegistryClientFacade facade = new MockRegistryClientFacade(SCHEMA);
        DefaultSchemaResolver<String, String> resolver = resolver(facade);

        SchemaLookupResult<String> a = resolver.resolveSchema(record("topic-a-value"));
        SchemaLookupResult<String> b = resolver.resolveSchema(record("topic-b-value"));

        assertEquals("topic-a-value", a.getArtifactId());
        assertEquals("topic-b-value", b.getArtifactId());
        assertEquals(List.of("g/topic-a-value", "g/topic-b-value"), new ArrayList<>(facade.getCreated().keySet()));
    }

    @Test
    void sameSchemaUnderSameCoordinatesIsRegisteredOnce() {
        MockRegistryClientFacade facade = new MockRegistryClientFacade(SCHEMA);
        DefaultSchemaResolver<String, String> resolver = resolver(facade);

        resolver.resolveSchema(record("topic-a-value"));
        resolver.resolveSchema(record("topic-a-value"));

        assertEquals(1, facade.getCreateSchemaCallCount());
    }
}
