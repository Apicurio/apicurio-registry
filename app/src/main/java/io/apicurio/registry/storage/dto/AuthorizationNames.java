package io.apicurio.registry.storage.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-resource authorization names for registry resources, and their translation into storage
 * match clauses. Shared by point access and by every storage implementation of
 * {@link AuthorizationFilter}, so both always agree.
 *
 * <p>Group resources are named by their group ID. Artifact resources are named
 * {@code escape(groupId) + "/" + artifactId}, where {@code escape} replaces {@code %} with
 * {@code %25} and {@code /} with {@code %2F}. Escaping the group makes names unambiguous even
 * though both group and artifact IDs may contain {@code /}: the first {@code /} always separates
 * them. The default group is named {@code default} in both cases.</p>
 */
public final class AuthorizationNames {

    public static final String DEFAULT_GROUP = "default";

    private AuthorizationNames() {
    }

    /** @return the name of a group; null and the default group are named {@code default} */
    public static String group(String groupId) {
        return groupId == null || DEFAULT_GROUP.equalsIgnoreCase(groupId) || isStoredDefault(groupId)
                ? DEFAULT_GROUP : groupId;
    }

    /** @return the name of an artifact, {@code escape(group) + "/" + artifactId} */
    public static String artifact(String groupId, String artifactId) {
        return escape(group(groupId)) + "/" + artifactId;
    }

    private static boolean isStoredDefault(String groupId) {
        return "__$GROUPID$__".equals(groupId);
    }

    static String escape(String groupId) {
        return groupId.replace("%", "%25").replace("/", "%2F");
    }

    /**
     * A storage match clause: the group equals (or starts with) {@code group}, and, if
     * {@code artifact} is not null, the artifact ID equals (or starts with) {@code artifact}. A
     * {@code group} of {@code default} with {@code groupPrefix == false} is the default group.
     */
    public record Clause(String group, boolean groupPrefix, String artifact, boolean artifactPrefix) {
    }

    /** @return clauses matching exactly the artifacts whose name equals {@code name} */
    public static List<Clause> artifactsNamed(String name) {
        int slash = name.indexOf('/');
        if (slash < 0) {
            return List.of();
        }
        String group = unescape(name.substring(0, slash));
        if (group == null) {
            return List.of();
        }
        return List.of(new Clause(group, false, name.substring(slash + 1), false));
    }

    /** @return clauses matching exactly the artifacts whose name starts with {@code prefix} */
    public static List<Clause> artifactsWithPrefix(String prefix) {
        int slash = prefix.indexOf('/');
        if (slash >= 0) {
            String group = unescape(prefix.substring(0, slash));
            if (group == null) {
                return List.of();
            }
            return List.of(new Clause(group, false, prefix.substring(slash + 1), true));
        }
        // The prefix ends inside the escaped group: match raw group IDs by prefix
        List<Clause> clauses = new ArrayList<>();
        for (String rawPrefix : unescapePrefix(prefix)) {
            clauses.add(new Clause(rawPrefix, true, null, false));
        }
        if (DEFAULT_GROUP.startsWith(prefix)) {
            clauses.add(new Clause(DEFAULT_GROUP, false, null, false));
        }
        return clauses;
    }

    /** @return the raw group ID for an escaped one, or null if it is not a valid escaping */
    static String unescape(String escaped) {
        StringBuilder raw = new StringBuilder(escaped.length());
        for (int i = 0; i < escaped.length(); i++) {
            char c = escaped.charAt(i);
            if (c == '/') {
                return null;
            }
            if (c != '%') {
                raw.append(c);
                continue;
            }
            String code = escaped.length() >= i + 3 ? escaped.substring(i + 1, i + 3) : "";
            if ("25".equals(code)) {
                raw.append('%');
            } else if ("2F".equals(code)) {
                raw.append('/');
            } else {
                return null;
            }
            i += 2;
        }
        return raw.toString();
    }

    /**
     * @return raw group ID prefixes such that a group's escaped ID starts with
     *         {@code escapedPrefix} iff the raw ID starts with one of them (and is not the
     *         default group, which callers handle separately)
     */
    static List<String> unescapePrefix(String escapedPrefix) {
        int lastPercent = escapedPrefix.lastIndexOf('%');
        int tail = lastPercent < 0 ? 0 : escapedPrefix.length() - lastPercent;
        if (lastPercent >= 0 && tail < 3) {
            // Ends inside an escape: "%" or "%2" can only continue as %25 or %2F
            String partial = escapedPrefix.substring(lastPercent + 1);
            String head = unescape(escapedPrefix.substring(0, lastPercent));
            if (head == null || !(partial.isEmpty() || "2".equals(partial))) {
                return List.of();
            }
            return List.of(head + "%", head + "/");
        }
        String raw = unescape(escapedPrefix);
        return raw == null ? List.of() : List.of(raw);
    }
}
