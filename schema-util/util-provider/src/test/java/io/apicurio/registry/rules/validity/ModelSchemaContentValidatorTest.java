package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.ModelSchemaContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.content.extract.ModelSchemaContentExtractor;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests for ModelSchemaContentValidator, ModelSchemaContentAccepter, and ModelSchemaContentExtractor.
 */
class ModelSchemaContentValidatorTest {

    private static final String VALID_MODEL_SCHEMA = """
            {
                "modelId": "gpt-4o",
                "provider": "openai",
                "version": "1.0",
                "input": {
                    "type": "object",
                    "properties": {
                        "prompt": { "type": "string" },
                        "temperature": { "type": "number" }
                    },
                    "required": ["prompt"]
                },
                "output": {
                    "type": "object",
                    "properties": {
                        "text": { "type": "string" },
                        "usage": { "type": "object" }
                    }
                },
                "metadata": {
                    "category": "text-generation"
                }
            }
            """;

    private static final String MISSING_MODEL_ID = """
            {
                "input": {
                    "type": "object",
                    "properties": {
                        "prompt": { "type": "string" }
                    }
                }
            }
            """;

    private static final String MISSING_INPUT_OUTPUT = """
            {
                "modelId": "gpt-4o"
            }
            """;

    private static final String INVALID_INPUT_TYPE = """
            {
                "modelId": "gpt-4o",
                "input": "not-an-object"
            }
            """;

    private static final String INVALID_METADATA_TYPE = """
            {
                "modelId": "gpt-4o",
                "input": { "type": "object" },
                "metadata": "not-an-object"
            }
            """;

    private static final String VALID_MODEL_SCHEMA_YAML = """
            modelId: gpt-4o
            provider: openai
            version: "1.0"
            input:
              type: object
              properties:
                prompt:
                  type: string
                temperature:
                  type: number
              required:
                - prompt
            output:
              type: object
              properties:
                text:
                  type: string
                usage:
                  type: object
            """;

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private TypedContent createYaml(String yaml) {
        return TypedContent.create(ContentHandle.create(yaml), ContentTypes.APPLICATION_YAML);
    }

    @Test
    void testValidModelSchema() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, create(VALID_MODEL_SCHEMA), Collections.emptyMap());
    }

    @Test
    void testSyntaxOnly() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, create(VALID_MODEL_SCHEMA), Collections.emptyMap());
    }

    @Test
    void testNoneLevel() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        validator.validate(ValidityLevel.NONE, create("not json at all"), Collections.emptyMap());
    }

    @Test
    void testMissingModelId() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(MISSING_MODEL_ID), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("modelId")));
    }

    @Test
    void testMissingInputAndOutput() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(MISSING_INPUT_OUTPUT), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("input")));
    }

    @Test
    void testInvalidInputType() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(INVALID_INPUT_TYPE), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("input")));
    }

    @Test
    void testInvalidMetadataType() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(INVALID_METADATA_TYPE), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("metadata")));
    }

    @Test
    void testAccepterAcceptsValidModelSchema() {
        ModelSchemaContentAccepter accepter = new ModelSchemaContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(create(VALID_MODEL_SCHEMA), Collections.emptyMap()));
    }

    @Test
    void testAccepterAcceptsWithSchemaField() {
        String withSchema = """
                {
                    "$schema": "https://example.com/model-schema/v1",
                    "modelId": "test",
                    "input": {}
                }
                """;
        ModelSchemaContentAccepter accepter = new ModelSchemaContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(create(withSchema), Collections.emptyMap()));
    }

    @Test
    void testAccepterRejectsNonModelSchema() {
        String jsonSchema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object",
                    "properties": { "name": { "type": "string" } }
                }
                """;
        ModelSchemaContentAccepter accepter = new ModelSchemaContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(create(jsonSchema), Collections.emptyMap()));
    }

    @Test
    void testContentExtractor() {
        ModelSchemaContentExtractor extractor = new ModelSchemaContentExtractor();
        ExtractedMetaData metaData = extractor.extract(ContentHandle.create(VALID_MODEL_SCHEMA));
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals("gpt-4o", metaData.getName());
        Assertions.assertEquals("openai", metaData.getDescription());
    }

    @Test
    void testValidModelSchemaYaml() {
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        validator.validate(ValidityLevel.FULL, createYaml(VALID_MODEL_SCHEMA_YAML), Collections.emptyMap());
    }

    @Test
    void testAccepterAcceptsYaml() {
        ModelSchemaContentAccepter accepter = new ModelSchemaContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(createYaml(VALID_MODEL_SCHEMA_YAML),
                Collections.emptyMap()));
    }

    @Test
    void testAccepterRejectsInvalidYaml() {
        String invalidYaml = """
                name: not a model schema
                type: object
                """;
        ModelSchemaContentAccepter accepter = new ModelSchemaContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(createYaml(invalidYaml), Collections.emptyMap()));
    }

    @Test
    void testValidateYamlMissingModelId() {
        String yaml = """
                input:
                  type: object
                  properties:
                    prompt:
                      type: string
                """;
        ModelSchemaContentValidator validator = new ModelSchemaContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, createYaml(yaml), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("modelId")));
    }
}
