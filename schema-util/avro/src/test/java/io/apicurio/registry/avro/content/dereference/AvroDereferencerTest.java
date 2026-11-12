package io.apicurio.registry.avro.content.dereference;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression tests for #9188: dereferencing must not depend on the iteration order of the resolved
 * reference map when references depend on each other.
 */
class AvroDereferencerTest {

    private static final ObjectMapper MAPPER = new JsonMapper();

    private static final String C = "{\"type\":\"enum\",\"name\":\"C\",\"namespace\":\"com.example\","
            + "\"symbols\":[\"X\",\"Y\"]}";

    /** Depends on C. */
    private static final String B = "{\"type\":\"record\",\"name\":\"B\",\"namespace\":\"com.example\","
            + "\"fields\":[{\"name\":\"c\",\"type\":\"com.example.C\"}]}";

    /** Depends on B. */
    private static final String A = "{\"type\":\"record\",\"name\":\"A\",\"namespace\":\"com.example\","
            + "\"fields\":[{\"name\":\"b\",\"type\":\"com.example.B\"}]}";

    private static TypedContent content(String schema) {
        return TypedContent.create(ContentHandle.create(schema), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void dereferencesNestedReferencesOfferedBeforeTheirDependencies() throws Exception {
        Map<String, TypedContent> references = new LinkedHashMap<>();
        references.put("com.example.B", content(B));
        references.put("com.example.C", content(C));

        JsonNode result = MAPPER.readTree(
                new AvroDereferencer().dereference(content(A), references).getContent().content());

        JsonNode inlinedB = result.get("fields").get(0).get("type");
        assertEquals("B", inlinedB.get("name").asString());
        JsonNode inlinedC = inlinedB.get("fields").get(0).get("type");
        assertEquals("enum", inlinedC.get("type").asString());
        assertEquals("C", inlinedC.get("name").asString());
        assertEquals("[\"X\",\"Y\"]", inlinedC.get("symbols").toString());
    }
}
