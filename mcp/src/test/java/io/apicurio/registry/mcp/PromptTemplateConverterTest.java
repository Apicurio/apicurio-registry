/*
 * Copyright 2026 Red Hat
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.apicurio.registry.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.apicurio.registry.mcp.PromptTemplateConverter.PromptTemplate;
import io.apicurio.registry.mcp.PromptTemplateConverter.VariableSchema;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

/**
 * Tests for the {{variable}} substitution done by the MCP prompt converter, and for the defaults
 * it applies from a parsed template's variable schema. These do not need a Quarkus context - the
 * converter only needs its ObjectMapper, which is set directly here.
 */
class PromptTemplateConverterTest {

    private PromptTemplateConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PromptTemplateConverter();
        converter.jsonMapper = new ObjectMapper();
    }

    private static PromptTemplate template(String text, Map<String, VariableSchema> variables) {
        PromptTemplate template = new PromptTemplate();
        template.setTemplate(text);
        template.setVariables(variables);
        return template;
    }

    private static VariableSchema variable(String type, Object defaultValue) {
        VariableSchema schema = new VariableSchema();
        schema.setType(type);
        if (defaultValue != null) {
            schema.setDefaultValue(defaultValue);
        }
        return schema;
    }

    @Test
    void testRenderPlainVariable() {
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("Hello {{name}}", Map.of("name", "Alice")));
    }

    @Test
    void testRenderVariableWithWhitespace() {
        // Previously the converter substituted the literal string "{{" + key + "}}", so a
        // space-padded placeholder was left in the output as dead text.
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("Hello {{ name }}", Map.of("name", "Alice")));
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("Hello {{   name   }}", Map.of("name", "Alice")));
    }

    @Test
    void testRenderMixedSpellingsOfSameVariable() {
        Assertions.assertEquals("Alice / Alice",
                converter.renderTemplate("{{name}} / {{ name }}", Map.of("name", "Alice")));
    }

    @Test
    void testUnknownVariableKeepsPlaceholder() {
        Assertions.assertEquals("Hello {{name}}",
                converter.renderTemplate("Hello {{name}}", Map.of()));
    }

    @Test
    void testNullValueRendersAsEmptyString() {
        Map<String, Object> args = new HashMap<>();
        args.put("name", null);
        Assertions.assertEquals("Hello !", converter.renderTemplate("Hello {{name}}!", args));
    }

    @Test
    void testNullArgsReturnsTemplateUnchanged() {
        Assertions.assertEquals("Hello {{name}}", converter.renderTemplate("Hello {{name}}", null));
    }

    @Test
    void testValueWithRegexReplacementCharacters() {
        Assertions.assertEquals("Cost: $1,000",
                converter.renderTemplate("Cost: {{amount}}", Map.of("amount", "$1,000")));
    }

    @Test
    void testVariableNameWithRegexMetacharacterDoesNotMatchWildly() {
        // The old code built a regex from the argument name, so a '.' in the key matched any
        // character. "a.b" must not substitute the unrelated placeholder "{{axb}}".
        Assertions.assertEquals("{{axb}}",
                converter.renderTemplate("{{axb}}", Map.of("a.b", "boom")));
    }

    @Test
    void testConditionalBlockStillWorks() {
        Assertions.assertEquals("Hello Alice",
                converter.renderTemplate("{{#if premium}}Hello {{ name }}{{/if}}",
                        Map.of("premium", true, "name", "Alice")));
    }

    @Test
    void testConditionalBlockDroppedWhenFalsy() {
        Assertions.assertEquals("",
                converter.renderTemplate("{{#if premium}}Hello {{ name }}{{/if}}",
                        Map.of("premium", false, "name", "Alice")));
    }

    @Test
    void testParseAndRenderAutoDetectJsonWithWhitespaceVariable() {
        String json = """
                {
                    "templateId": "greeting",
                    "template": "Hello {{ name }}!",
                    "variables": { "name": { "type": "string" } }
                }
                """;
        Assertions.assertEquals("Hello Alice!",
                converter.parseAndRenderAutoDetect(json, Map.of("name", "Alice")));
    }

    @Test
    void testDeclaredDefaultIsApplied() {
        PromptTemplate template = template("Write in a {{tone}} tone.",
                Map.of("tone", variable("string", "formal")));

        String rendered = converter.renderTemplate(template, Map.of());

        Assertions.assertEquals("Write in a formal tone.", rendered);
    }

    @Test
    void testCallerValueOverridesDefault() {
        PromptTemplate template = template("Write in a {{tone}} tone.",
                Map.of("tone", variable("string", "formal")));

        String rendered = converter.renderTemplate(template, Map.of("tone", "casual"));

        Assertions.assertEquals("Write in a casual tone.", rendered);
    }

    @Test
    void testNonStringDefaultIsApplied() {
        PromptTemplate template = template("Retry {{count}} times.",
                Map.of("count", variable("integer", 3)));

        String rendered = converter.renderTemplate(template, Map.of());

        Assertions.assertEquals("Retry 3 times.", rendered);
    }

    @Test
    void testVariableWithoutDefaultPreservesPlaceholder() {
        PromptTemplate template = template("Hello, {{name}}!",
                Map.of("name", variable("string", null)));

        String rendered = converter.renderTemplate(template, Map.of());

        Assertions.assertEquals("Hello, {{name}}!", rendered);
    }

    @Test
    void testDefaultAppliedInsideConditionalBlock() {
        PromptTemplate template = template("{{#if formal}}Dear {{title}}{{/if}}",
                Map.of("formal", variable("boolean", true),
                        "title", variable("string", "Sir or Madam")));

        String rendered = converter.renderTemplate(template, Map.of());

        Assertions.assertEquals("Dear Sir or Madam", rendered);
    }

    @Test
    void testDefaultsDoNotModifyCallerSuppliedMap() {
        PromptTemplate template = template("Write in a {{tone}} tone.",
                Map.of("tone", variable("string", "formal")));
        Map<String, Object> args = new HashMap<>();

        converter.renderTemplate(template, args);

        Assertions.assertTrue(args.isEmpty(),
                "Rendering must not mutate the caller-supplied arguments map");
    }

    @Test
    void testDefaultsAppliedWhenArgsAreNull() {
        PromptTemplate template = template("Write in a {{tone}} tone.",
                Map.of("tone", variable("string", "formal")));

        String rendered = converter.renderTemplate(template, null);

        Assertions.assertEquals("Write in a formal tone.", rendered);
    }

    @Test
    void testTemplateWithoutVariablesSchemaIsUnchanged() {
        PromptTemplate template = template("Hello, {{name}}!", null);

        String rendered = converter.renderTemplate(template, Map.of());

        Assertions.assertEquals("Hello, {{name}}!", rendered);
    }

    private static final String TONE_TEMPLATE_YAML = """
            templateId: tone-example
            template: "Write in a {{tone}} tone."
            variables:
              tone:
                type: string
                default: formal
            """;

    /**
     * Drives the render_prompt_template and render_registry_prompt path end to end, so that
     * routing parseAndRenderAutoDetect through the schema-aware overload cannot be reverted
     * without a failing test.
     */
    @Test
    void testDefaultAppliedThroughParseAndRenderAutoDetect() {
        String rendered = converter.parseAndRenderAutoDetect(TONE_TEMPLATE_YAML, Map.of());

        Assertions.assertEquals("Write in a formal tone.", rendered);
    }

    /**
     * Same guarantee for the explicit content type entry point, which parses the template and
     * therefore also has the variable schema available.
     */
    @Test
    void testDefaultAppliedThroughParseAndRender() {
        String rendered = converter.parseAndRender(TONE_TEMPLATE_YAML, "application/x-yaml", Map.of());

        Assertions.assertEquals("Write in a formal tone.", rendered);
    }

    /**
     * An argument the caller supplied explicitly as null is present, so the declared default is
     * not applied to it. Note that the substitution step then renders a null value as an empty
     * string, which is existing behaviour of this converter and is not changed here. The point of
     * this test is that the result is not the default, so a later change from containsKey to a
     * null check cannot silently flip the guarantee.
     */
    @Test
    void testExplicitNullArgumentIsNotOverwrittenByDefault() {
        PromptTemplate template = template("Write in a {{tone}} tone.",
                Map.of("tone", variable("string", "formal")));
        Map<String, Object> args = new HashMap<>();
        args.put("tone", null);

        String rendered = converter.renderTemplate(template, args);

        Assertions.assertEquals("Write in a  tone.", rendered);
        Assertions.assertNotEquals("Write in a formal tone.", rendered,
                "An explicitly supplied null must not be replaced by the declared default");
    }
}
