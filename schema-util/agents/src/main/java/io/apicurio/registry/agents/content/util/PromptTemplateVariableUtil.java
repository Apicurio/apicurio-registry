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

package io.apicurio.registry.agents.content.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical handling of the <code>{{variable}}</code> placeholder syntax and Handlebars-style
 * conditional blocks used by Prompt Template artifacts.
 *
 * The validity rule, the REST render endpoint and the MCP prompt converter all have to answer the
 * same question - "which variables does this template use?" - and each used to answer it with its
 * own regular expression. Because the expressions disagreed, a placeholder such as
 * <code>{{ name }}</code> was a variable to one component and literal text to another. Everything
 * that parses the syntax should go through this class so that the answer stays the same everywhere.
 *
 * The grammar is a word-character name with optional whitespace inside the braces, so both
 * <code>{{name}}</code> and <code>{{ name }}</code> resolve to <code>name</code>. Handlebars-style
 * control blocks such as <code>{{#if x}}</code> and <code>{{/if}}</code> are deliberately not
 * matched, because <code>#</code> and <code>/</code> are not word characters.
 *
 * Note that <code>\w</code> is ASCII-only in Java, so a name such as <code>{{caf&eacute;}}</code> is
 * not treated as a variable. That matches what the validity rule and the MCP converter already did
 * before this class existed. Supporting internationalized names would mean widening the pattern
 * with <code>UNICODE_CHARACTER_CLASS</code>, which is a deliberate behaviour change rather than
 * something to do by accident.
 */
public final class PromptTemplateVariableUtil {

    /**
     * The single pattern that defines what a prompt template variable placeholder looks like.
     * Group 1 is the variable name, with any surrounding whitespace already stripped.
     */
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    /**
     * Matches {{#if var}} ... {{/if}} blocks, including multi-line content.
     * Group 1 = variable name, Group 2 = inner content.
     */
    public static final Pattern IF_BLOCK_PATTERN =
            Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}((?:(?!\\{\\{/if\\}\\})[\\s\\S])*?)\\{\\{/if\\}\\}");

    /**
     * Matches if-else blocks: opening tag, truthy branch, else tag, falsy branch, closing tag.
     * Group 1 = variable name, Group 2 = truthy branch, Group 3 = falsy branch.
     * Stops both branches at {{/if}} and at a new {{#if to prevent swallowing adjacent blocks.
     */
    public static final Pattern IF_ELSE_BLOCK_PATTERN =
            Pattern.compile("\\{\\{#if\\s+(\\w+)\\}\\}((?:(?!\\{\\{else\\}\\}|\\{\\{/if\\}\\}|\\{\\{#if\\s)[\\s\\S])*?)"
                    + "\\{\\{else\\}\\}((?:(?!\\{\\{/if\\}\\}|\\{\\{#if\\s)[\\s\\S])*?)\\{\\{/if\\}\\}");

    /**
     * Matches unless blocks: opening tag, inner content, closing tag.
     * Group 1 = variable name, Group 2 = inner content.
     */
    public static final Pattern UNLESS_BLOCK_PATTERN =
            Pattern.compile("\\{\\{#unless\\s+(\\w+)\\}\\}((?:(?!\\{\\{/unless\\}\\})[\\s\\S])*?)\\{\\{/unless\\}\\}");

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * Handlebars tags that are spelled like a plain variable placeholder but are control syntax
     * rather than a name the template author is expected to declare.
     *
     * <code>{{#if x}}</code> and <code>{{/if}}</code> are already excluded by the pattern itself,
     * because <code>#</code> and <code>/</code> are not word characters. The inverse section
     * <code>{{else}}</code> and the current-context reference <code>{{this}}</code> are bare words,
     * so without this set they would be reported as variables that are used but never defined.
     */
    private static final Set<String> CONTROL_KEYWORDS = Set.of("else", "this");

    private PromptTemplateVariableUtil() {
    }

    /**
     * Returns true if the given placeholder name is Handlebars control syntax rather than a
     * template variable.
     */
    public static boolean isControlKeyword(String name) {
        return name != null && CONTROL_KEYWORDS.contains(name);
    }

    /**
     * Returns the names of the variables used by the given template, in the order they first
     * appear and without duplicates. Handlebars control keywords are not variables and are left
     * out. A null template yields an empty list.
     */
    public static List<String> extractVariableNames(String template) {
        if (template == null) {
            return Collections.emptyList();
        }

        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!isControlKeyword(name) && !variables.contains(name)) {
                variables.add(name);
            }
        }
        return variables;
    }

    /**
     * Replaces every variable placeholder in the template with the value returned by the given
     * resolver. A resolver that returns null leaves the placeholder exactly as it was written,
     * which lets each caller decide for itself whether an unresolved variable is an error or is
     * simply passed through to the output. Handlebars control keywords are never substituted,
     * for the same reason they are never extracted.
     */
    public static String substituteVariables(String template, Function<String, String> resolver) {
        if (template == null) {
            return null;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String name = matcher.group(1);
            String replacement = isControlKeyword(name) ? null : resolver.apply(name);
            if (replacement == null) {
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Determine whether a value is truthy for conditional evaluation.
     * Falsy when null, Boolean.FALSE, or empty string.
     */
    public static boolean isTruthy(Object value) {
        return value != null
                && !Boolean.FALSE.equals(value)
                && !(value instanceof String s && s.isEmpty());
    }

    /**
     * Format a value for substitution into the template. Complex objects and lists
     * are serialized to JSON.
     */
    public static String formatValue(Object value) {
        if (value instanceof List || value instanceof Map) {
            try {
                return OBJECT_MAPPER.writeValueAsString(value);
            } catch (Exception e) {
                return String.valueOf(value);
            }
        }
        return String.valueOf(value);
    }

    /**
     * Process Handlebars-style conditional blocks (if-else, if, unless).
     */
    public static String processConditionalBlocks(String template, Map<String, Object> variables) {
        if (template == null) {
            return null;
        }
        if (variables == null) {
            variables = Collections.emptyMap();
        }

        // Step 1: resolve if-else blocks first to prevent the plain-if pattern from partially matching them
        Matcher ifElseMatcher = IF_ELSE_BLOCK_PATTERN.matcher(template);
        StringBuilder ifElseResult = new StringBuilder();
        while (ifElseMatcher.find()) {
            String varName = ifElseMatcher.group(1);
            String truthyBranch = ifElseMatcher.group(2);
            String falsyBranch = ifElseMatcher.group(3);
            String chosen = isTruthy(variables.get(varName)) ? truthyBranch : falsyBranch;
            ifElseMatcher.appendReplacement(ifElseResult, Matcher.quoteReplacement(chosen));
        }
        ifElseMatcher.appendTail(ifElseResult);
        template = ifElseResult.toString();

        // Step 2: resolve plain if blocks
        Matcher ifMatcher = IF_BLOCK_PATTERN.matcher(template);
        StringBuilder ifResult = new StringBuilder();
        while (ifMatcher.find()) {
            String varName = ifMatcher.group(1);
            String content = ifMatcher.group(2);
            String chosen = isTruthy(variables.get(varName)) ? content : "";
            ifMatcher.appendReplacement(ifResult, Matcher.quoteReplacement(chosen));
        }
        ifMatcher.appendTail(ifResult);
        template = ifResult.toString();

        // Step 3: resolve unless blocks (logical complement of if)
        Matcher unlessMatcher = UNLESS_BLOCK_PATTERN.matcher(template);
        StringBuilder unlessResult = new StringBuilder();
        while (unlessMatcher.find()) {
            String varName = unlessMatcher.group(1);
            String content = unlessMatcher.group(2);
            String chosen = isTruthy(variables.get(varName)) ? "" : content;
            unlessMatcher.appendReplacement(unlessResult, Matcher.quoteReplacement(chosen));
        }
        unlessMatcher.appendTail(unlessResult);

        return unlessResult.toString();
    }
}
