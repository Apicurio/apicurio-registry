package io.apicurio.registry.graphql.content;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared line-scanning support for the <code>#import</code> leading-comment convention used by
 * {@link io.apicurio.registry.graphql.content.refs.GraphQLReferenceFinder} and
 * {@link io.apicurio.registry.graphql.content.dereference.GraphQLContentDereferencer}.
 * <p>
 * GraphQL SDL supports triple-quoted block-string descriptions (<code>"""..."""</code>) that may span
 * multiple lines and contain arbitrary text, including text that incidentally looks like an
 * <code>#import</code> comment. Since descriptions are string content and not comments, a line found
 * inside such a block must never be treated as an import directive. This class tracks block-string
 * state across lines so callers can skip import matching while inside one.
 */
public final class GraphQLImportLines {

    /**
     * Matches a full <code>#import "file"</code> or <code># import Foo, Bar from "file"</code> line.
     * Group 1 is the prefix up to (and including) the opening quote, group 2 is the quoted file name,
     * and group 3 is the trailing quote and whitespace. The prefix (group 1) contains the literal text
     * {@code "from"} when the line uses the named-import form.
     */
    public static final Pattern IMPORT_LINE = Pattern
            .compile("^(\\s*#\\s*import\\s+(?:.*?\\s+from\\s+)?\")([^\"]+)(\"\\s*)$",
                    Pattern.CASE_INSENSITIVE);

    private static final Pattern TRIPLE_QUOTE = Pattern.compile("\"\"\"");

    private GraphQLImportLines() {
    }

    /**
     * Splits the given content into lines, preserving the ability to reconstruct it (uses a
     * capturing split that keeps empty trailing lines).
     */
    public static String[] splitLines(String content) {
        return content.split("\\r?\\n", -1);
    }

    /**
     * Returns {@code true} if the given line, as matched by {@link #IMPORT_LINE}, uses the
     * named-import form (<code># import Foo, Bar from "file"</code>) rather than a plain whole-file
     * import (<code>#import "file"</code>).
     */
    public static boolean isNamedImport(Matcher importLineMatcher) {
        return importLineMatcher.group(1).toLowerCase(Locale.ROOT).contains("from");
    }

    /**
     * Tracks whether each line of a GraphQL document falls inside a triple-quoted block-string
     * description. Call {@link #consume(String)} once per line, in order; the return value for a
     * given line indicates whether that line should be treated as (at least partially) block-string
     * content, and therefore must not be matched against {@link #IMPORT_LINE}.
     */
    public static final class BlockStringTracker {

        private boolean insideBlockString = false;

        /**
         * Processes the next line and returns {@code true} if that line was inside (or opened/closed)
         * a block string, and therefore must be skipped for import-comment matching.
         */
        public boolean consume(String line) {
            boolean wasInside = insideBlockString;
            Matcher matcher = TRIPLE_QUOTE.matcher(line);
            int count = 0;
            while (matcher.find()) {
                count++;
            }
            if (count % 2 != 0) {
                insideBlockString = !insideBlockString;
            }
            // A line is "block-string content" if it started inside a block string, or it contains a
            // triple-quote delimiter (opening and/or closing one) - either way it is not a plain
            // standalone comment line.
            return wasInside || count > 0;
        }
    }
}
