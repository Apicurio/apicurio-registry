package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AvroTagExtractorTest {

    private final AvroTagExtractor extractor = new AvroTagExtractor();

    @Test
    void testGetArtifactType() {
        assertEquals(ArtifactType.AVRO, extractor.getArtifactType());
    }

    @Test
    void testSimpleFieldTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {"name": "ssn", "type": "string", "tags": ["PII", "SENSITIVE"]},
                    {"name": "name", "type": "string"}
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("ssn"));
    }

    @Test
    void testNestedRecordTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {
                      "name": "profile",
                      "type": {
                        "type": "record",
                        "name": "Profile",
                        "fields": [
                          {"name": "ssn", "type": "string", "tags": ["PII", "SENSITIVE"]}
                        ]
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("profile.ssn"));
    }

    @Test
    void testArrayItemTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {
                      "name": "emails",
                      "type": {
                        "type": "array",
                        "items": {
                          "type": "record",
                          "name": "Email",
                          "fields": [
                            {"name": "address", "type": "string", "tags": ["PII", "EMAIL"]}
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("emails[].address"));
    }

    @Test
    void testMapValueTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "Data",
                  "fields": [
                    {
                      "name": "metadata",
                      "type": {
                        "type": "map",
                        "values": {
                          "type": "record",
                          "name": "MetaEntry",
                          "fields": [
                            {"name": "secret", "type": "string", "tags": ["CONFIDENTIAL"]}
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("CONFIDENTIAL"), tags.get("metadata.values.secret"));
    }

    @Test
    void testUnionNullableTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {
                      "name": "phone",
                      "type": ["null", "string"],
                      "tags": ["PII"]
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("phone"));
    }

    @Test
    void testUnionWithNestedRecord() {
        String schema = """
                {
                  "type": "record",
                  "name": "Event",
                  "fields": [
                    {
                      "name": "payload",
                      "type": [
                        "null",
                        {
                          "type": "record",
                          "name": "Payload",
                          "fields": [
                            {"name": "data", "type": "string", "tags": ["INTERNAL"]}
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("INTERNAL"), tags.get("payload.data"));
    }

    @Test
    void testSelfReferencingSchema() {
        String schema = """
                {
                  "type": "record",
                  "name": "TreeNode",
                  "fields": [
                    {"name": "value", "type": "string", "tags": ["DATA"]},
                    {"name": "left", "type": ["null", "TreeNode"]},
                    {"name": "right", "type": ["null", "TreeNode"]}
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("DATA"), tags.get("value"));
    }

    @Test
    void testNoTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "Simple",
                  "fields": [
                    {"name": "id", "type": "int"},
                    {"name": "name", "type": "string"}
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testInvalidSchema() {
        Map<String, Set<String>> tags = extractor
                .extractTags(ContentHandle.create("not valid avro"));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testConfluentTags() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {"name": "ssn", "type": "string", "confluent:tags": ["PII"]}
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("ssn"));
    }

    @Test
    void testMergedTagsFromMultipleProperties() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {
                      "name": "ssn",
                      "type": "string",
                      "tags": ["PII"],
                      "confluent:tags": ["SENSITIVE"]
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("ssn"));
    }

    @Test
    void testIssueExample() {
        String schema = """
                {
                  "type": "record",
                  "name": "User",
                  "fields": [
                    {
                      "name": "profile",
                      "type": {
                        "type": "record",
                        "name": "Profile",
                        "fields": [
                          {"name": "ssn", "type": "string", "tags": ["PII", "SENSITIVE"]}
                        ]
                      }
                    },
                    {
                      "name": "emails",
                      "type": {
                        "type": "array",
                        "items": {
                          "type": "record",
                          "name": "Email",
                          "fields": [
                            {"name": "address", "type": "string", "tags": ["PII", "EMAIL"]}
                          ]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("profile.ssn"));
        assertEquals(Set.of("PII", "EMAIL"), tags.get("emails[].address"));
    }

    @Test
    void testDeeplyNestedStructure() {
        String schema = """
                {
                  "type": "record",
                  "name": "Root",
                  "fields": [
                    {
                      "name": "level1",
                      "type": {
                        "type": "record",
                        "name": "Level1",
                        "fields": [
                          {
                            "name": "level2",
                            "type": {
                              "type": "array",
                              "items": {
                                "type": "record",
                                "name": "Level2",
                                "fields": [
                                  {"name": "secret", "type": "string", "tags": ["DEEP"]}
                                ]
                              }
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("DEEP"), tags.get("level1.level2[].secret"));
    }
}
