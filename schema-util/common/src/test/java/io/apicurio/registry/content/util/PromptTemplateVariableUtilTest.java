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

package io.apicurio.registry.content.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

class PromptTemplateVariableUtilTest {

    @Test
    void testExtractPlainVariable() {
        Assertions.assertEquals(List.of("name"),
                PromptTemplateVariableUtil.extractVariableNames("Hello {{name}}"));
    }

    @Test
    void testExtractVariableWithSurroundingWhitespace() {
        // The whole point of the shared pattern: spaces inside the braces are still a variable.
        Assertions.assertEquals(List.of("name"),
                PromptTemplateVariableUtil.extractVariableNames("Hello {{ name }}"));
        Assertions.assertEquals(List.of("name"),
                PromptTemplateVariableUtil.extractVariableNames("Hello {{   name}}"));
        Assertions.assertEquals(List.of("name"),
                PromptTemplateVariableUtil.extractVariableNames("Hello {{name   }}"));
    }

    @Test
    void testExtractPreservesOrderAndDeduplicates() {
        Assertions.assertEquals(List.of("language", "code"),
                PromptTemplateVariableUtil.extractVariableNames(
                        "Review this {{language}} code: {{code}} (in {{ language }})"));
    }

    @Test
    void testExtractIgnoresConditionalBlockMarkers() {
        // '#' and '/' are not word characters, so neither {{#if plan}} nor {{/if}} is read as a
        // variable - not the control keyword, and not the condition subject either. Only the
        // real placeholder inside the block is returned.
        Assertions.assertEquals(List.of("name"),
                PromptTemplateVariableUtil.extractVariableNames(
                        "{{#if plan}}Hi {{ name }}{{/if}}"));
    }

    @Test
    void testExtractFromNullTemplate() {
        Assertions.assertEquals(List.of(), PromptTemplateVariableUtil.extractVariableNames(null));
    }

    @Test
    void testExtractFromTemplateWithNoVariables() {
        Assertions.assertEquals(List.of(),
                PromptTemplateVariableUtil.extractVariableNames("A static prompt."));
    }

    @Test
    void testSubstituteWithAndWithoutWhitespace() {
        Map<String, String> values = Map.of("name", "Alice");
        Assertions.assertEquals("Hello Alice and Alice",
                PromptTemplateVariableUtil.substituteVariables("Hello {{name}} and {{ name }}",
                        values::get));
    }

    @Test
    void testSubstituteLeavesUnresolvedPlaceholderUntouched() {
        // A null from the resolver means "no value" - the placeholder is written back verbatim,
        // including whatever whitespace the author used.
        Assertions.assertEquals("Hello {{ name }}",
                PromptTemplateVariableUtil.substituteVariables("Hello {{ name }}", varName -> null));
    }

    @Test
    void testSubstituteEscapesRegexReplacementCharacters() {
        // A '$' or '\' in the value must not be interpreted as a replacement group reference.
        Assertions.assertEquals("Cost: $1,000 \\ net",
                PromptTemplateVariableUtil.substituteVariables("Cost: {{amount}}",
                        varName -> "$1,000 \\ net"));
    }

    @Test
    void testSubstituteWithEmptyValue() {
        Assertions.assertEquals("Hello !",
                PromptTemplateVariableUtil.substituteVariables("Hello {{name}}!", varName -> ""));
    }

    @Test
    void testSubstituteOnNullTemplate() {
        Assertions.assertNull(PromptTemplateVariableUtil.substituteVariables(null, varName -> "x"));
    }

    @Test
    void testSubstituteDoesNotTouchConditionalBlockMarkers() {
        Assertions.assertEquals("{{#if plan}}Hi Alice{{/if}}",
                PromptTemplateVariableUtil.substituteVariables("{{#if plan}}Hi {{ name }}{{/if}}",
                        varName -> "name".equals(varName) ? "Alice" : null));
    }
}
