package io.apicurio.registry.resolver;

import io.apicurio.registry.resolver.config.SchemaResolverConfig;
import io.apicurio.registry.resolver.data.Record;
import io.apicurio.registry.resolver.strategy.ArtifactReference;
import io.apicurio.registry.resolver.strategy.ArtifactReferenceResolverStrategy;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Serializer and deserializer paths use their own dereference flag (#10060).
 */
class SplitDereferenceResolverTest {

    private static final String SCHEMA = "{\"type\":\"record\",\"name\":\"R\",\"fields\":[]}";

    private MockRegistryClientFacade facade;
    private MockSchemaParser parser;

    private DefaultSchemaResolver<String, String> resolver(Map<String, Object> extra) {
        facade = new MockRegistryClientFacade(SCHEMA);
        ParsedSchemaImpl<String> parsed = new ParsedSchemaImpl<>();
        parsed.setParsedSchema(SCHEMA);
        parsed.setRawSchema(SCHEMA.getBytes(StandardCharsets.UTF_8));
        parser = new MockSchemaParser(parsed);
        DefaultSchemaResolver<String, String> resolver = new DefaultSchemaResolver<>();
        resolver.setClientFacade(facade);
        Map<String, Object> configs = new HashMap<>(extra);
        resolver.configure(configs, parser);
        resolver.setArtifactResolverStrategy(new ArtifactReferenceResolverStrategy<>() {
            @Override
            public ArtifactReference artifactReference(Record<String> data, ParsedSchema<String> ps) {
                return ArtifactReference.builder().groupId("g").artifactId("a").build();
            }

            @Override
            public boolean loadSchema() {
                return false;
            }
        });
        return resolver;
    }

    private static Map<String, Object> sides(Object serializer, Object deserializer) {
        Map<String, Object> map = new HashMap<>();
        map.put(SchemaResolverConfig.SERIALIZER_DEREFERENCE_SCHEMA, serializer);
        map.put(SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA, deserializer);
        return map;
    }

    private static Map<String, Object> autoRegister(Map<String, Object> map) {
        map.put(SchemaResolverConfig.AUTO_REGISTER_ARTIFACT, true);
        return map;
    }

    @Test
    void serializerFlagReachesGetSchemaFromData() {
        resolver(autoRegister(sides(true, false))).resolveSchema(new MockRecord<>("p", null));
        assertEquals(List.of(true), parser.getSchemaFromDataDereference());

        resolver(autoRegister(sides(false, true))).resolveSchema(new MockRecord<>("p", null));
        assertEquals(List.of(false), parser.getSchemaFromDataDereference());
    }

    @Test
    void serializerFlagReachesFindLatestLookup() {
        Map<String, Object> map = sides(true, false);
        map.put(SchemaResolverConfig.FIND_LATEST_ARTIFACT, true);
        resolver(map).resolveSchema(new MockRecord<>("p", null));
        assertEquals(List.of("globalId:true"), facade.getDereferenceRequests());
    }

    @Test
    void deserializerFlagReachesGlobalIdLookup() {
        resolver(sides(true, false)).resolveSchemaByGlobalId(5L);
        assertEquals(List.of("globalId:false"), facade.getDereferenceRequests());

        resolver(sides(false, true)).resolveSchemaByGlobalId(5L);
        assertEquals(List.of("globalId:true"), facade.getDereferenceRequests());
        assertEquals(0, facade.getGetReferencesCallCount());
    }

    @Test
    void deserializerFlagReachesContentLookups() {
        DefaultSchemaResolver<String, String> resolver = resolver(sides(false, true));
        resolver.resolveSchemaByArtifactReference(ArtifactReference.builder().contentId(7L).build());
        resolver.resolveSchemaByArtifactReference(ArtifactReference.builder().contentHash("h").build());
        assertEquals(List.of("contentId:true", "contentHash:true"), facade.getDereferenceRequests());
        assertEquals(0, facade.getGetReferencesCallCount());

        resolver = resolver(sides(true, false));
        resolver.resolveSchemaByArtifactReference(ArtifactReference.builder().contentId(7L).build());
        assertEquals(List.of("contentId:false"), facade.getDereferenceRequests());
        assertEquals(1, facade.getGetReferencesCallCount());
    }

    @Test
    void contentLookupsKeepReferencesWhenTheFacadeCannotDereference() {
        DefaultSchemaResolver<String, String> resolver = resolver(sides(false, true));
        facade.setSupportsDereferencedContentLookups(false);
        resolver.resolveSchemaByArtifactReference(ArtifactReference.builder().contentId(7L).build());
        assertEquals(List.of("contentId:false"), facade.getDereferenceRequests());
        assertEquals(1, facade.getGetReferencesCallCount());
    }

    @Test
    void contentLookupsKeepReferencesWhenTheParserCannotDereference() {
        DefaultSchemaResolver<String, String> resolver = resolver(sides(false, true));
        parser.withSupportsDereferencedContentLookups(false);
        resolver.resolveSchemaByArtifactReference(ArtifactReference.builder().contentId(7L).build());
        assertEquals(List.of("contentId:false"), facade.getDereferenceRequests());
        assertEquals(1, facade.getGetReferencesCallCount());
    }

    @Test
    void globalKeyAloneDrivesBothSides() {
        DefaultSchemaResolver<String, String> resolver = resolver(
                autoRegister(new HashMap<>(Map.of(SchemaResolverConfig.DEREFERENCE_SCHEMA, "true"))));
        resolver.resolveSchema(new MockRecord<>("p", null));
        resolver.resolveSchemaByGlobalId(5L);
        assertEquals(List.of(true), parser.getSchemaFromDataDereference());
        assertEquals(List.of("globalId:true"), facade.getDereferenceRequests());
        assertEquals(true, resolver.resolveDereferenced);
    }

    @Test
    void oneSharedResolverKeepsBothSidesApart() {
        // Kafka Connect shares one resolver between its serializer and deserializer.
        DefaultSchemaResolver<String, String> resolver = resolver(autoRegister(sides(true, false)));
        resolver.resolveSchema(new MockRecord<>("p", null));
        resolver.resolveSchemaByGlobalId(9L);
        assertEquals(List.of(true), parser.getSchemaFromDataDereference());
        assertEquals(List.of("globalId:false"), facade.getDereferenceRequests());
    }
}
