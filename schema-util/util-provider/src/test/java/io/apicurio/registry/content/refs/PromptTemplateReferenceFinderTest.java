package io.apicurio.registry.content.refs;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromptTemplateReferenceFinderTest {

    private PromptTemplateReferenceFinder finder;

    @BeforeEach
    void setUp() {
        finder = new PromptTemplateReferenceFinder();
    }

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    @Test
    void testNoReferences() {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello {{name}}",
                    "variables": {
                        "name": { "type": "string" }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertTrue(refs.isEmpty());
    }

    @Test
    void testFindsRefInVariables() {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello {{config}}",
                    "variables": {
                        "config": { "$ref": "shared-vars.json#/defs/Config" }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertEquals(1, refs.size());
        ExternalReference ref = refs.iterator().next();
        assertEquals("shared-vars.json", ref.getResource());
    }

    @Test
    void testFindsRefInOutputSchema() {
        String template = """
                {
                    "templateId": "test",
                    "template": "Analyze this",
                    "outputSchema": {
                        "type": "object",
                        "properties": {
                            "result": { "$ref": "result-types.json" }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertEquals(1, refs.size());
        assertTrue(refs.stream().anyMatch(r -> r.getResource().equals("result-types.json")));
    }

    @Test
    void testDoesNotFindRefsInTemplateString() {
        String template = """
                {
                    "templateId": "test",
                    "template": "See $ref: external-doc.json for details about {{topic}}"
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertTrue(refs.isEmpty());
    }

    @Test
    void testIgnoresInternalRefs() {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello",
                    "variables": {
                        "config": { "$ref": "#/definitions/Config" }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertTrue(refs.isEmpty());
    }

    @Test
    void testFindsRefsInBothSections() {
        String template = """
                {
                    "templateId": "test",
                    "template": "Analyze {{input}}",
                    "variables": {
                        "input": { "$ref": "input-types.json" }
                    },
                    "outputSchema": {
                        "type": "object",
                        "properties": {
                            "result": { "$ref": "output-types.json" }
                        }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertEquals(2, refs.size());
        assertTrue(refs.stream().anyMatch(r -> r.getResource().equals("input-types.json")));
        assertTrue(refs.stream().anyMatch(r -> r.getResource().equals("output-types.json")));
    }

    @Test
    void testDoesNotSearchTopLevelFields() {
        String template = """
                {
                    "templateId": "test",
                    "template": "Hello",
                    "metadata": {
                        "source": { "$ref": "metadata-source.json" }
                    }
                }
                """;

        Set<ExternalReference> refs = finder.findExternalReferences(create(template));
        assertTrue(refs.isEmpty(), "Should not find refs in metadata, only in variables and outputSchema");
    }
}
