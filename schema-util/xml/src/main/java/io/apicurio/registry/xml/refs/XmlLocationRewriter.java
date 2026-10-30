package io.apicurio.registry.xml.refs;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Rewrites location attributes of import/include-like elements in raw XML text, preserving all other
 * formatting. A small single-pass scanner is used: it is quote-aware, skips comments, CDATA sections,
 * processing instructions and declarations, and resolves element names against in-scope namespace
 * bindings so that only elements matching an {@link XmlReferenceKind} are rewritten. Attribute values are
 * decoded the same way {@link XmlReferenceLocator} sees them (entity decoding plus {@link String#trim()}).
 * Malformed input never causes an exception; unparseable remainders are copied unchanged.
 */
public final class XmlLocationRewriter {

    private XmlLocationRewriter() {
    }

    /**
     * Rewrites mapped locations.
     *
     * @param xml the raw XML content
     * @param locationToUrl map of original (decoded, trimmed) location to replacement location
     * @return the rewritten content (the same instance if the map is empty)
     */
    public static String rewrite(String xml, Map<String, String> locationToUrl) {
        if (locationToUrl == null || locationToUrl.isEmpty() || xml == null) {
            return xml;
        }
        return new Scanner(xml, locationToUrl).run();
    }

    /** A parsed attribute: name and the bounds of its raw value (excluding quotes). */
    private record Attr(String name, int valueStart, int valueEnd, char quote) {
    }

    private static final class Scanner {
        private final String xml;
        private final Map<String, String> map;
        private final StringBuilder out;
        private final Deque<Map<String, String>> scopes = new ArrayDeque<>();
        private int pos;

        Scanner(String xml, Map<String, String> map) {
            this.xml = xml;
            this.map = map;
            this.out = new StringBuilder(xml.length());
            scopes.push(Map.of("xml", "http://www.w3.org/XML/1998/namespace"));
        }

        String run() {
            int n = xml.length();
            while (pos < n) {
                int lt = xml.indexOf('<', pos);
                if (lt < 0) {
                    break;
                }
                out.append(xml, pos, lt);
                pos = lt;
                int end;
                if (xml.startsWith("<!--", lt)) {
                    end = endAfter("-->", lt + 4);
                } else if (xml.startsWith("<![CDATA[", lt)) {
                    end = endAfter("]]>", lt + 9);
                } else if (xml.startsWith("<?", lt)) {
                    end = endAfter("?>", lt + 2);
                } else if (xml.startsWith("<!", lt)) {
                    end = declarationEnd(lt + 2);
                } else if (xml.startsWith("</", lt)) {
                    end = endAfter(">", lt + 2);
                    if (end > 0 && scopes.size() > 1) {
                        scopes.pop();
                    }
                } else {
                    end = startTag(lt);
                    if (end > 0) {
                        pos = end;
                        continue;
                    }
                }
                if (end < 0) {
                    break;
                }
                out.append(xml, lt, end);
                pos = end;
            }
            out.append(xml, pos, n);
            return out.toString();
        }

        private int endAfter(String terminator, int from) {
            int i = xml.indexOf(terminator, from);
            return i < 0 ? -1 : i + terminator.length();
        }

        /** Finds the end of a {@code <!...>} declaration, honoring quotes and an internal subset. */
        private int declarationEnd(int from) {
            int depth = 0;
            int i = from;
            while (i < xml.length()) {
                char c = xml.charAt(i);
                if (c == '"' || c == '\'') {
                    int close = xml.indexOf(c, i + 1);
                    if (close < 0) {
                        return -1;
                    }
                    i = close + 1;
                    continue;
                }
                if (depth > 0 && xml.startsWith("<!--", i)) {
                    int close = endAfter("-->", i + 4);
                    if (close < 0) {
                        return -1;
                    }
                    i = close;
                    continue;
                }
                if (c == '[') {
                    depth++;
                } else if (c == ']') {
                    depth = Math.max(0, depth - 1);
                } else if (c == '>' && depth == 0) {
                    return i + 1;
                }
                i++;
            }
            return -1;
        }

        /**
         * Parses a start tag at {@code lt}, writes it (rewritten if applicable) and returns the index after
         * it, or -1 if it is malformed (nothing is written in that case).
         */
        private int startTag(int lt) {
            int i = lt + 1;
            int nameStart = i;
            while (i < xml.length() && !isSpace(xml.charAt(i)) && xml.charAt(i) != '>' && xml.charAt(i) != '/') {
                i++;
            }
            String qname = xml.substring(nameStart, i);
            if (qname.isEmpty()) {
                return -1;
            }
            Map<String, Attr> attrs = new HashMap<>();
            boolean selfClosing = false;
            while (true) {
                i = skipSpace(i);
                if (i >= xml.length()) {
                    return -1;
                }
                if (xml.charAt(i) == '>') {
                    i++;
                    break;
                }
                if (xml.startsWith("/>", i)) {
                    selfClosing = true;
                    i += 2;
                    break;
                }
                i = parseAttribute(i, attrs);
                if (i < 0) {
                    return -1;
                }
            }
            writeTag(lt, i, qname, attrs, selfClosing);
            return i;
        }

        /** Parses one {@code name="value"} attribute at {@code i}; returns the index after it, or -1. */
        private int parseAttribute(int start, Map<String, Attr> attrs) {
            int i = start;
            while (i < xml.length() && !isSpace(xml.charAt(i)) && "=>/".indexOf(xml.charAt(i)) < 0) {
                i++;
            }
            String name = xml.substring(start, i);
            i = skipSpace(i);
            if (name.isEmpty() || i >= xml.length() || xml.charAt(i) != '=') {
                return -1;
            }
            i = skipSpace(i + 1);
            if (i >= xml.length() || xml.charAt(i) != '"' && xml.charAt(i) != '\'') {
                return -1;
            }
            char quote = xml.charAt(i);
            int close = xml.indexOf(quote, i + 1);
            if (close < 0) {
                return -1;
            }
            attrs.putIfAbsent(name, new Attr(name, i + 1, close, quote));
            return close + 1;
        }

        private int skipSpace(int from) {
            int i = from;
            while (i < xml.length() && isSpace(xml.charAt(i))) {
                i++;
            }
            return i;
        }

        /** Updates namespace scopes and writes the tag {@code [lt, end)}, rewriting its location if mapped. */
        private void writeTag(int lt, int end, String qname, Map<String, Attr> attrs, boolean selfClosing) {
            Map<String, String> scope = new HashMap<>(scopes.peek());
            attrs.values().forEach(a -> {
                if (a.name().equals("xmlns")) {
                    scope.put("", decode(raw(a)));
                } else if (a.name().startsWith("xmlns:")) {
                    scope.put(a.name().substring(6), decode(raw(a)));
                }
            });
            if (!selfClosing) {
                scopes.push(scope);
            }

            int colon = qname.indexOf(':');
            String prefix = colon < 0 ? "" : qname.substring(0, colon);
            String local = qname.substring(colon + 1);
            String ns = scope.get(prefix);
            Optional<Attr> target = XmlReferenceKind.of(ns, local)
                    .map(k -> attrs.get(k.locationAttribute()));
            String replacement = target.map(a -> map.get(decode(raw(a)).trim())).orElse(null);
            if (replacement == null) {
                out.append(xml, lt, end);
            } else {
                Attr a = target.get();
                out.append(xml, lt, a.valueStart())
                        .append(escape(replacement, a.quote()))
                        .append(xml, a.valueEnd(), end);
            }
        }

        private String raw(Attr a) {
            return xml.substring(a.valueStart(), a.valueEnd());
        }
    }

    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\r';
    }

    /** Decodes predefined entities and character references once; unknown entities are kept as-is. */
    private static String decode(String value) {
        if (value.indexOf('&') < 0) {
            return value;
        }
        StringBuilder sb = new StringBuilder(value.length());
        int i = 0;
        while (i < value.length()) {
            char c = value.charAt(i);
            int semi = c == '&' ? value.indexOf(';', i + 1) : -1;
            String decoded = semi < 0 ? null : decodeEntity(value.substring(i + 1, semi));
            if (decoded == null) {
                sb.append(c);
                i++;
            } else {
                sb.append(decoded);
                i = semi + 1;
            }
        }
        return sb.toString();
    }

    private static String decodeEntity(String name) {
        switch (name) {
            case "lt":
                return "<";
            case "gt":
                return ">";
            case "amp":
                return "&";
            case "quot":
                return "\"";
            case "apos":
                return "'";
            default:
                break;
        }
        try {
            int cp;
            if (name.startsWith("#x")) {
                cp = Integer.parseInt(name.substring(2), 16);
            } else if (name.startsWith("#")) {
                cp = Integer.parseInt(name.substring(1));
            } else {
                return null;
            }
            return Character.isValidCodePoint(cp) ? new String(Character.toChars(cp)) : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String escape(String value, char quote) {
        String v = value.replace("&", "&amp;").replace("<", "&lt;");
        return quote == '"' ? v.replace("\"", "&quot;") : v.replace("'", "&apos;");
    }
}
