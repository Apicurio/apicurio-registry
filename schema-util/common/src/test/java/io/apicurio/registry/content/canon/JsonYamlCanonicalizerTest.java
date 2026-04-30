/*
 * Copyright 2025 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.apicurio.registry.content.canon;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Test;

import java.util.Iterator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonYamlCanonicalizerTest {

    private final ObjectMapper jsonMapper = new ObjectMapper();

    private TypedContent createJson(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private TypedContent createYaml(String yaml) {
        return TypedContent.create(ContentHandle.create(yaml), ContentTypes.APPLICATION_YAML);
    }

    @Test
    void canonicalizeJsonInput() throws Exception {
        String input = """
            {
                "zebra": "last",
                "alpha": "first",
                "middle": "between"
            }
            """;

        TypedContent result = JsonYamlCanonicalizer.canonicalize(createJson(input));

        assertEquals(ContentTypes.APPLICATION_JSON, result.getContentType());
        JsonNode tree = jsonMapper.readTree(result.getContent().content());
        Iterator<String> fields = tree.fieldNames();
        assertEquals("alpha", fields.next());
        assertEquals("middle", fields.next());
        assertEquals("zebra", fields.next());
    }

    @Test
    void canonicalizeYamlInput() throws Exception {
        String input = """
            zebra: last
            alpha: first
            middle: between
            """;

        TypedContent result = JsonYamlCanonicalizer.canonicalize(createYaml(input));

        assertEquals(ContentTypes.APPLICATION_JSON, result.getContentType());
        JsonNode tree = jsonMapper.readTree(result.getContent().content());
        Iterator<String> fields = tree.fieldNames();
        assertEquals("alpha", fields.next());
        assertEquals("middle", fields.next());
        assertEquals("zebra", fields.next());
    }

    @Test
    void canonicalizeSortsNestedKeys() throws Exception {
        String input = """
            {
                "outer": {
                    "z_field": "z",
                    "a_field": "a"
                },
                "alpha": "first"
            }
            """;

        TypedContent result = JsonYamlCanonicalizer.canonicalize(createJson(input));

        JsonNode tree = jsonMapper.readTree(result.getContent().content());
        Iterator<String> outerFields = tree.fieldNames();
        assertEquals("alpha", outerFields.next());
        assertEquals("outer", outerFields.next());

        Iterator<String> innerFields = tree.get("outer").fieldNames();
        assertEquals("a_field", innerFields.next());
        assertEquals("z_field", innerFields.next());
    }

    @Test
    void canonicalizeProducesDeterministicOutput() throws ContentCanonicalizationException {
        String input1 = """
            { "b": 2, "a": 1, "c": 3 }
            """;
        String input2 = """
            { "c": 3, "a": 1, "b": 2 }
            """;

        TypedContent result1 = JsonYamlCanonicalizer.canonicalize(createJson(input1));
        TypedContent result2 = JsonYamlCanonicalizer.canonicalize(createJson(input2));

        assertEquals(result1.getContent().content(), result2.getContent().content());
    }

    @Test
    void canonicalizeThrowsOnInvalidContent() {
        TypedContent content = createJson("not valid json");
        assertThrows(ContentCanonicalizationException.class, () -> JsonYamlCanonicalizer.canonicalize(content));
    }

    @Test
    void canonicalizeOutputIsJson() throws ContentCanonicalizationException {
        String input = """
            {
                "openapi": "3.0.0",
                "info": { "title": "Test", "version": "1.0" }
            }
            """;

        TypedContent result = JsonYamlCanonicalizer.canonicalize(createJson(input));

        assertTrue(result.getContent().content().startsWith("{"));
        assertEquals(ContentTypes.APPLICATION_JSON, result.getContentType());
    }

    @Test
    void canonicalizeYamlWithPromptTemplateContentType() throws Exception {
        String input = """
            templateId: greet
            template: "Hello {{name}}"
            variables:
              name:
                type: string
            """;

        TypedContent content = TypedContent.create(
            ContentHandle.create(input), "text/x-prompt-template");
        TypedContent result = JsonYamlCanonicalizer.canonicalize(content);

        assertEquals(ContentTypes.APPLICATION_JSON, result.getContentType());
        JsonNode tree = jsonMapper.readTree(result.getContent().content());
        assertTrue(tree.has("templateId"));
        assertTrue(tree.has("template"));
        assertTrue(tree.has("variables"));
    }
}
