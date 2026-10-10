package io.apicurio.registry.iceberg.rest.v1.impl;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * Maps Iceberg namespaces to registry group IDs. Shared by the Iceberg REST resource and the
 * access controllers, so authorization always evaluates the same group the endpoint operates on.
 */
public final class IcebergNamespaces {

    /** Separator between namespace levels in the encoded URL path segment (Iceberg REST spec). */
    static final String NAMESPACE_SEPARATOR = "\u0000";

    private IcebergNamespaces() {
    }

    /**
     * @param namespace namespace levels, e.g. {@code ["sales", "eu"]}
     * @return the group ID ({@code "sales.eu"}), or null for an empty namespace
     */
    public static String toGroupId(List<String> namespace) {
        if (namespace == null || namespace.isEmpty()) {
            return null;
        }
        return String.join(".", namespace);
    }

    /**
     * @param encodedNamespace URL-encoded namespace path segment, levels separated by U+0000
     * @return the group ID, or null for an empty namespace
     */
    public static String encodedToGroupId(String encodedNamespace) {
        if (encodedNamespace == null || encodedNamespace.isEmpty()) {
            return null;
        }
        String decoded = URLDecoder.decode(encodedNamespace, StandardCharsets.UTF_8);
        return toGroupId(Arrays.asList(decoded.split(NAMESPACE_SEPARATOR)));
    }
}
