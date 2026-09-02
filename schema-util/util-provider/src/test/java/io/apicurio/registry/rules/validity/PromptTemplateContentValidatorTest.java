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
import java.util.List;

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
    void testUndefinedVariableWithWhitespace() {
        // Same as UNDEFINED_VARIABLE, but the undefined placeholder is written with spaces inside
        // the braces. Before the shared pattern, the validator's \w+ regex could not see it, so
        // the "used but not defined" rule silently never fired.
        String undefinedVariableWithWhitespace = """
                {
                    "templateId": "test",
                    "template": "Hello {{name}}, your code is {{ quality }}",
                    "variables": {
                        "name": { "type": "string" }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(undefinedVariableWithWhitespace),
                    Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("quality")));
    }

    @Test
    void testDefinedVariableWithWhitespaceIsAccepted() {
        // The flip side: a space-padded placeholder that *is* defined must not be reported.
        String definedVariableWithWhitespace = """
                {
                    "templateId": "test",
                    "template": "Hello {{ name }}, welcome to {{  place  }}.",
                    "variables": {
                        "name": { "type": "string" },
                        "place": { "type": "string" }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, create(definedVariableWithWhitespace),
                Collections.emptyMap());
    }

    @Test
    void testConditionalBlockMarkersAreNotVariables() {
        // {{#if ...}} and {{/if}} are control syntax, not variables, and must not be reported as
        // undefined. Only the real {{ name }} placeholder inside the block counts.
        String withConditionalBlock = """
                {
                    "templateId": "test",
                    "template": "{{#if premium}}Hello {{ name }}{{/if}}",
                    "variables": {
                        "name": { "type": "string" },
                        "premium": { "type": "boolean" }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, create(withConditionalBlock), Collections.emptyMap());
    }

    @Test
    void testExtractTemplateVariablesNormalisesWhitespace() {
        // The extraction helper is also used by PromptTemplateCompatibilityChecker, so pin the
        // exact names it returns: whitespace stripped, first-seen order, no duplicates.
        Assertions.assertEquals(List.of("language", "code"),
                PromptTemplateContentValidator.extractTemplateVariables(
                        "Review this {{language}} code: {{ code }} (in {{  language  }})"));
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
    void testMinimumGreaterThanMaximumIsRejected() {
        String minGreaterThanMax = """
                {
                    "templateId": "test",
                    "template": "Priority: {{priority}}",
                    "variables": {
                        "priority": { "type": "integer", "minimum": 10, "maximum": 5 }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(minGreaterThanMax), Collections.emptyMap());
        });
        Assertions.assertTrue(error.getCauses().stream()
                .anyMatch(v -> v.getDescription().contains("priority")
                        && v.getDescription().contains("minimum")
                        && v.getDescription().contains("maximum")));
    }

    @Test
    void testMinimumEqualToMaximumIsAccepted() {
        String minEqualsMax = """
                {
                    "templateId": "test",
                    "template": "Priority: {{priority}}",
                    "variables": {
                        "priority": { "type": "integer", "minimum": 5, "maximum": 5 }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, create(minEqualsMax), Collections.emptyMap());
    }

    @Test
    void testMinimumWithoutMaximumIsAccepted() {
        String minOnly = """
                {
                    "templateId": "test",
                    "template": "Priority: {{priority}}",
                    "variables": {
                        "priority": { "type": "integer", "minimum": 5 }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, create(minOnly), Collections.emptyMap());
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

    @Test
    void testElseKeywordIsNotAnUndefinedVariable() throws Exception {
        // This is the shape of examples/a2a-real-world-integration translator-agent-prompt.yaml,
        // which uses {{else}} inside an {{#if}} block and declares only responseText and
        // targetLanguage. Because {{else}} is a bare word it matches the variable pattern, so
        // before the control-keyword set the validator reported it as used-but-not-defined and this
        // shipped example could not pass FULL validity.
        String yaml = """
                templateId: translation
                template: |
                  {{responseText}}
                  {{#if targetLanguage}}
                  Target language: {{targetLanguage}}
                  {{else}}
                  Target language: Spanish (es)
                  {{/if}}
                variables:
                  responseText:
                    type: string
                  targetLanguage:
                    type: string
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, createYaml(yaml), Collections.emptyMap());
    }

    @Test
    void testThisKeywordIsNotAnUndefinedVariable() throws Exception {
        String yaml = """
                templateId: iteration
                template: "{{#each items}}{{this}}{{/each}}"
                variables:
                  items:
                    type: string
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, createYaml(yaml), Collections.emptyMap());
    }

    /**
     * A triple-brace run is not supported placeholder syntax - it renders through as literal text -
     * so it is not a variable and must not be reported as an undefined one. This is the same
     * treatment every other unsupported construct already gets: {{#each x}}, a dotted {{user.name}}
     * and a handlebars comment are all invisible to the extractor and none of them are flagged.
     *
     * Before the placeholder pattern was tightened, the matcher locked onto the inner {{foo}} of
     * {{{foo}}} and this template was rejected with "Template variable '{{foo}}' is used but not
     * defined" - naming a placeholder the author had not written.
     */
    @Test
    void testTripleBracePlaceholderIsNotReportedAsUndefined() {
        String tripleBrace = """
                {
                    "templateId": "test",
                    "template": "Hello {{name}}, raw is {{{foo}}}.",
                    "variables": {
                        "name": { "type": "string" }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        validator.validate(ValidityLevel.FULL, create(tripleBrace), Collections.emptyMap());
    }

    /**
     * The flip side of the above: leaving triple-brace alone must not blind the rule to a real
     * undefined placeholder sitting next to it.
     */
    @Test
    void testUndefinedVariableAdjacentToTripleBraceIsStillReported() {
        String adjacent = """
                {
                    "templateId": "test",
                    "template": "{{{foo}}} and {{quality}}",
                    "variables": {
                        "name": { "type": "string" }
                    }
                }
                """;
        PromptTemplateContentValidator validator = new PromptTemplateContentValidator();
        RuleViolationException error = Assertions.assertThrows(RuleViolationException.class, () -> {
            validator.validate(ValidityLevel.FULL, create(adjacent), Collections.emptyMap());
        });
        Assertions.assertTrue(
                error.getCauses().stream().anyMatch(v -> v.getDescription().contains("quality")));
        Assertions.assertTrue(
                error.getCauses().stream().noneMatch(v -> v.getDescription().contains("foo")),
                "The triple-brace run must not be reported as an undefined variable");
    }
}
