package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.json.rules.validity.JsonSchemaContentValidator;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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

    /**
     * A reference Registry has no content for must not be fetched, or an uploaded schema could make
     * the server request any URL. It is loaded as a schema that accepts anything instead. Both
     * libraries the validator loads schemas with are covered.
     */
    @ParameterizedTest
    @ValueSource(strings = {"http://json-schema.org/draft-07/schema#", "https://json-schema.org/draft/2020-12/schema"})
    public void testReferenceWithoutContentIsNotFetched(String draft) throws Exception {
        TypedContent content = TypedContent.create(ContentHandle.create("""
                {
                  "$schema": "%s",
                  "type": "object",
                  "properties": { "x": { "$ref": "http://127.0.0.1:1/missing.json" } }
                }
                """.formatted(draft)), ContentTypes.APPLICATION_JSON);
        new JsonSchemaContentValidator().validate(ValidityLevel.FULL, content, Collections.emptyMap());
    }

    /**
     * A reference recorded for the artifact that its content does not use is most likely a typo in
     * the reference name, so it is rejected rather than ignored.
     */
    @Test
    public void testUnusedReferenceIsRejected() throws Exception {
        TypedContent content = resourceToTypedContentHandle("jsonschema-valid-d7.json");
        TypedContent city = resourceToTypedContentHandle("city.json");
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class,
                () -> new JsonSchemaContentValidator().validate(ValidityLevel.FULL, content,
                        Map.of("https://example.com/city.json", city)));
        String description = error.getCauses().iterator().next().getDescription();
        Assertions.assertTrue(description.contains("Unused reference records: https://example.com/city.json"),
                () -> "The violation should name the unused reference: " + description);
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

    @Test
    public void testValidateReferencesWithNullReferenceList() throws Exception {
        TypedContent contentWithoutRefs = resourceToTypedContentHandle("jsonschema-valid.json");
        TypedContent contentWithRefs = resourceToTypedContentHandle("jsonschema-valid-with-refs.json");
        JsonSchemaContentValidator validator = new JsonSchemaContentValidator();

        validator.validateReferences(contentWithoutRefs, null);

        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validateReferences(contentWithRefs, null);
        });
    }
}
