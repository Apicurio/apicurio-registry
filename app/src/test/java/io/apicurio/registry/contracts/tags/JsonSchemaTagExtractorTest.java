package io.apicurio.registry.contracts.tags;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.types.ArtifactType;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonSchemaTagExtractorTest {

    private final JsonSchemaTagExtractor extractor = new JsonSchemaTagExtractor();

    @Test
    void testGetArtifactType() {
        assertEquals(ArtifactType.JSON, extractor.getArtifactType());
    }

    @Test
    void testSimpleFieldTags() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ssn": {
                      "type": "string",
                      "x-tags": ["PII", "SENSITIVE"]
                    },
                    "name": {
                      "type": "string"
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("ssn"));
    }

    @Test
    void testNestedObjectTags() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "profile": {
                      "type": "object",
                      "properties": {
                        "ssn": {
                          "type": "string",
                          "x-tags": ["PII", "SENSITIVE"]
                        }
                      }
                    }
                  }
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
                  "type": "object",
                  "properties": {
                    "emails": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "address": {
                            "type": "string",
                            "x-tags": ["PII", "EMAIL"]
                          }
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("emails[].address"));
    }

    @Test
    void testAdditionalPropertiesTags() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "metadata": {
                      "type": "object",
                      "additionalProperties": {
                        "type": "object",
                        "properties": {
                          "secret": {
                            "type": "string",
                            "x-tags": ["CONFIDENTIAL"]
                          }
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("CONFIDENTIAL"), tags.get("metadata.values.secret"));
    }

    @Test
    void testAllOfComposition() {
        String schema = """
                {
                  "allOf": [
                    {
                      "type": "object",
                      "properties": {
                        "id": {
                          "type": "string",
                          "x-tags": ["INTERNAL"]
                        }
                      }
                    },
                    {
                      "type": "object",
                      "properties": {
                        "ssn": {
                          "type": "string",
                          "x-tags": ["PII"]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("INTERNAL"), tags.get("id"));
        assertEquals(Set.of("PII"), tags.get("ssn"));
    }

    @Test
    void testConfluentTags() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "ssn": {
                      "type": "string",
                      "x-confluent-tags": ["PII"]
                    }
                  }
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
                  "type": "object",
                  "properties": {
                    "ssn": {
                      "type": "string",
                      "x-tags": ["PII"],
                      "x-confluent-tags": ["SENSITIVE"]
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "SENSITIVE"), tags.get("ssn"));
    }

    @Test
    void testNoTags() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "id": {"type": "integer"},
                    "name": {"type": "string"}
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testInvalidSchema() {
        Map<String, Set<String>> tags = extractor
                .extractTags(ContentHandle.create("not valid json"));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testDeeplyNestedStructure() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "level1": {
                      "type": "object",
                      "properties": {
                        "level2": {
                          "type": "array",
                          "items": {
                            "type": "object",
                            "properties": {
                              "secret": {
                                "type": "string",
                                "x-tags": ["DEEP"]
                              }
                            }
                          }
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("DEEP"), tags.get("level1.level2[].secret"));
    }
}
