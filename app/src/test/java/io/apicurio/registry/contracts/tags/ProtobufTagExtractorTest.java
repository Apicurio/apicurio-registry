package io.apicurio.registry.contracts.tags;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;

public class ProtobufTagExtractorTest {

    private ProtobufTagExtractor extractor;

    @BeforeEach
    void setUp() {
        extractor = new ProtobufTagExtractor();
    }

    @Test
    void testGetArtifactType() {
        assertEquals(ArtifactType.PROTOBUF, extractor.getArtifactType());
    }

    @Test
    void testNullContent() {
        Map<String, Set<String>> tags = extractor.extractTags(null);
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    void testEmptyContent() {
        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(""));
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    void testBlankContent() {
        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create("   "));
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    void testSchemaWithoutTags() {
        String schema = """
                syntax = "proto3";
                message User {
                    string name = 1;
                    int32 age = 2;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    void testApicurioFieldMetaTags() {
        String schema = """
                syntax = "proto3";
                message User {
                    string email = 1 [(apicurio.field_meta).tags = "PII,EMAIL"];
                    string ssn = 2 [(apicurio.field_meta).tags = "PII,SENSITIVE"];
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("User.email"));
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("User.ssn"));
    }

    @Test
    void testConfluentFieldMetaTags() {
        String schema = """
                syntax = "proto3";
                message User {
                    string email = 1 [(confluent.field_meta).tags = "PII,EMAIL"];
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("User.email"));
    }

    @Test
    void testSingleTag() {
        String schema = """
                syntax = "proto3";
                message User {
                    string email = 1 [(apicurio.field_meta).tags = "PII"];
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("User.email"));
    }

    @Test
    void testNestedMessages() {
        String schema = """
                syntax = "proto3";
                message User {
                    string email = 1 [(apicurio.field_meta).tags = "PII,EMAIL"];
                    message Address {
                        string street = 1 [(apicurio.field_meta).tags = "PII"];
                        string zip = 2;
                    }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("User.email"));
        assertEquals(Set.of("PII"), tags.get("User.Address.street"));
    }

    @Test
    void testRepeatedFields() {
        String schema = """
                syntax = "proto3";
                message User {
                    repeated string emails = 1 [(apicurio.field_meta).tags = "PII,EMAIL"];
                    repeated int32 scores = 2;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("User.emails"));
    }

    @Test
    void testMapFields() {
        String schema = """
                syntax = "proto3";
                message User {
                    map<string, string> metadata = 1 [(apicurio.field_meta).tags = "SENSITIVE"];
                    map<int32, string> labels = 2;
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("SENSITIVE"), tags.get("User.metadata"));
    }

    @Test
    void testOneOfFields() {
        String schema = """
                syntax = "proto3";
                message User {
                    oneof contact {
                        string email = 1 [(apicurio.field_meta).tags = "PII,EMAIL"];
                        string phone = 2 [(apicurio.field_meta).tags = "PII,PHONE"];
                    }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("User.email"));
        assertEquals(Set.of("PII", "PHONE"), tags.get("User.phone"));
    }

    @Test
    void testDeeplyNestedMessages() {
        String schema = """
                syntax = "proto3";
                message Outer {
                    string id = 1 [(apicurio.field_meta).tags = "IDENTIFIER"];
                    message Middle {
                        string data = 1 [(apicurio.field_meta).tags = "SENSITIVE"];
                        message Inner {
                            string secret = 1 [(apicurio.field_meta).tags = "PII,SENSITIVE"];
                        }
                    }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(3, tags.size());
        assertEquals(Set.of("IDENTIFIER"), tags.get("Outer.id"));
        assertEquals(Set.of("SENSITIVE"), tags.get("Outer.Middle.data"));
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("Outer.Middle.Inner.secret"));
    }

    @Test
    void testMixedTaggedAndUntaggedFields() {
        String schema = """
                syntax = "proto3";
                message User {
                    string name = 1;
                    string email = 2 [(apicurio.field_meta).tags = "PII,EMAIL"];
                    int32 age = 3;
                    string ssn = 4 [(apicurio.field_meta).tags = "PII,SENSITIVE"];
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertTrue(tags.containsKey("User.email"));
        assertTrue(tags.containsKey("User.ssn"));
        assertTrue(tags.get("User.email").contains("PII"));
        assertTrue(tags.get("User.email").contains("EMAIL"));
    }

    @Test
    void testMultipleMessages() {
        String schema = """
                syntax = "proto3";
                message User {
                    string email = 1 [(apicurio.field_meta).tags = "PII,EMAIL"];
                }
                message Order {
                    string credit_card = 1 [(apicurio.field_meta).tags = "FINANCIAL,SENSITIVE"];
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("User.email"));
        assertEquals(Set.of("FINANCIAL", "SENSITIVE"), tags.get("Order.credit_card"));
    }

    @Test
    void testInvalidProtobufReturnsEmpty() {
        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create("not a valid protobuf"));
        assertNotNull(tags);
        assertTrue(tags.isEmpty());
    }

    @Test
    void testEnumNotProcessed() {
        String schema = """
                syntax = "proto3";
                message User {
                    string email = 1 [(apicurio.field_meta).tags = "PII"];
                    enum Status {
                        ACTIVE = 0;
                        INACTIVE = 1;
                    }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("User.email"));
    }

    @Test
    void testPackageWithTags() {
        String schema = """
                syntax = "proto3";
                package com.example;
                message User {
                    string email = 1 [(apicurio.field_meta).tags = "PII"];
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("User.email"));
    }
}
