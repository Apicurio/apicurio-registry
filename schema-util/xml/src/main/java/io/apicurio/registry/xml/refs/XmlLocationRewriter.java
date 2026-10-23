package io.apicurio.registry.xml.refs;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Rewrites location attributes of import/include-like elements in raw XML text, preserving all other
 * formatting.
 */
public final class XmlLocationRewriter {

    private static final Pattern TOKENS = Pattern.compile(
            "(<!--.*?-->)|(<!\\[CDATA\\[.*?]]>)|(<\\?.*?\\?>)"
                    + "|(<(?:[A-Za-z_][\\w.\\-]*:)?(?:import|include|redefine|override)\\b[^>]*>)",
            Pattern.DOTALL);

    private static final Pattern LOCATION_ATTR = Pattern.compile(
            "(\\s(?:schemaLocation|location)\\s*=\\s*)([\"'])(.*?)\\2", Pattern.DOTALL);

    private XmlLocationRewriter() {
    }

    /**
     * Rewrites mapped locations.
     *
     * @param xml the raw XML content
     * @param locationToUrl map of original (unescaped) location to replacement location
     * @return the rewritten content (the same instance if the map is empty)
     */
    public static String rewrite(String xml, Map<String, String> locationToUrl) {
        if (locationToUrl == null || locationToUrl.isEmpty()) {
            return xml;
        }
        Matcher m = TOKENS.matcher(xml);
        StringBuilder out = new StringBuilder(xml.length());
        while (m.find()) {
            String replacement = m.group(4) != null ? rewriteTag(m.group(4), locationToUrl) : m.group();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String rewriteTag(String tag, Map<String, String> locationToUrl) {
        Matcher m = LOCATION_ATTR.matcher(tag);
        StringBuilder out = new StringBuilder(tag.length());
        while (m.find()) {
            String quote = m.group(2);
            String newValue = locationToUrl.get(unescape(m.group(3)));
            String replacement = newValue == null ? m.group()
                    : m.group(1) + quote + escape(newValue, quote.charAt(0)) + quote;
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return out.toString();
    }

    private static String unescape(String value) {
        return value.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    private static String escape(String value, char quote) {
        String v = value.replace("&", "&amp;").replace("<", "&lt;");
        return quote == '"' ? v.replace("\"", "&quot;") : v.replace("'", "&apos;");
    }
}
