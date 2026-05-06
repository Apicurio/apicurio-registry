/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

    @Test
    void testAllOfComposition() {
        String schema = """
                {
                  "allOf": [
                    {
                      "properties": {
                        "name": {
                          "type": "string",
                          "x-tags": ["PII"]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("name"));
    }

    @Test
    void testAnyOfComposition() {
        String schema = """
                {
                  "anyOf": [
                    {
                      "properties": {
                        "email": {
                          "type": "string",
                          "x-tags": ["PII", "EMAIL"]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("email"));
    }

    @Test
    void testOneOfComposition() {
        String schema = """
                {
                  "oneOf": [
                    {
                      "properties": {
                        "phone": {
                          "type": "string",
                          "x-tags": ["PII", "PHONE"]
                        }
                      }
                    }
                  ]
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII", "PHONE"), tags.get("phone"));
    }

    @Test
    void testCircularRef() {
        String schema = """
                {
                  "$defs": {
                    "node": {
                      "type": "object",
                      "properties": {
                        "value": {
                          "type": "string",
                          "x-tags": ["DATA"]
                        },
                        "child": {
                          "$ref": "#/$defs/node"
                        }
                      }
                    }
                  },
                  "properties": {
                    "root": {
                      "$ref": "#/$defs/node"
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("DATA"), tags.get("root.value"));
    }

    @Test
    void testMultipleFieldsWithTagsAtDifferentNestingLevels() {
        String schema = """
                {
                  "type": "object",
                  "properties": {
                    "id": {
                      "type": "string",
                      "x-tags": ["IDENTIFIER"]
                    },
                    "profile": {
                      "type": "object",
                      "properties": {
                        "email": {
                          "type": "string",
                          "x-tags": ["PII", "EMAIL"]
                        },
                        "address": {
                          "type": "object",
                          "properties": {
                            "street": {
                              "type": "string",
                              "x-tags": ["ADDRESS"]
                            }
                          }
                        }
                      }
                    },
                    "orders": {
                      "type": "array",
                      "items": {
                        "type": "object",
                        "properties": {
                          "productId": {
                            "type": "string",
                            "x-tags": ["IDENTIFIER"]
                          }
                        }
                      }
                    }
                  }
                }
                """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(4, tags.size());
        assertEquals(Set.of("IDENTIFIER"), tags.get("id"));
        assertEquals(Set.of("PII", "EMAIL"), tags.get("profile.email"));
        assertEquals(Set.of("ADDRESS"), tags.get("profile.address.street"));
        assertEquals(Set.of("IDENTIFIER"), tags.get("orders[].productId"));
    }

    @Test
    void testPrefixItems() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "coordinates": {
              "type": "array",
              "prefixItems": [
                {
                  "type": "number",
                  "x-tags": ["LATITUDE"]
                },
                {
                  "type": "number",
                  "x-tags": ["LONGITUDE"]
                }
              ]
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("LATITUDE", "LONGITUDE"), tags.get("coordinates[]"));
    }

    @Test
    void testAllOfWithPropertiesAtSameLevel() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "id": {
              "type": "string",
              "x-tags": ["IDENTIFIER"]
            }
          },
          "allOf": [
            {
              "properties": {
                "name": {
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
        assertEquals(Set.of("IDENTIFIER"), tags.get("id"));
        assertEquals(Set.of("PII"), tags.get("name"));
    }

    @Test
    void testAllOfWithNestedPropertiesDoesNotAppendArrayBrackets() {
        String schema = """
        {
          "allOf": [
            {
              "type": "object",
              "properties": {
                "address": {
                  "type": "object",
                  "properties": {
                    "street": {
                      "type": "string",
                      "x-tags": ["ADDRESS"]
                    }
                  }
                }
              }
            }
          ]
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("ADDRESS"), tags.get("address.street"));
    }

    @Test
    void testAnyOfWithMultipleBranches() {
        String schema = """
        {
          "anyOf": [
            {
              "properties": {
                "email": {
                  "type": "string",
                  "x-tags": ["PII", "EMAIL"]
                }
              }
            },
            {
              "properties": {
                "phone": {
                  "type": "string",
                  "x-tags": ["PII", "PHONE"]
                }
              }
            }
          ]
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(2, tags.size());
        assertEquals(Set.of("PII", "EMAIL"), tags.get("email"));
        assertEquals(Set.of("PII", "PHONE"), tags.get("phone"));
    }

    @Test
    void testOneOfWithNestedArrayDoesNotAppendBracketsToOneOf() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "contact": {
              "oneOf": [
                {
                  "type": "object",
                  "properties": {
                    "email": {
                      "type": "string",
                      "x-tags": ["PII"]
                    }
                  }
                }
              ]
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("contact.email"));
    }

    @Test
    void testExternalRefIgnored() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "refField": {
              "$ref": "other.json#/definitions/Foo"
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testCircularRefDoesNotInfiniteLoop() {
        String schema = """
        {
          "$defs": {
            "person": {
              "type": "object",
              "properties": {
                "name": {
                  "type": "string",
                  "x-tags": ["PII"]
                },
                "friend": {
                  "$ref": "#/$defs/person"
                },
                "colleague": {
                  "$ref": "#/$defs/person"
                }
              }
            }
          },
          "properties": {
            "root": {
              "$ref": "#/$defs/person"
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("PII"), tags.get("root.name"));
    }

    @Test
    void testEmptySchema() {
        String schema = "{}";

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testSchemaWithNoTags() {
        String schema = """
        {
          "type": "object",
          "properties": {
            "name": {
              "type": "string"
            },
            "age": {
              "type": "integer"
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertTrue(tags.isEmpty());
    }

    @Test
    void testItemsWithRefAndTags() {
        String schema = """
        {
          "$defs": {
            "item": {
              "type": "object",
              "properties": {
                "code": {
                  "type": "string",
                  "x-tags": ["SENSITIVE"]
                }
              }
            }
          },
          "type": "object",
          "properties": {
            "items": {
              "type": "array",
              "items": {
                "$ref": "#/$defs/item"
              }
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("SENSITIVE"), tags.get("items[].code"));
    }

    @Test
    void testPrefixItemsWithRef() {
        String schema = """
        {
          "$defs": {
            "point": {
              "type": "object",
              "properties": {
                "lat": {
                  "type": "number",
                  "x-tags": ["LOCATION"]
                }
              }
            }
          },
          "type": "object",
          "properties": {
            "data": {
              "type": "array",
              "prefixItems": [
                { "$ref": "#/$defs/point" }
              ]
            }
          }
        }
        """;

        Map<String, Set<String>> tags = extractor.extractTags(ContentHandle.create(schema));

        assertEquals(1, tags.size());
        assertEquals(Set.of("LOCATION"), tags.get("data[].lat"));
    }
}
