package io.apicurio.registry.maven;

import java.net.URI;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public final class ReferenceUrlUtil {

    private ReferenceUrlUtil() {
    }

    public static Pattern createRegistryArtifactUrlPattern(String registryUrl) {
        URI registryUri = URI.create(registryUrl);
        String registryPath = stripTrailingSlash(registryUri.getRawPath());
        return Pattern.compile("^" + Pattern.quote(registryPath)
                + "/groups/([^/]+)/artifacts/([^/]+)/versions/([^/]+)(?:/content)?$");
    }

    public static boolean isSameApicurioServer(String registryUrl, String resource) {
        try {
            URI registryUri = URI.create(registryUrl);
            URI resourceUri = URI.create(resource);

            return Objects.equals(lowercase(registryUri.getScheme()), lowercase(resourceUri.getScheme()))
                    && Objects.equals(lowercase(registryUri.getHost()), lowercase(resourceUri.getHost()))
                    && effectivePort(registryUri) == effectivePort(resourceUri);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public static String decodePathSegment(String segment) {
        try {
            return URI.create("https://xxx/" + segment).getPath().substring(1);
        } catch (IllegalArgumentException e) {
            return segment;
        }
    }

    public static String registryReferenceName(String fullReference) {
        try {
            URI uri = URI.create(fullReference);
            if (uri.getRawPath() == null) {
                return fullReference;
            }

            StringBuilder name = new StringBuilder(uri.getRawPath());
            if (uri.getRawQuery() != null) {
                name.append('?').append(uri.getRawQuery());
            }
            if (uri.getRawFragment() != null) {
                name.append('#').append(uri.getRawFragment());
            }
            return name.toString();
        } catch (IllegalArgumentException e) {
            return fullReference;
        }
    }

    public static boolean isAbsoluteUri(String resourceName) {
        if (resourceName == null) {
            return false;
        }

        try {
            return URI.create(resourceName).isAbsolute();
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        if ("http".equalsIgnoreCase(uri.getScheme())) {
            return 80;
        }
        if ("https".equalsIgnoreCase(uri.getScheme())) {
            return 443;
        }
        return -1;
    }

    private static String lowercase(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private static String stripTrailingSlash(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return "";
        }
        while (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
