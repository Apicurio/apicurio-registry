package io.apicurio.registry.rules.validity;

import io.apicurio.registry.avro.rules.validity.AvroContentValidator;
import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.rest.v3.beans.ArtifactReference;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Tests the Avro content validator.
 */
public class AvroContentValidatorTest extends ArtifactUtilProviderTestBase {

    @Test
    public void testValidAvroSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("avro-valid.json");
        AvroContentValidator validator = new AvroContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
    }

    @Test
    public void testInvalidAvroSchema() throws Exception {
        TypedContent content = resourceToTypedContentHandle("avro-invalid.json");
        AvroContentValidator validator = new AvroContentValidator();
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.SYNTAX_ONLY, content, Collections.emptyMap());
        });
    }

    @Test
    public void testValidateReferences() throws Exception {
        TypedContent content = resourceToTypedContentHandle("avro-valid-with-refs.json");
        AvroContentValidator validator = new AvroContentValidator();

        // Properly map both required references - success.
        {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(
                    ArtifactReference.builder().groupId("com.example.search").artifactId("SearchResultType")
                            .version("1.0").name("com.example.search.SearchResultType").build());
            references.add(ArtifactReference.builder().groupId("com.example.actions").artifactId("UserAction")
                    .version("1.1").name("com.example.actions.UserAction").build());
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
            references.add(
                    ArtifactReference.builder().groupId("com.example.search").artifactId("SearchResultType")
                            .version("1.0").name("com.example.search.SearchResultType").build());
            validator.validateReferences(content, references);
        });

        // Only map one of the two required refs - failure.
        Assertions.assertThrows(RuleViolationException.class, () -> {
            List<ArtifactReference> references = new ArrayList<>();
            references.add(
                    ArtifactReference.builder().groupId("com.example.search").artifactId("SearchResultType")
                            .version("1.0").name("com.example.search.SearchResultType").build());
            references.add(ArtifactReference.builder().groupId("default").artifactId("WrongType")
                    .version("2.3").name("com.example.invalid.WrongType").build());
            validator.validateReferences(content, references);
        });
    }

    @Test
    public void testValidateReferencesWithNoExternalTypes() throws Exception {
        // A schema with no external references should pass validation even with no references provided
        String schemaWithNoRefs = """
                {
                    "type": "record",
                    "namespace": "com.example",
                    "name": "User",
                    "fields": [
                        {"name": "name", "type": "string"},
                        {"name": "age", "type": "int"}
                    ]
                }""";
        TypedContent content = TypedContent.create(ContentHandle.create(schemaWithNoRefs),
                ContentTypes.APPLICATION_JSON);
        AvroContentValidator validator = new AvroContentValidator();
        validator.validateReferences(content, Collections.emptyList());
    }

    @Test
    public void testValidateReferencesExceptionContainsMissingRefNames() throws Exception {
        TypedContent content = resourceToTypedContentHandle("avro-valid-with-refs.json");
        AvroContentValidator validator = new AvroContentValidator();

        // When no references are provided, the exception should list the missing references
        RuleViolationException exception = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validateReferences(content, Collections.emptyList());
        });

        // Verify the exception contains information about the missing references
        String exceptionMessage = exception.getMessage();
        Assertions.assertNotNull(exceptionMessage);
        Assertions.assertTrue(exceptionMessage.contains("Missing reference detected"),
                "Exception should mention missing references");
    }

    @Test
    public void testValidateReferencesWithUnionTypes() throws Exception {
        // Schema with union type containing an external reference
        String schemaWithUnionRef = """
                {
                    "type": "record",
                    "namespace": "com.example",
                    "name": "Message",
                    "fields": [
                        {"name": "payload", "type": ["null", "com.example.data.Payload"]}
                    ]
                }""";
        TypedContent content = TypedContent.create(ContentHandle.create(schemaWithUnionRef),
                ContentTypes.APPLICATION_JSON);
        AvroContentValidator validator = new AvroContentValidator();

        // Without the reference mapped, should fail
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validateReferences(content, Collections.emptyList());
        });

        // With the reference properly mapped, should pass
        List<ArtifactReference> references = new ArrayList<>();
        references.add(ArtifactReference.builder().groupId("com.example.data").artifactId("Payload")
                .version("1.0").name("com.example.data.Payload").build());
        validator.validateReferences(content, references);
    }

    @Test
    public void testValidateReferencesWithArrayType() throws Exception {
        // Schema with array type containing an external reference
        String schemaWithArrayRef = """
                {
                    "type": "record",
                    "namespace": "com.example",
                    "name": "Container",
                    "fields": [
                        {"name": "items", "type": {"type": "array", "items": "com.example.data.Item"}}
                    ]
                }""";
        TypedContent content = TypedContent.create(ContentHandle.create(schemaWithArrayRef),
                ContentTypes.APPLICATION_JSON);
        AvroContentValidator validator = new AvroContentValidator();

        // Without the reference mapped, should fail
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validateReferences(content, Collections.emptyList());
        });

        // With the reference properly mapped, should pass
        List<ArtifactReference> references = new ArrayList<>();
        references.add(ArtifactReference.builder().groupId("com.example.data").artifactId("Item")
                .version("1.0").name("com.example.data.Item").build());
        validator.validateReferences(content, references);
    }

    @Test
    public void testValidateReferencesWithMapType() throws Exception {
        // Schema with map type containing an external reference
        String schemaWithMapRef = """
                {
                    "type": "record",
                    "namespace": "com.example",
                    "name": "Registry",
                    "fields": [
                        {"name": "entries", "type": {"type": "map", "values": "com.example.data.Entry"}}
                    ]
                }""";
        TypedContent content = TypedContent.create(ContentHandle.create(schemaWithMapRef),
                ContentTypes.APPLICATION_JSON);
        AvroContentValidator validator = new AvroContentValidator();

        // Without the reference mapped, should fail
        Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validateReferences(content, Collections.emptyList());
        });

        // With the reference properly mapped, should pass
        List<ArtifactReference> references = new ArrayList<>();
        references.add(ArtifactReference.builder().groupId("com.example.data").artifactId("Entry")
                .version("1.0").name("com.example.data.Entry").build());
        validator.validateReferences(content, references);
    }

}
