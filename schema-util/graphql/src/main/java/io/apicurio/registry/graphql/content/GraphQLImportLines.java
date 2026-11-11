package io.apicurio.registry.graphql.content;

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

    private static final String IMPORT_KEYWORD = "import";
    private static final String FROM_KEYWORD = "from";

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
     * A full <code>#import "file"</code> or <code># import Foo, Bar from "file"</code> line.
     *
     * @param prefix the text up to (and including) the opening quote
     * @param file the quoted file name
     * @param suffix the closing quote and any trailing whitespace
     * @param named {@code true} for the named-import form (<code># import Foo from "file"</code>),
     *        {@code false} for a plain whole-file import (<code>#import "file"</code>)
     */
    public record ImportLine(String prefix, String file, String suffix, boolean named) {
    }

    /**
     * Parses the given line as an <code>#import</code> directive. Equivalent to matching the whole line
     * (case-insensitively) against <code>\s*#\s*import\s+(?:.*?\s+from\s+)?"([^"]+)"\s*</code>, but
     * implemented as a linear scan so that no input can trigger regex backtracking, and without any limit
     * on the amount of separator whitespace.
     *
     * @return the parsed import, or {@code null} if the line is not an import directive
     */
    public static ImportLine parse(String line) {
        // The file name is the text between the last two quotes; only whitespace may follow.
        int closeQuote = line.lastIndexOf('"');
        if (closeQuote < 0 || !isBlank(line, closeQuote + 1, line.length())) {
            return null;
        }
        int openQuote = line.lastIndexOf('"', closeQuote - 1);
        if (openQuote < 0 || closeQuote - openQuote < 2) {
            return null;
        }
        int afterKeyword = skipImportKeyword(line);
        if (afterKeyword < 0 || afterKeyword >= openQuote || !isWhitespace(line.charAt(afterKeyword))) {
            return null;
        }
        // Between the keyword and the opening quote: whitespace only (plain import), or
        // whitespace + anything + whitespace + "from" + whitespace (named import).
        boolean named;
        if (isBlank(line, afterKeyword, openQuote)) {
            named = false;
        } else if (isNamedImportList(line, afterKeyword, openQuote)) {
            named = true;
        } else {
            return null;
        }
        return new ImportLine(line.substring(0, openQuote + 1), line.substring(openQuote + 1, closeQuote),
                line.substring(closeQuote), named);
    }

    /**
     * Returns the index right after the <code>import</code> keyword of a line of the form
     * <code>\s*#\s*import</code>, or {@code -1} if the line does not start that way.
     */
    private static int skipImportKeyword(String line) {
        int i = skipWhitespace(line, 0);
        if (i >= line.length() || line.charAt(i) != '#') {
            return -1;
        }
        i = skipWhitespace(line, i + 1);
        if (!line.regionMatches(true, i, IMPORT_KEYWORD, 0, IMPORT_KEYWORD.length())) {
            return -1;
        }
        return i + IMPORT_KEYWORD.length();
    }

    /**
     * Checks that {@code line[start, end)} is <code>\s+.*?\s+from\s+</code>, where {@code start} is
     * known to be a whitespace character.
     */
    private static boolean isNamedImportList(String line, int start, int end) {
        int fromEnd = trimTrailingWhitespace(line, start, end);
        int fromStart = fromEnd - FROM_KEYWORD.length();
        // Need trailing whitespace after "from", and at least two characters before it: the
        // whitespace following "import" and the whitespace preceding "from".
        return fromEnd < end && fromStart - start >= 2
                && line.regionMatches(true, fromStart, FROM_KEYWORD, 0, FROM_KEYWORD.length())
                && isWhitespace(line.charAt(fromStart - 1))
                && !containsLineTerminator(line, skipWhitespace(line, start), trimTrailingWhitespace(line,
                        start, fromStart - 1));
    }

    /**
     * Checks {@code line[start, end)} for characters that the regex {@code .} does not match. The
     * names listed before <code>from</code> may not contain them, but the whitespace around the list may.
     */
    private static boolean containsLineTerminator(String line, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = line.charAt(i);
            if (c == '\n' || c == '\r' || c == '\u0085' || c == '\u2028' || c == '\u2029') {
                return true;
            }
        }
        return false;
    }

    private static int trimTrailingWhitespace(String line, int start, int end) {
        int i = end;
        while (i > start && isWhitespace(line.charAt(i - 1))) {
            i--;
        }
        return i;
    }

    private static int skipWhitespace(String line, int from) {
        int i = from;
        while (i < line.length() && isWhitespace(line.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isBlank(String line, int start, int end) {
        for (int i = start; i < end; i++) {
            if (!isWhitespace(line.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /** Same character set as the regex {@code \s} class. */
    private static boolean isWhitespace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }

    /**
     * Tracks whether each line of a GraphQL document falls inside a triple-quoted block-string
     * description. Call {@link #consume(String)} once per line, in order; the return value for a
     * given line indicates whether that line should be treated as (at least partially) block-string
     * content, and therefore must not be parsed with {@link #parse(String)}.
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
