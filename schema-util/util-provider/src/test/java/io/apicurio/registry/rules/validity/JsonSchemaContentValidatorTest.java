package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests the JSON Schema content validator.
 */
public class JsonSchemaContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidJsonSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidJsonSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-invalid.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testInvalidJsonSchemaVersion() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid-d7.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidJsonSchemaFull() throws Exception {
        TypedContent content = resourceToTypedContentHandle("bad-json-schema-v1.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, content, Collections.emptyMap());
        });
        Assertions.assertFalse(error.getCauses().isEmpty());
        Assertions.assertEquals("expected type: Number, found: Boolean",
                error.getCauses().iterator().next().getDescription());
        Assertions.assertEquals("#/items/properties/price/exclusiveMinimum",
                error.getCauses().iterator().next().getContext());
    }

    @Test
    public void testJsonSchemaWithReferences() throws Exception {
        TypedContent city = resourceToTypedContentHandle("city.json");
        TypedContent citizen = resourceToTypedContentHandle("citizen.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, citizen,
                Collections.singletonMap("https://example.com/city.json", city));
    }
}
