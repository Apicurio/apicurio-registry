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
    void testSimpleXTags() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "email": {
                      "type": "string",
                      "x-tags": ["PII", "EMAIL"]
                    },
                    "name": {
                      "type": "string"
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("email"));
    }

    @Test
    void testNestedObjectsAndArrays() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "customer": {
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
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("customer.emails[].address"));
    }

    @Test
    void testRefResolutionWithDefs() {
        String schema = """
                {
                  "type": "object",
                  "$defs": {
                    "address": {
                      "type": "object",
                      "properties": {
                        "street": {
                          "type": "string",
                          "x-tags": ["ADDRESS"]
                        },
                        "zip": {
                          "type": "string"
                        }
                      }
                    }
                  },
                  "properties": {
                    "billingAddress": {
                      "$ref": "#/$defs/address",
                      "x-tags": ["PRIMARY"]
                    },
                    "shippingAddresses": {
                      "type": "array",
                      "items": {
                        "$ref": "#/$defs/address"
                      }
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(3, tags.size());
        assertEquals(Set.of("PRIMARY"), tags.get("billingAddress"));
        assertEquals(Set.of("ADDRESS"), tags.get("billingAddress.street"));
        assertEquals(Set.of("ADDRESS"), tags.get("shippingAddresses[].street"));
    }

    @Test
    void testInvalidSchema() {
        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create("not valid json"));
        assertTrue(tags.isEmpty());
    }
}
