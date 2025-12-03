package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.RuleViolationException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

    @Test
    public void testValidateReferences() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid-with-refs.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Category")
                    .version("1.0")
                    .name("example.com/schemas/Category")
                    .build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Customer")
                    .version("1.1")
                    .name("example.com/schemas/Customer")
                    .build());
            validator.validateReferences(content, references);
        }

        // Don't map either of the required references - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Category")
                    .version("1.0")
                    .name("example.com/schemas/Category")
                    .build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("Category")
                    .version("1.0")
                    .name("example.com/schemas/Category")
                    .build());
            references.add(ArtifactReference.builder()
                    .groupId("default")
                    .artifactId("WrongSchema")
                    .version("2.3")
                    .name("example.com/schemas/WrongSchema")
                    .build());
            validator.validateReferences(content, references);
        });
    }
}
