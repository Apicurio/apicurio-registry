package io.apicurio.registry.cli.utils;

import io.apicurio.registry.types.ContentTypes;

import java.util.Locale;
import java.util.Map;

public final class ContentTypeDetector {

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            ".json", ContentTypes.APPLICATION_JSON,
            ".avsc", ContentTypes.APPLICATION_JSON,
            ".proto", ContentTypes.APPLICATION_PROTOBUF,
            ".yaml", ContentTypes.APPLICATION_YAML,
            ".yml", ContentTypes.APPLICATION_YAML,
            ".xml", ContentTypes.APPLICATION_XML,
            ".xsd", ContentTypes.APPLICATION_XML,
            ".wsdl", ContentTypes.APPLICATION_XML,
            ".graphql", ContentTypes.APPLICATION_GRAPHQL
    );

    private static final Map<String, String> ARTIFACT_TYPE_MAP = Map.ofEntries(
            Map.entry("AVRO", ContentTypes.APPLICATION_JSON),
            Map.entry("PROTOBUF", ContentTypes.APPLICATION_PROTOBUF),
            Map.entry("JSON", ContentTypes.APPLICATION_JSON),
            Map.entry("OPENAPI", ContentTypes.APPLICATION_JSON),
            Map.entry("ASYNCAPI", ContentTypes.APPLICATION_JSON),
            Map.entry("GRAPHQL", ContentTypes.APPLICATION_GRAPHQL),
            Map.entry("WSDL", ContentTypes.APPLICATION_XML),
            Map.entry("XSD", ContentTypes.APPLICATION_XML),
            Map.entry("XML", ContentTypes.APPLICATION_XML),
            Map.entry("THRIFT", ContentTypes.APPLICATION_THRIFT)
    );

    private ContentTypeDetector() {
    }

    /**
     * Resolves the default content type for a new artifact/version when
     * --content-type was not given. The --type (artifact type) is the primary
     * signal; the filename extension is only used as a fallback when the
     * artifact type is unset or unrecognized. Never returns null.
     */
    public static String detect(final String artifactType, final String file) {
        if (artifactType != null) {
            final var mapped = ARTIFACT_TYPE_MAP.get(artifactType.toUpperCase(Locale.ROOT));
            if (mapped != null) {
                return mapped;
            }
        }
        return detect(file);
    }

    /**
     * Extension-only fallback. Used directly by VersionCreateCommand, which has
     * no --type option (a version is created under an artifact whose type is
     * already fixed). Never returns null.
     */
    public static String detect(final String file) {
        if (file == null || "-".equals(file)) {
            return ContentTypes.APPLICATION_JSON;
        }
        final var lower = file.toLowerCase(Locale.ROOT);
        for (final var entry : EXTENSION_MAP.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return ContentTypes.APPLICATION_JSON;
    }
}
