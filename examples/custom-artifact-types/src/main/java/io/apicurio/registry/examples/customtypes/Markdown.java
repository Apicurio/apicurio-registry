package io.apicurio.registry.examples.customtypes;

import io.apicurio.registry.content.TypedContent;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Tiny Markdown "model" shared by the MARKDOWN artifact type providers: a document has a title
 * (the first level-1 heading) and a set of sections (level-2 headings).
 */
final class Markdown {

    static final String CONTENT_TYPE = "text/markdown";

    private Markdown() {
    }

    static String text(TypedContent content) {
        return content.getContent().content();
    }

    /** Returns the text of the first {@code # } heading, or {@code null} when there is none. */
    static String title(String markdown) {
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            return trimmed.startsWith("# ") ? trimmed.substring(2).trim() : null;
        }
        return null;
    }

    /** Returns the texts of all {@code ## } headings, in document order. */
    static Set<String> sections(String markdown) {
        Set<String> sections = new LinkedHashSet<>();
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("## ")) {
                sections.add(trimmed.substring(3).trim());
            }
        }
        return sections;
    }

    /** Normalizes line endings and trailing whitespace so that equivalent documents hash alike. */
    static String canonicalize(String markdown) {
        StringBuilder out = new StringBuilder(markdown.length());
        for (String line : markdown.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1)) {
            out.append(line.stripTrailing()).append('\n');
        }
        return out.toString().strip() + "\n";
    }
}
