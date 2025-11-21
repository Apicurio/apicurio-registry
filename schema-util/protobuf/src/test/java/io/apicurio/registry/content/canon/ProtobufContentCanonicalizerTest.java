package io.apicurio.registry.content.canon;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for ProtobufContentCanonicalizer.
 *
 * Tests verify that:
 * 1. Protobuf schemas are canonicalized to a consistent text format
 * 2. Semantically equivalent schemas produce the same canonical output
 * 3. Dependencies/references are properly handled during canonicalization
 * 4. Invalid schemas return original content gracefully
 */
class ProtobufContentCanonicalizerTest {

    private ProtobufContentCanonicalizer canonicalizer;

    @BeforeEach
    void setUp() {
        canonicalizer = new ProtobufContentCanonicalizer();
    }

    /**
     * Helper method to create TypedContent from a string.
     */
    private TypedContent toTypedContent(String content) {
        return TypedContent.create(ContentHandle.create(content), ContentTypes.APPLICATION_PROTOBUF);
    }

    @Test
    void testBasicCanonicalization() {
        // Test that a simple protobuf schema is canonicalized
        String schema = """
            syntax = "proto3";

            message Person {
                string name = 1;
                int32 age = 2;
                string email = 3;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        assertNotNull(canonicalized);
        assertNotNull(canonicalized.getContent());
        assertEquals(ContentTypes.APPLICATION_PROTOBUF, canonicalized.getContentType());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        // Verify canonical output contains key elements
        assertTrue(result.contains("message Person"));
        assertTrue(result.contains("string name = 1"));
        assertTrue(result.contains("int32 age = 2"));
        assertTrue(result.contains("string email = 3"));
    }

    @Test
    void testCanonicalizationWithWhitespaceVariations() {
        // Two schemas with different whitespace should canonicalize to the same output
        String schema1 = """
            syntax = "proto3";
            message Person {
                string name = 1;
                int32 age = 2;
            }
            """;

        String schema2 = """
            syntax = "proto3";

            message Person {
              string name = 1;
              int32 age = 2;
            }
            """;

        TypedContent content1 = toTypedContent(schema1);
        TypedContent content2 = toTypedContent(schema2);

        String canonical1 = canonicalizer.canonicalize(content1, Collections.emptyMap()).getContent().content();
        String canonical2 = canonicalizer.canonicalize(content2, Collections.emptyMap()).getContent().content();

        // Both should produce the same canonical output
        assertEquals(canonical1, canonical2, "Schemas with different whitespace should canonicalize identically");
    }

    @Test
    void testSyntax2Canonicalization() {
        // Test proto2 syntax
        // Note: The canonical form may not preserve optional keywords for proto2
        String schema = """
            syntax = "proto2";

            message SearchRequest {
                required string query = 1;
                optional int32 page_number = 2;
                optional int32 results_per_page = 3;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message SearchRequest"));
        assertTrue(result.contains("string query = 1"));
        assertTrue(result.contains("int32 page_number = 2"));
        assertTrue(result.contains("int32 results_per_page = 3"));
    }

    @Test
    void testCanonicalizationWithEnums() {
        String schema = """
            syntax = "proto3";

            enum Status {
                UNKNOWN = 0;
                ACTIVE = 1;
                INACTIVE = 2;
            }

            message User {
                string name = 1;
                Status status = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("enum Status"));
        assertTrue(result.contains("UNKNOWN = 0"));
        assertTrue(result.contains("ACTIVE = 1"));
        assertTrue(result.contains("message User"));
    }

    @Test
    void testCanonicalizationWithNestedMessages() {
        String schema = """
            syntax = "proto3";

            message Outer {
                message Inner {
                    string value = 1;
                }
                Inner inner = 1;
                string name = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message Outer"));
        assertTrue(result.contains("message Inner"));
    }

    @Test
    void testCanonicalizationWithRepeatedFields() {
        String schema = """
            syntax = "proto3";

            message EmailList {
                repeated string emails = 1;
                repeated int32 ids = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("repeated string emails = 1"));
        assertTrue(result.contains("repeated int32 ids = 2"));
    }

    @Test
    void testCanonicalizationWithWellKnownTypes() {
        String schema = """
            syntax = "proto3";

            import "google/protobuf/timestamp.proto";

            message Event {
                string name = 1;
                google.protobuf.Timestamp created_at = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message Event"));
        // The canonical form should preserve the well-known type reference
        assertTrue(result.contains("google.protobuf.Timestamp") || result.contains("Timestamp"));
    }

    @Test
    void testCanonicalizationWithPackage() {
        String schema = """
            syntax = "proto3";
            package com.example.proto;

            message Person {
                string name = 1;
                int32 age = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        // Canonical form should preserve package declaration
        assertTrue(result.contains("package com.example.proto") || result.contains("com.example.proto"));
        assertTrue(result.contains("message Person"));
    }

    @Test
    void testCanonicalizationWithDependencies() {
        // Main schema that imports a dependency
        String mainSchema = """
            syntax = "proto3";

            import "common.proto";

            message User {
                string name = 1;
                Address address = 2;
            }
            """;

        // Dependency schema
        String dependencySchema = """
            syntax = "proto3";

            message Address {
                string street = 1;
                string city = 2;
            }
            """;

        TypedContent mainContent = toTypedContent(mainSchema);
        Map<String, TypedContent> references = new HashMap<>();
        references.put("common.proto", toTypedContent(dependencySchema));

        TypedContent canonicalized = canonicalizer.canonicalize(mainContent, references);

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message User"));
        // The import should be preserved or the dependency should be resolved
    }

    @Test
    void testCanonicalizationWithOptions() {
        String schema = """
            syntax = "proto3";

            option java_package = "com.example.proto";
            option java_outer_classname = "PersonProto";

            message Person {
                string name = 1;
                int32 age = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        // Canonical form should preserve file options
        assertTrue(result.contains("message Person"));
    }

    @Test
    void testInvalidSchemaReturnsOriginalContent() {
        // Invalid protobuf schema (missing field numbers)
        String invalidSchema = """
            syntax = "proto3";

            message InvalidMessage {
                string name;
                int32 age;
            }
            """;

        TypedContent content = toTypedContent(invalidSchema);
        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        // Should return original content when canonicalization fails
        assertNotNull(result);
        assertEquals(invalidSchema, result.getContent().content());
        assertEquals(ContentTypes.APPLICATION_PROTOBUF, result.getContentType());
    }

    @Test
    void testEmptySchemaReturnsOriginalContent() {
        String emptySchema = "";

        TypedContent content = toTypedContent(emptySchema);
        TypedContent result = canonicalizer.canonicalize(content, Collections.emptyMap());

        // Should return original content for empty schema
        assertNotNull(result);
        assertEquals(emptySchema, result.getContent().content());
    }

    @Test
    void testNullReferencesHandledGracefully() {
        String schema = """
            syntax = "proto3";

            message Person {
                string name = 1;
            }
            """;

        TypedContent content = toTypedContent(schema);
        // Pass null references - should be handled as empty map
        TypedContent canonicalized = canonicalizer.canonicalize(content, null);

        assertNotNull(canonicalized);
        String result = canonicalized.getContent().content();
        assertTrue(result.contains("message Person"));
    }

    @Test
    void testCanonicalizationWithServices() {
        String schema = """
            syntax = "proto3";

            message Request {
                string id = 1;
            }

            message Response {
                string result = 1;
            }

            service MyService {
                rpc DoSomething(Request) returns (Response);
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message Request"));
        assertTrue(result.contains("message Response"));
        assertTrue(result.contains("service MyService"));
    }

    @Test
    void testCanonicalizationWithOneOf() {
        // Note: The canonical form may not preserve the oneof structure explicitly
        // but should preserve the fields
        String schema = """
            syntax = "proto3";

            message Payment {
                oneof payment_method {
                    string credit_card = 1;
                    string paypal = 2;
                    string bank_transfer = 3;
                }
                int32 amount = 4;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message Payment"));
        // The canonical form should contain all fields even if oneof structure is flattened
        assertTrue(result.contains("credit_card"));
        assertTrue(result.contains("paypal"));
        assertTrue(result.contains("bank_transfer"));
        assertTrue(result.contains("amount"));
    }

    @Test
    void testCanonicalizationWithMaps() {
        String schema = """
            syntax = "proto3";

            message Config {
                map<string, string> properties = 1;
                map<string, int32> counts = 2;
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("message Config"));
        // Maps might be represented as nested messages in canonical form
        assertTrue(result.contains("properties") || result.contains("map"));
    }

    @Test
    void testCanonicalizationIdempotency() {
        // Canonicalizing an already canonical schema should produce the same output
        String schema = """
            syntax = "proto3";

            message Person {
                string name = 1;
            }
            """;

        TypedContent content = toTypedContent(schema);

        // First canonicalization
        String canonical1 = canonicalizer.canonicalize(content, Collections.emptyMap()).getContent().content();

        // Second canonicalization of the canonical output
        TypedContent canonicalContent = toTypedContent(canonical1);
        String canonical2 = canonicalizer.canonicalize(canonicalContent, Collections.emptyMap()).getContent().content();

        // Should be idempotent
        assertEquals(canonical1, canonical2, "Canonicalization should be idempotent");
    }

    @Test
    void testCanonicalizationPreservesSemantics() {
        // Two different but semantically equivalent schemas
        String schema1 = """
            syntax = "proto3";
            message Person { string name = 1; int32 age = 2; }
            """;

        String schema2 = """
            syntax = "proto3";

            message Person {
              string name = 1;
              int32 age = 2;
            }
            """;

        TypedContent content1 = toTypedContent(schema1);
        TypedContent content2 = toTypedContent(schema2);

        String canonical1 = canonicalizer.canonicalize(content1, Collections.emptyMap()).getContent().content();
        String canonical2 = canonicalizer.canonicalize(content2, Collections.emptyMap()).getContent().content();

        // Should produce identical canonical forms
        assertEquals(canonical1, canonical2, "Semantically equivalent schemas should have identical canonical forms");
    }

    @Test
    void testComplexSchemaWithMultipleFeatures() {
        // Complex schema combining multiple protobuf features
        String schema = """
            syntax = "proto3";
            package com.example;

            option java_package = "com.example.proto";

            import "google/protobuf/timestamp.proto";

            enum OrderStatus {
                UNKNOWN = 0;
                PENDING = 1;
                COMPLETED = 2;
                CANCELLED = 3;
            }

            message Order {
                string id = 1;
                OrderStatus status = 2;
                repeated string items = 3;
                google.protobuf.Timestamp created_at = 4;

                message LineItem {
                    string product_id = 1;
                    int32 quantity = 2;
                    double price = 3;
                }

                repeated LineItem line_items = 5;
                map<string, string> metadata = 6;

                oneof payment {
                    string credit_card = 7;
                    string invoice = 8;
                }
            }
            """;

        TypedContent content = toTypedContent(schema);
        TypedContent canonicalized = canonicalizer.canonicalize(content, Collections.emptyMap());

        String result = canonicalized.getContent().content();
        assertNotNull(result);
        assertTrue(result.contains("enum OrderStatus"));
        assertTrue(result.contains("message Order"));
        assertTrue(result.contains("message LineItem"));
        assertTrue(result.contains("repeated string items"));
        assertTrue(result.contains("oneof payment"));
    }
}
