package io.apicurio.registry.cli.utils;

import java.util.Locale;
import java.util.Map;

public final class ContentTypeDetector {

    private static final String APPLICATION_JSON = "application/json";
    private static final String APPLICATION_XML = "application/xml";
    private static final String APPLICATION_YAML = "application/x-yaml";

    private static final Map<String, String> EXTENSION_MAP = Map.of(
            ".json", APPLICATION_JSON,
            ".avsc", APPLICATION_JSON,
            ".proto", "application/x-protobuf",
            ".yaml", APPLICATION_YAML,
            ".yml", APPLICATION_YAML,
            ".xml", APPLICATION_XML,
            ".xsd", APPLICATION_XML,
            ".wsdl", APPLICATION_XML,
            ".graphql", "application/graphql"
    );

    private ContentTypeDetector() {
    }

    public static String detect(final String file) {
        if (file == null || "-".equals(file)) {
            return APPLICATION_JSON;
        }
        final var lower = file.toLowerCase(Locale.ROOT);
        for (final var entry : EXTENSION_MAP.entrySet()) {
            if (lower.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return APPLICATION_JSON;
    }
}
