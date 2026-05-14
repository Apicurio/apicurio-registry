package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelSchemaReferenceFinderTest {

    private ModelSchemaReferenceFinder finder;

    @BeforeEach
    void setUp() {
        finder = new ModelSchemaReferenceFinder();
    }

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testNoReferences() {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "prompt": { "type": "string" }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(schema));
        assertTrue(refs.isEmpty());
    }

    @Test
    void testFindsExternalRef() {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "common-types.json#/defs/Config" }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(schema));
        assertEquals(1, refs.size());
        ExternalReference ref = refs.iterator().next();
        assertEquals("common-types.json", ref.getResource());
        assertEquals("#/defs/Config", ref.getComponent());
    }

    @Test
    void testIgnoresInternalRefs() {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "#/definitions/Config" }
                        }
                    },
                    "definitions": {
                        "Config": { "type": "object" }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(schema));
        assertTrue(refs.isEmpty());
    }

    @Test
    void testFindsMultipleRefs() {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "config": { "$ref": "config.json" },
                            "params": { "$ref": "params.json#/defs/Params" }
                        }
                    },
                    "output": {
                        "type": "object",
                        "properties": {
                            "result": { "$ref": "result-types.json" }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(schema));
        assertEquals(3, refs.size());
    }

    @Test
    void testFindsRefsInNestedStructures() {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "nested": {
                                "type": "object",
                                "properties": {
                                    "deep": { "$ref": "deep-type.json" }
                                }
                            }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(schema));
        assertEquals(1, refs.size());
        assertTrue(refs.stream().anyMatch(r -> r.getResource().equals("deep-type.json")));
    }

    @Test
    void testDeduplicatesRefs() {
        String schema = """
                {
                    "modelId": "test",
                    "input": {
                        "type": "object",
                        "properties": {
                            "a": { "$ref": "shared.json" },
                            "b": { "$ref": "shared.json" }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(schema));
        assertEquals(1, refs.size());
    }
}
