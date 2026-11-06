package io.apicurio.registry.content.refs.resolve;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves absolute references pointing at artifact versions on the same Apicurio Registry server (v3 API)
 * to {@link ExistingArtifact} instances.
 */
public final class RegistryUrlReferenceResolver implements ReferenceUrlResolver {

    private final String registryUrl;
    private final Pattern artifactUrlPattern;

    /**
     * Creates a resolver for the given registry.
     *
     * @param registryV3BaseUrl the base URL of the registry v3 API, e.g.
     *        {@code http://host:8080/apis/registry/v3}
     */
    public RegistryUrlReferenceResolver(String registryV3BaseUrl) {
        this.registryUrl = registryV3BaseUrl;
        String path = stripTrailingSlash(URI.create(registryV3BaseUrl).getRawPath());
        this.artifactUrlPattern = Pattern.compile("^" + Pattern.quote(path)
                + "/groups/([^/]+)/artifacts/([^/]+)/versions/([^/]+)(?:/content)?$");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<ExistingArtifact> resolve(String fullReference) {
        if (fullReference == null || !isSameServer(fullReference)) {
            return Optional.empty();
        }
        String rawPath;
        try {
            rawPath = URI.create(fullReference).getRawPath();
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (rawPath == null) {
            return Optional.empty();
        }
        Matcher matcher = artifactUrlPattern.matcher(rawPath);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        // URI.create above already rejected malformed percent-escapes, so decoding cannot fail here
        return Optional.of(new ExistingArtifact(registryReferenceName(fullReference),
                decodePathSegment(matcher.group(1)), decodePathSegment(matcher.group(2)),
                decodePathSegment(matcher.group(3))));
    }

    /**
     * Strips the scheme and authority from a reference, keeping path, query and fragment.
     *
     * @param fullReference the full reference
     * @return the reference name
     */
    static String registryReferenceName(String fullReference) {
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

    /**
     * Percent-decodes a single URI path segment.
     *
     * @param segment the raw segment
     * @return the decoded segment; a {@code +} is kept literally
     * @throws IllegalArgumentException if the segment contains a malformed percent-escape
     */
    static String decodePathSegment(String segment) {
        // percent-decode only: '+' is a literal character in path segments, not a space
        return URLDecoder.decode(segment.replace("+", "%2B"), StandardCharsets.UTF_8);
    }

    private boolean isSameServer(String resource) {
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
        String result = path;
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
