package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify that ObjectMapper doesn't serialize null fields.
 * This is critical for MCP protocol compliance - the MCP TypeScript SDK
 * expects fields like _meta to be either omitted or objects, never null.
 */
@QuarkusTest
public class ObjectMapperNullSerializationTest {

    @Inject
    ObjectMapper mapper;

    @Test
    public void testNullFieldsAreNotSerialized() throws Exception {
        // Create a test object with null fields
        TestObject obj = new TestObject();
        obj.setName("test");
        obj.setDescription(null);  // This should NOT appear in JSON
        obj.setMeta(null);          // This should NOT appear in JSON

        // Serialize to JSON
        String json = mapper.writeValueAsString(obj);

        // Verify null fields are omitted
        assertFalse(json.contains("\"description\""),
            "JSON should not contain 'description' field when it's null");
        assertFalse(json.contains("\"_meta\""),
            "JSON should not contain '_meta' field when it's null");
        assertFalse(json.contains("null"),
            "JSON should not contain any null values");

        // Verify non-null fields are present
        assertTrue(json.contains("\"name\""),
            "JSON should contain 'name' field");
        assertTrue(json.contains("\"test\""),
            "JSON should contain the value 'test'");
    }

    @Test
    public void testNestedNullFieldsAreNotSerialized() throws Exception {
        // Create nested object with null fields
        TestObject outer = new TestObject();
        outer.setName("outer");

        TestObject inner = new TestObject();
        inner.setName("inner");
        inner.setDescription(null);  // Should not appear

        outer.setNested(inner);

        // Serialize to JSON
        String json = mapper.writeValueAsString(outer);

        // Verify nested null fields are omitted
        assertFalse(json.contains("\"description\""),
            "Nested null fields should be omitted");
        assertTrue(json.contains("\"name\":\"inner\""),
            "Nested non-null fields should be present");
    }

    /**
     * Test object to verify serialization behavior.
     * Intentionally uses _meta field name to match MCP protocol.
     */
    public static class TestObject {
        private String name;
        private String description;
        private Object _meta;
        private TestObject nested;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public Object get_meta() {
            return _meta;
        }

        public void setMeta(Object meta) {
            this._meta = meta;
        }

        public TestObject getNested() {
            return nested;
        }

        public void setNested(TestObject nested) {
            this.nested = nested;
        }
    }
}
