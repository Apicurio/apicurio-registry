package io.apicurio.registry.resolver.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DereferenceConfigTest {

    private static void putIfSet(Map<String, Object> map, String key, String value) {
        if (!"unset".equals(value)) {
            map.put(key, "bool-true".equals(value) ? Boolean.TRUE
                    : "bool-false".equals(value) ? Boolean.FALSE : value);
        }
    }

    @ParameterizedTest(name = "global={0} side={1} -> {2}")
    @CsvSource({
            "unset, unset, false",
            "true, unset, true",
            "false, unset, false",
            "unset, true, true",
            "unset, false, false",
            "true, false, false",
            "false, true, true",
            "bool-true, unset, true",
            "unset, bool-true, true",
            "true, bool-false, false"
    })
    void eachSideFallsBackToTheGlobalKey(String global, String side, boolean expected) {
        Map<String, Object> serializer = new HashMap<>();
        putIfSet(serializer, SchemaResolverConfig.DEREFERENCE_SCHEMA, global);
        putIfSet(serializer, SchemaResolverConfig.SERIALIZER_DEREFERENCE_SCHEMA, side);
        assertEquals(expected, new SchemaResolverConfig(serializer).serializerDereference());

        Map<String, Object> deserializer = new HashMap<>();
        putIfSet(deserializer, SchemaResolverConfig.DEREFERENCE_SCHEMA, global);
        putIfSet(deserializer, SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA, side);
        assertEquals(expected, new SchemaResolverConfig(deserializer).deserializerDereference());
    }

    @ParameterizedTest(name = "serializer={0} deserializer={1}")
    @CsvSource({"true, false", "false, true"})
    void sidesAreIndependent(boolean serializer, boolean deserializer) {
        Map<String, Object> map = new HashMap<>();
        map.put(SchemaResolverConfig.DEREFERENCE_SCHEMA, "true");
        map.put(SchemaResolverConfig.SERIALIZER_DEREFERENCE_SCHEMA, serializer);
        map.put(SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA, deserializer);
        SchemaResolverConfig config = new SchemaResolverConfig(map);
        assertEquals(serializer, config.serializerDereference());
        assertEquals(deserializer, config.deserializerDereference());
        assertEquals(true, config.resolveDereferenced());
    }

    @Test
    void keyNames() {
        assertEquals("apicurio.registry.serializer.dereference-schema",
                SchemaResolverConfig.SERIALIZER_DEREFERENCE_SCHEMA);
        assertEquals("apicurio.registry.deserializer.dereference-schema",
                SchemaResolverConfig.DESERIALIZER_DEREFERENCE_SCHEMA);
    }
}
