package io.apicurio.registry.content.util;

import java.util.List;

/**
 * The template formats the registry is able to render.
 * <p>
 * Rendering is implemented by {@link PromptTemplateVariableUtil} and the prompt rendering service:
 * {@code {{variable}}} placeholders are substituted, and the Handlebars-style {@code {{#if}}},
 * {@code {{#unless}}} and {@code {{else}}} blocks are evaluated. A template declaring any other
 * format would be accepted at write time and then rendered with the wrong engine, so the prompt
 * template validity rule rejects it.
 * <p>
 * This list is the single source of truth. It is deliberately kept next to the rendering helpers
 * rather than inside the validity rule, because it describes what the renderer can do rather than
 * what the rule enforces. The {@code templateFormat} enum in {@code prompt-template-v1.json} must
 * list the same values, and {@code PromptTemplateSchemaSyncTest} fails if the two drift apart.
 */
public final class PromptTemplateFormats {

    /**
     * Formats the renderer can honor, in the order they are advertised.
     * <p>
     * Values are matched exactly. The published JSON Schema declares these as a case-sensitive
     * {@code enum}, so normalizing case here would make the registry accept values that the
     * schema rejects.
     */
    private static final List<String> SUPPORTED = List.of("mustache");

    private PromptTemplateFormats() {
    }

    /**
     * @return the template formats the registry can render, in the order they are advertised
     */
    public static List<String> supported() {
        return SUPPORTED;
    }

    /**
     * @param format the declared {@code templateFormat} value, matched exactly
     * @return true when the renderer can honor the given format
     */
    public static boolean isSupported(String format) {
        return SUPPORTED.contains(format);
    }
}
