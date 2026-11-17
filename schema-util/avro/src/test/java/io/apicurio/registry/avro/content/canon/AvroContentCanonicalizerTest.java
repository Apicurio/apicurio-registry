package io.apicurio.registry.avro.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the parser-based fallback in {@link AvroContentCanonicalizer}, reached when the registry's plain
 * Jackson mapper cannot read content that the Avro parser accepts (Avro allows JSON comments).
 */
class AvroContentCanonicalizerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String CITY = "{\"type\":\"record\",\"name\":\"City\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"name\",\"type\":\"string\"}]}";

    /** Depends on City. */
    private static final String ADDRESS_WITH_CITY = "{\"type\":\"record\",\"name\":\"Address\","
            + "\"namespace\":\"com.example\",\"fields\":[{\"name\":\"city\",\"type\":\"com.example.City\"}]}";

    /** Depends on Address. The comment is valid for Avro but not for a default ObjectMapper. */
    private static final String PERSON_WITH_COMMENT = "// person schema\n"
            + "{\"type\":\"record\",\"name\":\"Person\",\"namespace\":\"com.example\","
            + "\"fields\":[{\"name\":\"address\",\"type\":\"com.example.Address\"}]}";

    private static TypedContent content(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_JSON);
    }

    /**
     * Address is offered before the City it depends on (#9188). A single pass over the references in map
     * order fails to parse Address, so the fallback throws instead of canonicalizing.
     */
    @Test
    void fallbackCanonicalizesNestedReferencesOfferedBeforeTheirDependencies() throws Exception {
        Map<String, TypedContent> references = new LinkedHashMap<>();
        references.put("com.example.Address", content(ADDRESS_WITH_CITY));
        references.put("com.example.City", content(CITY));

        JsonNode result = MAPPER.readTree(new AvroContentCanonicalizer()
                .canonicalize(content(PERSON_WITH_COMMENT), references).getContent().content());

        assertEquals("com.example.Person", result.get("namespace").asText() + "." + result.get("name").asText());
        // Referenced types stay as name references rather than being inlined. Avro writes the short name
        // because Address shares Person's namespace.
        JsonNode addressType = result.get("fields").get(0).get("type");
        assertTrue(addressType.isTextual(), "expected a name reference, got: " + addressType);
        assertEquals("Address", addressType.asText());
    }
}
