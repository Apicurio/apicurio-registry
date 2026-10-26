package io.apicurio.registry.avro.content;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for {@link AvroContentAccepter}, in particular that nested references are accepted regardless of the
 * iteration order of the resolved reference map (#9188).
 */
class AvroContentAccepterTest {

    private static final String CITY = "{\"type\":\"record\",\"name\":\"City\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";

    /** Depends on City. */
    private static final String ADDRESS_WITH_CITY = "{\"type\":\"record\",\"name\":\"Address\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"city\",\"type\":\"com.example.City\"}]}";

    /** Depends on Address. */
    private static final String PERSON = "{\"type\":\"record\",\"name\":\"Person\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"address\",\"type\":\"com.example.Address\"}]}";

    private static TypedContent content(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_JSON);
    }

    /**
     * Person -> Address -> City, with Address offered before the City it depends on. A single pass over the
     * map in iteration order fails to parse Address and so rejects valid Avro content.
     */
    @Test
    void acceptsNestedReferencesOfferedBeforeTheirDependencies() {
        Map<String, TypedContent> references = new LinkedHashMap<>();
        references.put("com.example.Address", content(ADDRESS_WITH_CITY));
        references.put("com.example.City", content(CITY));

        assertTrue(new AvroContentAccepter().acceptsContent(content(PERSON), references));
    }

    @Test
    void acceptsNestedReferencesOfferedInDependencyOrder() {
        Map<String, TypedContent> references = new LinkedHashMap<>();
        references.put("com.example.City", content(CITY));
        references.put("com.example.Address", content(ADDRESS_WITH_CITY));

        assertTrue(new AvroContentAccepter().acceptsContent(content(PERSON), references));
    }

    /** Ordering tolerance must not turn a genuinely missing reference into acceptance. */
    @Test
    void rejectsContentWhoseNestedReferenceIsMissing() {
        Map<String, TypedContent> references = Map.of("com.example.Address", content(ADDRESS_WITH_CITY));

        assertFalse(new AvroContentAccepter().acceptsContent(content(PERSON), references));
    }
}
