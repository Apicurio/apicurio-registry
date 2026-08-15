package io.apicurio.registry.rules.validity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Guards the agreement between the Prompt Template JSON Schema served at
 * {@code /.well-known/schemas/prompt-template/v1} and the rules enforced by
 * {@link PromptTemplateContentValidator}.
 * <p>
 * The two are maintained by hand in different modules, so without this test they can drift apart
 * as soon as a format or field is added on one side only.
 */
class PromptTemplateSchemaSyncTest {

    private static final String SCHEMA_RESOURCE = "schemas/prompt-template-v1.json";

    private JsonNode loadSchema() throws Exception {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(SCHEMA_RESOURCE)) {
            Assertions.assertNotNull(in, "Could not find " + SCHEMA_RESOURCE + " on the test classpath");
            return new ObjectMapper().readTree(in);
        }
    }

    @Test
    void testTemplateFormatEnumMatchesValidator() throws Exception {
        JsonNode enumNode = loadSchema().path("properties").path("templateFormat").path("enum");
        Assertions.assertTrue(enumNode.isArray(),
                "The published schema must declare an enum for 'templateFormat'");

        List<String> published = new ArrayList<>();
        enumNode.forEach(value -> published.add(value.asText()));

        Assertions.assertEquals(PromptTemplateContentValidator.getSupportedTemplateFormats(), published,
                "The 'templateFormat' enum in " + SCHEMA_RESOURCE + " must list exactly the formats "
                        + "accepted by PromptTemplateContentValidator, in the same order");
    }

    @Test
    void testTemplateFormatDefaultIsSupported() throws Exception {
        JsonNode defaultNode = loadSchema().path("properties").path("templateFormat").path("default");
        Assertions.assertTrue(defaultNode.isTextual(),
                "The published schema must declare a default for 'templateFormat'");
        Assertions.assertTrue(
                PromptTemplateContentValidator.getSupportedTemplateFormats()
                        .contains(defaultNode.asText()),
                "The default 'templateFormat' must be a format the validator accepts");
    }

    @Test
    void testPromptyAlignedFieldsArePublished() throws Exception {
        JsonNode properties = loadSchema().path("properties");
        for (String field : List.of("templateFormat", "model", "authors", "tags")) {
            Assertions.assertTrue(properties.has(field),
                    "The documented Prompty aligned field '" + field + "' is missing from "
                            + SCHEMA_RESOURCE);
        }
    }
}
