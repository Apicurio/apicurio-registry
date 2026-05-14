package io.apicurio.registry.rules.validity;

import io.apicurio.registry.content.ContentHandle;
import io.apicurio.registry.content.PromptTemplateContentAccepter;
import io.apicurio.registry.content.TypedContent;
import io.apicurio.registry.content.extract.ExtractedMetaData;
import io.apicurio.registry.content.extract.PromptTemplateContentExtractor;
import io.apicurio.registry.rules.violation.RuleViolationException;
import io.apicurio.registry.types.ContentTypes;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Collections;

/**
 * Tests for PromptTemplateContentValidator, PromptTemplateContentAccepter, and PromptTemplateContentExtractor.
 */
class PromptTemplateContentValidatorTest {

    private static final String VALID_PROMPT_TEMPLATE = """
            {
                "templateId": "code-review",
                "description": "A code review prompt",
                "template": "Review the following {{language}} code:\\n\\n{{code}}\\n\\nFocus on {{focus}}.",
                "variables": {
                    "language": {
                        "type": "string",
                        "required": true,
                        "description": "Programming language"
                    },
                    "code": {
                        "type": "string",
                        "required": true,
                        "description": "Code to review"
                    },
                    "focus": {
                        "type": "string",
                        "required": false,
                        "default": "best practices",
                        "enum": ["best practices", "security", "performance"]
                    }
                },
                "metadata": {
                    "category": "development"
                }
            }
            """;

    private static final String MISSING_TEMPLATE_ID = """
            {
                "template": "Hello {{name}}"
            }
            """;

    private static final String MISSING_TEMPLATE = """
            {
                "templateId": "test"
            }
            """;

    private static final String UNDEFINED_VARIABLE = """
            {
                "templateId": "test",
                "template": "Hello {{name}}, your code is {{quality}}",
                "variables": {
                    "name": { "type": "string" }
                }
            }
            """;

    private static final String INVALID_VARIABLE_TYPE = """
            {
                "templateId": "test",
                "template": "Hello {{name}}",
                "variables": {
                    "name": { "type": "invalid-type" }
                }
            }
            """;

    private static final String VALID_PROMPT_TEMPLATE_YAML = """
            templateId: code-review
            description: A code review prompt
            template: "Review the following {{language}} code:\\n\\n{{code}}\\n\\nFocus on {{focus}}."
            variables:
              language:
                type: string
                required: true
                description: Programming language
              code:
                type: string
                required: true
                description: Code to review
              focus:
                type: string
                required: false
                default: best practices
                enum:
                  - best practices
                  - security
                  - performance
            """;

    private TypedContent create(String json) {
        return TypedContent.create(ContentHandle.create(json), ContentTypes.APPLICATION_JSON);
    }

    private TypedContent createYaml(String yaml) {
        return TypedContent.create(ContentHandle.create(yaml), ContentTypes.APPLICATION_YAML);
    }

    private TypedContent createPromptTemplate(String yaml) {
        return TypedContent.create(ContentHandle.create(yaml), ContentTypes.TEXT_PROMPT_TEMPLATE);
    }

    @Test
    void testValidPromptTemplate() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, create(VALID_PROMPT_TEMPLATE), Collections.emptyMap());
    }

    @Test
    void testSyntaxOnly() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.SYNTAX_ONLY, create(VALID_PROMPT_TEMPLATE), Collections.emptyMap());
    }

    @Test
    void testMissingTemplateId() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(MISSING_TEMPLATE_ID), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("templateId")));
    }

    @Test
    void testMissingTemplate() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(MISSING_TEMPLATE), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("template")));
    }

    @Test
    void testUndefinedVariable() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(UNDEFINED_VARIABLE), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("quality")));
    }

    @Test
    void testInvalidVariableType() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(INVALID_VARIABLE_TYPE), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("invalid-type")));
    }

    @Test
    void testAccepterAcceptsValidTemplate() {
        PromptTemplateContentAccepter accepter = new PromptTemplateContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(create(VALID_PROMPT_TEMPLATE), Collections.emptyMap()));
    }

    @Test
    void testAccepterAcceptsWithSchemaField() {
        String withSchema = """
                {
                    "$schema": "https://example.com/prompt-template/v1",
                    "templateId": "test",
                    "template": "Hello"
                }
                """;
        PromptTemplateContentAccepter accepter = new PromptTemplateContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(create(withSchema), Collections.emptyMap()));
    }

    @Test
    void testAccepterRejectsNonTemplate() {
        String jsonSchema = """
                {
                    "$schema": "http://json-schema.org/draft-07/schema#",
                    "type": "object"
                }
                """;
        PromptTemplateContentAccepter accepter = new PromptTemplateContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(create(jsonSchema), Collections.emptyMap()));
    }

    @Test
    void testContentExtractor() {
        PromptTemplateContentExtractor extractor = new PromptTemplateContentExtractor();
        ExtractedMetaData metaData = extractor.extract(ContentHandle.create(VALID_PROMPT_TEMPLATE));
        Assertions.assertNotNull(metaData);
        Assertions.assertEquals("code-review", metaData.getName());
        Assertions.assertEquals("A code review prompt", metaData.getDescription());
    }

    @Test
    void testInvalidOutputSchemaType() {
        String invalidOutputSchema = """
                {
                    "templateId": "test",
                    "template": "Hello",
                    "outputSchema": "not-an-object"
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(invalidOutputSchema), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("outputSchema")));
    }

    @Test
    void testValidPromptTemplateYaml() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, createYaml(VALID_PROMPT_TEMPLATE_YAML),
                Collections.emptyMap());
    }

    @Test
    void testAccepterAcceptsYaml() {
        PromptTemplateContentAccepter accepter = new PromptTemplateContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(createYaml(VALID_PROMPT_TEMPLATE_YAML),
                Collections.emptyMap()));
    }

    @Test
    void testAccepterAcceptsTextPromptTemplateContentType() {
        PromptTemplateContentAccepter accepter = new PromptTemplateContentAccepter();
        Assertions.assertTrue(accepter.acceptsContent(createPromptTemplate(VALID_PROMPT_TEMPLATE_YAML),
                Collections.emptyMap()));
    }

    @Test
    void testValidateTextPromptTemplateContentType() {
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, createPromptTemplate(VALID_PROMPT_TEMPLATE_YAML),
                Collections.emptyMap());
    }

    @Test
    void testAccepterRejectsInvalidYaml() {
        String invalidYaml = """
                name: not a prompt template
                type: object
                """;
        PromptTemplateContentAccepter accepter = new PromptTemplateContentAccepter();
        Assertions.assertFalse(accepter.acceptsContent(createYaml(invalidYaml), Collections.emptyMap()));
    }

    @Test
    void testValidateYamlMissingTemplateId() {
        String yaml = """
                template: "Hello {{name}}"
                variables:
                  name:
                    type: string
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, createYaml(yaml), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("templateId")));
    }

    @Test
    void testValidateYamlUndefinedVariable() {
        String yaml = """
                templateId: test
                template: "Hello {{name}} and {{unknown}}"
                variables:
                  name:
                    type: string
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, createYaml(yaml), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("unknown")));
    }
}
