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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Canonical handling of the <code>{{variable}}</code> placeholder syntax used by Prompt Template
 * artifacts.
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
 */
public final class PromptTemplateVariableUtil {

    /**
     * The single pattern that defines what a prompt template variable placeholder looks like.
     * Group 1 is the variable name, with any surrounding whitespace already stripped.
     */
    public static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*(\\w+)\\s*\\}\\}");

    private PromptTemplateVariableUtil() {
    }

    /**
     * Returns the names of the variables used by the given template, in the order they first
     * appear and without duplicates. A null template yields an empty list.
     */
    public static List<String> extractVariableNames(String template) {
        if (template == null) {
            return Collections.emptyList();
        }

        List<String> variables = new ArrayList<>();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        while (matcher.find()) {
            String name = matcher.group(1);
            if (!variables.contains(name)) {
                variables.add(name);
            }
        }
        return variables;
    }

    /**
     * Replaces every variable placeholder in the template with the value returned by the given
     * resolver. A resolver that returns null leaves the placeholder exactly as it was written,
     * which lets each caller decide for itself whether an unresolved variable is an error or is
     * simply passed through to the output.
     */
    public static String substituteVariables(String template, Function<String, String> resolver) {
        if (template == null) {
            return null;
        }

        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String replacement = resolver.apply(matcher.group(1));
            if (replacement == null) {
                replacement = matcher.group(0);
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
